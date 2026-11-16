package com.matessa.commercial;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WishlistApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commercial");

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        signingKey = (RSAPrivateKey) keyPair.getPrivate();
        Path dir = Files.createTempDirectory("commercial-test-keys");
        Path publicKey = dir.resolve("public.pem");
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        publicKey.toFile().deleteOnExit();
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(String role, String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject(subject).claim("tenant", "matessa")
                .claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void wishlist_perUser_addDedupeRemove_ownerIsolation() throws Exception {
        String manager = token("MANAGER", "1");
        String alice = token("CUSTOMER", "100");
        String bob = token("CUSTOMER", "200");
        Long variantId = createVariant(manager);

        // Alice's wishlist auto-creates empty
        ResponseEntity<Map<String, Object>> empty = client().get().uri("/api/wishlist")
                .header("Authorization", "Bearer " + alice).retrieve().toEntity(MAP);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) empty.getBody().get("userId")).longValue()).isEqualTo(100L);
        assertThat((List<?>) empty.getBody().get("items")).isEmpty();

        // add a variant
        ResponseEntity<Map<String, Object>> added = client().post().uri("/api/wishlist/items")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", variantId)).retrieve().toEntity(MAP);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long itemId = ((Number) added.getBody().get("id")).longValue();

        // adding the same variant again -> 409
        ResponseEntity<String> dup = client().post().uri("/api/wishlist/items")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", variantId)).retrieve().toEntity(String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // a non-existent variant -> 400
        ResponseEntity<String> badVariant = client().post().uri("/api/wishlist/items")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", 999999)).retrieve().toEntity(String.class);
        assertThat(badVariant.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Alice sees her one item
        ResponseEntity<Map<String, Object>> aliceList = client().get().uri("/api/wishlist")
                .header("Authorization", "Bearer " + alice).retrieve().toEntity(MAP);
        assertThat((List<?>) aliceList.getBody().get("items")).hasSize(1);

        // Bob has a separate, empty wishlist and cannot delete Alice's item
        ResponseEntity<Map<String, Object>> bobList = client().get().uri("/api/wishlist")
                .header("Authorization", "Bearer " + bob).retrieve().toEntity(MAP);
        assertThat((List<?>) bobList.getBody().get("items")).isEmpty();
        ResponseEntity<String> bobDeletesAlice = client().delete().uri("/api/wishlist/items/" + itemId)
                .header("Authorization", "Bearer " + bob).retrieve().toEntity(String.class);
        assertThat(bobDeletesAlice.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // anonymous cannot read a wishlist
        ResponseEntity<String> anon = client().get().uri("/api/wishlist").retrieve().toEntity(String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Alice removes her item
        ResponseEntity<Void> removed = client().delete().uri("/api/wishlist/items/" + itemId)
                .header("Authorization", "Bearer " + alice).retrieve().toBodilessEntity();
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map<String, Object>> afterRemove = client().get().uri("/api/wishlist")
                .header("Authorization", "Bearer " + alice).retrieve().toEntity(MAP);
        assertThat((List<?>) afterRemove.getBody().get("items")).isEmpty();
    }

    private Long createVariant(String managerToken) {
        ResponseEntity<Map<String, Object>> category = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Helmets", "slug", "helmets", "displayOrder", 0)).retrieve().toEntity(MAP);
        Long categoryId = ((Number) category.getBody().get("id")).longValue();
        Map<String, Object> productBody = new HashMap<>();
        productBody.put("categoryId", categoryId);
        productBody.put("name", "Trail Helmet");
        productBody.put("slug", "trail-helmet");
        productBody.put("basePrice", 100.00);
        ResponseEntity<Map<String, Object>> product = client().post().uri("/api/products")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(productBody).retrieve().toEntity(MAP);
        Long productId = ((Number) product.getBody().get("id")).longValue();
        Map<String, Object> variantBody = new HashMap<>();
        variantBody.put("sku", "TH-RED-M");
        variantBody.put("color", "Red");
        variantBody.put("stockQuantity", 5);
        ResponseEntity<Map<String, Object>> variant = client().post().uri("/api/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(variantBody).retrieve().toEntity(MAP);
        assertThat(variant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) variant.getBody().get("id")).longValue();
    }
}
