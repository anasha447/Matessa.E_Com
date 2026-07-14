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
class ReviewApiIntegrationTest {

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

    private String token(String role, String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(subject).claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void reviews_lifecycle_ownership_summary_moderation() throws Exception {
        String manager = token("MANAGER", "1");
        String alice = token("CUSTOMER", "100");
        String bob = token("CUSTOMER", "200");
        Long productId = createProduct(manager);

        // Alice reviews the product
        Long aliceReview = postReview(alice, productId, 5, "Great helmet", HttpStatus.CREATED);
        // one review per user per product
        postReview(alice, productId, 4, "changed my mind", HttpStatus.CONFLICT);

        // public list + summary reflect Alice's review
        ResponseEntity<String> list = client().get().uri("/api/products/" + productId + "/reviews")
                .retrieve().toEntity(String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("Great helmet");
        assertThat(number(summary(productId), "averageRating")).isEqualTo(5.0);
        assertThat(number(summary(productId), "reviewCount")).isEqualTo(1.0);

        // Bob reviews too -> average is (5+3)/2 = 4.0
        Long bobReview = postReview(bob, productId, 3, "ok", HttpStatus.CREATED);
        assertThat(number(summary(productId), "averageRating")).isEqualTo(4.0);
        assertThat(number(summary(productId), "reviewCount")).isEqualTo(2.0);

        // Bob cannot edit Alice's review (masked as 404)
        ResponseEntity<String> bobEditsAlice = client().put().uri("/api/reviews/" + aliceReview)
                .header("Authorization", "Bearer " + bob).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("rating", 1)).retrieve().toEntity(String.class);
        assertThat(bobEditsAlice.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Alice edits her own review down to 1 -> average (1+3)/2 = 2.0
        ResponseEntity<Map<String, Object>> aliceEdit = client().put().uri("/api/reviews/" + aliceReview)
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("rating", 1, "text", "scratched easily")).retrieve().toEntity(MAP);
        assertThat(aliceEdit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(summary(productId), "averageRating")).isEqualTo(2.0);

        // validation: rating must be 1..5
        ResponseEntity<String> badRating = client().post().uri("/api/products/" + productId + "/reviews")
                .header("Authorization", "Bearer " + token("CUSTOMER", "300")).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("rating", 6)).retrieve().toEntity(String.class);
        assertThat(badRating.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // anonymous cannot post a review
        ResponseEntity<String> anon = client().post().uri("/api/products/" + productId + "/reviews")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("rating", 4)).retrieve().toEntity(String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Bob deletes his own review -> count back to 1
        ResponseEntity<Void> bobDeletes = client().delete().uri("/api/reviews/" + bobReview)
                .header("Authorization", "Bearer " + bob).retrieve().toBodilessEntity();
        assertThat(bobDeletes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(number(summary(productId), "reviewCount")).isEqualTo(1.0);

        // manager moderates: deletes Alice's review -> count 0
        ResponseEntity<Void> managerDeletes = client().delete().uri("/api/reviews/" + aliceReview)
                .header("Authorization", "Bearer " + manager).retrieve().toBodilessEntity();
        assertThat(managerDeletes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(number(summary(productId), "reviewCount")).isEqualTo(0.0);
        assertThat(number(summary(productId), "averageRating")).isEqualTo(0.0);
    }

    private Long createProduct(String managerToken) {
        ResponseEntity<Map<String, Object>> category = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Helmets", "slug", "helmets", "displayOrder", 0)).retrieve().toEntity(MAP);
        Long categoryId = ((Number) category.getBody().get("id")).longValue();
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", "Trail Helmet");
        body.put("slug", "trail-helmet");
        body.put("basePrice", 100.00);
        ResponseEntity<Map<String, Object>> product = client().post().uri("/api/products")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) product.getBody().get("id")).longValue();
    }

    private Long postReview(String token, Long productId, int rating, String text, HttpStatus expected) {
        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        body.put("text", text);
        ResponseEntity<Map<String, Object>> res = client().post().uri("/api/products/" + productId + "/reviews")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(expected);
        return expected == HttpStatus.CREATED ? ((Number) res.getBody().get("id")).longValue() : null;
    }

    private Map<String, Object> summary(Long productId) {
        return client().get().uri("/api/products/" + productId + "/reviews/summary").retrieve().toEntity(MAP).getBody();
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }
}
