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
class DiscountPricingApiIntegrationTest {

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

    private String token(String role, String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(subject).claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void discounts_foldIntoEffectivePrice_withCategoryCascadeAndBestWins() throws Exception {
        String manager = token("MANAGER", "1");
        Long parentCategory = createCategory(manager, "Outdoor", "outdoor", null);
        Long childCategory = createCategory(manager, "Helmets", "helmets", parentCategory);
        Long productId = createProduct(manager, childCategory, "trail-helmet", 100.00);
        Long variantId = createVariant(manager, productId, "TH-RED-M");

        // no discount -> effective price equals base price
        assertThat(number(priceStock(variantId), "effectivePrice")).isEqualTo(100.00);

        // a 10% discount on the PARENT category cascades down to a product in the child branch
        createDiscount(manager, categoryDiscountBody(parentCategory, "PERCENTAGE", 10), HttpStatus.CREATED);
        assertThat(number(priceStock(variantId), "effectivePrice")).isEqualTo(90.00);

        // a fixed 30-off product discount beats the 10% -> the single best discount wins
        Long productDiscountId = createDiscount(manager, productDiscountBody(productId, "FIXED_AMOUNT", 30), HttpStatus.CREATED);
        assertThat(number(priceStock(variantId), "effectivePrice")).isEqualTo(70.00);

        // both surface as applicable for the product (public read)
        ResponseEntity<String> applicable = client().get().uri("/api/products/" + productId + "/discounts")
                .retrieve().toEntity(String.class);
        assertThat(applicable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applicable.getBody()).contains("PERCENTAGE").contains("FIXED_AMOUNT");

        // an expired 90% discount must NOT apply
        Map<String, Object> expired = productDiscountBody(productId, "PERCENTAGE", 90);
        expired.put("validFrom", Instant.now().minusSeconds(7200).toString());
        expired.put("validTo", Instant.now().minusSeconds(3600).toString());
        createDiscount(manager, expired, HttpStatus.CREATED);
        assertThat(number(priceStock(variantId), "effectivePrice")).isEqualTo(70.00);

        // the catalog variant list shows both list price and discounted effective price
        List<Map<String, Object>> variantList = client().get().uri("/api/products/" + productId + "/variants")
                .retrieve().toEntity(LIST).getBody();
        assertThat(variantList).hasSize(1);
        assertThat(number(variantList.get(0), "listPrice")).isEqualTo(100.00);
        assertThat(number(variantList.get(0), "effectivePrice")).isEqualTo(70.00);

        // customers cannot create discounts
        ResponseEntity<String> forbidden = client().post().uri("/api/discounts")
                .header("Authorization", "Bearer " + token("CUSTOMER", "100")).contentType(MediaType.APPLICATION_JSON)
                .body(productDiscountBody(productId, "FIXED_AMOUNT", 5)).retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // target must be exactly one of product / category
        Map<String, Object> both = productDiscountBody(productId, "PERCENTAGE", 5);
        both.put("categoryId", childCategory);
        assertThat(createDiscount(manager, both, HttpStatus.BAD_REQUEST)).isNull();
        Map<String, Object> neither = new HashMap<>();
        neither.put("type", "PERCENTAGE");
        neither.put("value", 5);
        neither.put("validFrom", Instant.now().minusSeconds(60).toString());
        neither.put("validTo", Instant.now().plusSeconds(86400).toString());
        assertThat(createDiscount(manager, neither, HttpStatus.BAD_REQUEST)).isNull();

        // remove the product discount -> price falls back to the category 10%
        ResponseEntity<Void> deleted = client().delete().uri("/api/discounts/" + productDiscountId)
                .header("Authorization", "Bearer " + manager).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(number(priceStock(variantId), "effectivePrice")).isEqualTo(90.00);
    }

    private Map<String, Object> productDiscountBody(Long productId, String type, int value) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("type", type);
        body.put("value", value);
        body.put("validFrom", Instant.now().minusSeconds(60).toString());
        body.put("validTo", Instant.now().plusSeconds(86400).toString());
        body.put("active", true);
        return body;
    }

    private Map<String, Object> categoryDiscountBody(Long categoryId, String type, int value) {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        body.put("type", type);
        body.put("value", value);
        body.put("validFrom", Instant.now().minusSeconds(60).toString());
        body.put("validTo", Instant.now().plusSeconds(86400).toString());
        body.put("active", true);
        return body;
    }

    private Long createDiscount(String token, Map<String, Object> body, HttpStatus expected) {
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/discounts")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(expected);
        return expected == HttpStatus.CREATED ? ((Number) res.getBody().get("id")).longValue() : null;
    }

    private Map<String, Object> priceStock(Long variantId) {
        return client().get().uri("/api/variants/" + variantId).retrieve().toEntity(MAP).getBody();
    }

    private Long createCategory(String token, String name, String slug, Long parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("slug", slug);
        body.put("displayOrder", 0);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private Long createProduct(String token, Long categoryId, String slug, double basePrice) {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", slug);
        body.put("slug", slug);
        body.put("basePrice", basePrice);
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/products")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private Long createVariant(String token, Long productId, String sku) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("stockQuantity", 5);
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/products/" + productId + "/variants")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) res.getBody().get("id")).longValue();
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }
}
