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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stock for products sold with and without variations. A product without variations holds its
 * stock on a hidden default variant (one stock path for cart and checkout); adding a real
 * variation retires it, removing the last one brings it back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DefaultVariantIntegrationTest {

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
        Path publicKey = Files.createTempDirectory("k").resolve("public.pem");
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n");
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return RestClient.builder().defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String manager() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject("1").claim("tenant", "matessa")
                .claim("roles", List.of("MANAGER")).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private long product(Integer stock) throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map<String, Object>> category = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + manager()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "C" + s, "slug", "c-" + s, "displayOrder", 0)).retrieve().toEntity(MAP);
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", ((Number) category.getBody().get("id")).longValue());
        body.put("name", "Gourd " + s);
        body.put("slug", "gourd-" + s);
        body.put("basePrice", 499.00);
        if (stock != null) {
            body.put("stockQuantity", stock);
        }
        ResponseEntity<Map<String, Object>> created = client().post().uri("/api/products")
                .header("Authorization", "Bearer " + manager()).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) created.getBody().get("id")).longValue();
    }

    private List<Map<String, Object>> variants(long productId) {
        return client().get().uri("/api/products/" + productId + "/variants").retrieve().toEntity(LIST).getBody();
    }

    @Test
    void productWithoutVariations_isSellable_fromItsOwnStock() throws Exception {
        long id = product(12);
        List<Map<String, Object>> vs = variants(id);
        assertThat(vs).hasSize(1);
        Map<String, Object> def = vs.getFirst();
        assertThat(def.get("defaultVariant")).isEqualTo(true);
        assertThat(def.get("size")).isNull();
        assertThat(((Number) def.get("stockQuantity")).intValue()).isEqualTo(12);

        // It is a normal variant to cart/checkout: price + stock lookup works.
        Map<String, Object> ps = client().get().uri("/api/variants/" + def.get("id")).retrieve().toEntity(MAP).getBody();
        assertThat(ps.get("available")).isEqualTo(true);
        assertThat(((Number) ps.get("stockQuantity")).intValue()).isEqualTo(12);

        // The product's own stock can't be deleted, only set (e.g. to 0).
        assertThat(client().delete().uri("/api/variants/" + def.get("id")).header("Authorization", "Bearer " + manager())
                .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void addingAVariation_movesStockToVariations_removingTheLastOneReturnsToProductStock() throws Exception {
        long id = product(null);
        assertThat(((Number) variants(id).getFirst().get("stockQuantity")).intValue()).isZero();

        Map<String, Object> body = new HashMap<>();
        body.put("size", "250g");
        body.put("stockQuantity", 7);
        ResponseEntity<Map<String, Object>> created = client().post().uri("/api/products/" + id + "/variants")
                .header("Authorization", "Bearer " + manager()).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Map<String, Object>> vs = variants(id);
        assertThat(vs).hasSize(1);                                   // default retired
        assertThat(vs.getFirst().get("size")).isEqualTo("250g");
        assertThat(vs.getFirst().get("defaultVariant")).isEqualTo(false);

        assertThat(client().delete().uri("/api/variants/" + created.getBody().get("id"))
                .header("Authorization", "Bearer " + manager()).retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful())
                .isTrue();
        vs = variants(id);
        assertThat(vs).hasSize(1);
        assertThat(vs.getFirst().get("defaultVariant")).isEqualTo(true); // sold as itself again
        assertThat(((Number) vs.getFirst().get("stockQuantity")).intValue()).isZero();
    }
}
