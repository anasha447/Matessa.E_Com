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
class CouponApiIntegrationTest {

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
    void coupons_crud_validate_softDelete() throws Exception {
        String manager = token("MANAGER", "1");
        String customer = token("CUSTOMER", "100");

        // staff create a 20%-off coupon with a 50 minimum spend
        Map<String, Object> save20 = new HashMap<>();
        save20.put("code", "SAVE20");
        save20.put("discountType", "PERCENTAGE");
        save20.put("discountValue", 20);
        save20.put("minSpend", 50);
        save20.put("maxUses", 5);
        save20.put("validFrom", Instant.now().minusSeconds(3600).toString());
        save20.put("validUntil", Instant.now().plusSeconds(86400).toString());
        save20.put("active", true);
        ResponseEntity<Map<String, Object>> created = client().post().uri("/api/coupons")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(save20).retrieve().toEntity(MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long couponId = ((Number) created.getBody().get("id")).longValue();

        // customers cannot create coupons
        ResponseEntity<String> forbidden = client().post().uri("/api/coupons")
                .header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON)
                .body(save20).retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // validate (authenticated): subtotal 100 -> 20 off
        Map<String, Object> ok = validate(customer, "SAVE20", 100);
        assertThat(ok.get("valid")).isEqualTo(true);
        assertThat(number(ok, "discountAmount")).isEqualTo(20.00);

        // below the minimum spend -> invalid with a reason
        Map<String, Object> low = validate(customer, "SAVE20", 40);
        assertThat(low.get("valid")).isEqualTo(false);
        assertThat((String) low.get("message")).contains("minimum");

        // unknown code -> invalid
        assertThat(validate(customer, "NOPE", 100).get("valid")).isEqualTo(false);

        // staff may list; customers may not
        ResponseEntity<String> list = client().get().uri("/api/coupons")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("SAVE20");
        assertThat(client().get().uri("/api/coupons").header("Authorization", "Bearer " + customer)
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // fixed-amount coupon, capped at the subtotal
        Map<String, Object> flat = new HashMap<>();
        flat.put("code", "FLAT15");
        flat.put("discountType", "FIXED_AMOUNT");
        flat.put("discountValue", 15);
        flat.put("validFrom", Instant.now().minusSeconds(60).toString());
        flat.put("validUntil", Instant.now().plusSeconds(86400).toString());
        flat.put("active", true);
        client().post().uri("/api/coupons").header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).body(flat).retrieve().toBodilessEntity();
        assertThat(number(validate(customer, "FLAT15", 100), "discountAmount")).isEqualTo(15.00);
        assertThat(number(validate(customer, "FLAT15", 10), "discountAmount")).isEqualTo(10.00); // capped

        // expired coupon (window in the past) -> invalid
        Map<String, Object> old = new HashMap<>();
        old.put("code", "OLDCODE");
        old.put("discountType", "PERCENTAGE");
        old.put("discountValue", 50);
        old.put("validFrom", Instant.now().minusSeconds(7200).toString());
        old.put("validUntil", Instant.now().minusSeconds(3600).toString());
        old.put("active", true);
        client().post().uri("/api/coupons").header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).body(old).retrieve().toBodilessEntity();
        assertThat(validate(customer, "OLDCODE", 100).get("valid")).isEqualTo(false);

        // soft delete SAVE20 -> no longer valid
        ResponseEntity<Void> deleted = client().delete().uri("/api/coupons/" + couponId)
                .header("Authorization", "Bearer " + manager).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(validate(customer, "SAVE20", 100).get("valid")).isEqualTo(false);
    }

    private Map<String, Object> validate(String token, String code, int subtotal) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("subtotal", subtotal);
        return client().post().uri("/api/coupons/validate").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP).getBody();
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }
}
