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
class ProductApiIntegrationTest {

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

    private Long createCategory(String managerToken) {
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Helmets", "slug", "helmets", "displayOrder", 0))
                .retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }

    @Test
    void productAndVariant_lifecycle_withPriceStock() throws Exception {
        String manager = token("MANAGER");
        Long categoryId = createCategory(manager);

        Map<String, Object> productBody = new HashMap<>();
        productBody.put("categoryId", categoryId);
        productBody.put("name", "Trail Helmet");
        productBody.put("slug", "trail-helmet");
        productBody.put("basePrice", 100.00);
        productBody.put("brand", "AdventureDose");
        ResponseEntity<Map<String, Object>> product = client().post().uri("/api/products")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(productBody).retrieve().toEntity(MAP);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long productId = ((Number) product.getBody().get("id")).longValue();

        // public list contains it
        ResponseEntity<String> list = client().get().uri("/api/products").retrieve().toEntity(String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("trail-helmet");

        // customers cannot create products
        ResponseEntity<String> forbidden = client().post().uri("/api/products")
                .header("Authorization", "Bearer " + token("CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                .body(productBody).retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // variant with no override -> effective price = base price
        Map<String, Object> variantBody = new HashMap<>();
        variantBody.put("sku", "TH-RED-M");
        variantBody.put("size", "M");
        variantBody.put("color", "Red");
        variantBody.put("stockQuantity", 5);
        ResponseEntity<Map<String, Object>> variant = client().post().uri("/api/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(variantBody).retrieve().toEntity(MAP);
        assertThat(variant.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(number(variant.getBody(), "effectivePrice")).isEqualTo(100.00);
        Long variantId = ((Number) variant.getBody().get("id")).longValue();

        // order-facing price/stock lookup (public)
        ResponseEntity<Map<String, Object>> priceStock = client().get().uri("/api/variants/" + variantId)
                .retrieve().toEntity(MAP);
        assertThat(priceStock.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(priceStock.getBody(), "effectivePrice")).isEqualTo(100.00);
        assertThat(number(priceStock.getBody(), "stockQuantity")).isEqualTo(5);
        assertThat(priceStock.getBody().get("available")).isEqualTo(true);

        // soft delete -> no longer available
        ResponseEntity<Void> deleted = client().delete().uri("/api/variants/" + variantId)
                .header("Authorization", "Bearer " + manager).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map<String, Object>> afterDelete = client().get().uri("/api/variants/" + variantId)
                .retrieve().toEntity(MAP);
        assertThat(afterDelete.getBody().get("available")).isEqualTo(false);
    }
}
