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
class ProductImageApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commercial");

    private static RSAPrivateKey signingKey;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

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
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("1").claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void imageGallery_multiImage_variantScoped_singlePrimary() throws Exception {
        String manager = token("MANAGER");
        Long categoryId = createCategory(manager);
        Long productId = createProduct(manager, categoryId, "trail-helmet");
        Long variantId = createVariant(manager, productId, "TH-RED-M");

        // a general primary image
        Map<String, Object> first = new HashMap<>();
        first.put("url", "https://cdn.example.com/helmet-front.jpg");
        first.put("displayOrder", 1);
        first.put("primary", true);
        Long img1 = addImage(manager, productId, first, HttpStatus.CREATED);

        // a second image scoped to the red variant (colour-specific)
        Map<String, Object> second = new HashMap<>();
        second.put("url", "https://cdn.example.com/helmet-red.jpg");
        second.put("variantId", variantId);
        second.put("displayOrder", 2);
        second.put("primary", false);
        Long img2 = addImage(manager, productId, second, HttpStatus.CREATED);

        // public list: two images, ordered by displayOrder, exactly one primary, variant carried through
        List<Map<String, Object>> images = client().get().uri("/api/products/" + productId + "/images")
                .retrieve().toEntity(LIST).getBody();
        assertThat(images).hasSize(2);
        assertThat(images.get(0).get("url")).isEqualTo("https://cdn.example.com/helmet-front.jpg");
        assertThat(images.get(1).get("variantId")).isNotNull();
        assertThat(images).filteredOn(i -> (Boolean) i.get("primary")).hasSize(1);

        // promote img2 to primary -> single-primary invariant moves it off img1
        Map<String, Object> promote = new HashMap<>(second);
        promote.put("primary", true);
        ResponseEntity<Map<String, Object>> updated = client().put().uri("/api/images/" + img2)
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(promote).retrieve().toEntity(MAP);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> afterPromote = client().get().uri("/api/products/" + productId + "/images")
                .retrieve().toEntity(LIST).getBody();
        assertThat(afterPromote).filteredOn(i -> (Boolean) i.get("primary"))
                .singleElement()
                .extracting(i -> ((Number) i.get("id")).longValue()).isEqualTo(img2);

        // customers cannot add images
        Map<String, Object> denied = new HashMap<>();
        denied.put("url", "https://cdn.example.com/nope.jpg");
        denied.put("displayOrder", 9);
        denied.put("primary", false);
        ResponseEntity<String> forbidden = client().post().uri("/api/products/" + productId + "/images")
                .header("Authorization", "Bearer " + token("CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                .body(denied).retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // a variant from a different product is rejected (400)
        Long otherProduct = createProduct(manager, categoryId, "other-helmet");
        Map<String, Object> crossVariant = new HashMap<>();
        crossVariant.put("url", "https://cdn.example.com/cross.jpg");
        crossVariant.put("variantId", variantId);
        crossVariant.put("displayOrder", 1);
        crossVariant.put("primary", false);
        ResponseEntity<String> badRequest = client().post().uri("/api/products/" + otherProduct + "/images")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(crossVariant).retrieve().toEntity(String.class);
        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // delete the first image -> gallery shrinks
        ResponseEntity<Void> deleted = client().delete().uri("/api/images/" + img1)
                .header("Authorization", "Bearer " + manager).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<Map<String, Object>> finalList = client().get().uri("/api/products/" + productId + "/images")
                .retrieve().toEntity(LIST).getBody();
        assertThat(finalList).hasSize(1);
    }

    private Long createCategory(String managerToken) {
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Helmets", "slug", "helmets", "displayOrder", 0))
                .retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private Long createProduct(String managerToken, Long categoryId, String slug) {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", slug);
        body.put("slug", slug);
        body.put("basePrice", 100.00);
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/products")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private Long createVariant(String managerToken, Long productId, String sku) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("color", "Red");
        body.put("stockQuantity", 5);
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private Long addImage(String managerToken, Long productId, Map<String, Object> body, HttpStatus expected) {
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/products/" + productId + "/images")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(expected);
        return res.getBody() == null ? null : ((Number) res.getBody().get("id")).longValue();
    }
}
