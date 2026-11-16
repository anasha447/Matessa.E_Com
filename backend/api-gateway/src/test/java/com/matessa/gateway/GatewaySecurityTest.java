package com.matessa.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge-security contract for the gateway: validate the RS256 JWT once at the edge.
 * No Testcontainers/Eureka needed — Eureka is disabled, so routed requests have no
 * upstream and fail with 5xx; the point is whether the EDGE accepts/rejects the token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static RSAPrivateKey signingKey;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("eureka.client.enabled", () -> "false");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        signingKey = (RSAPrivateKey) keyPair.getPrivate();

        Path dir = Files.createTempDirectory("gw-test-keys");
        Path publicKey = dir.resolve("public.pem");
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        publicKey.toFile().deleteOnExit();
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return client("matessa");
    }

    private RestClient client(String store) {
        RestClient.Builder builder = RestClient.builder();
        if (store != null) {
            builder.defaultHeader("X-Tenant-ID", store);
        }
        return builder
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }) // never throw; assert on status
                .build();
    }

    private String validToken() throws Exception {
        return tokenFor("matessa");
    }

    private String tokenFor(String store) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("1")
                .claim("tenant", store)
                .claim("roles", List.of("CUSTOMER"))
                .issuer("matessa-user-service")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void protectedRoute_withoutToken_rejectedAtEdge() {
        ResponseEntity<String> response = client().get().uri("/api/users/me").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRoute_withJunkToken_rejectedAtEdge() {
        ResponseEntity<String> response = client().get().uri("/api/users/me")
                .header("Authorization", "Bearer not.a.valid.jwt")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRoute_withValidToken_passesEdge() throws Exception {
        ResponseEntity<String> response = client().get().uri("/api/users/me")
                .header("Authorization", "Bearer " + validToken())
                .retrieve().toEntity(String.class);
        // Token cleared the edge; with no upstream (Eureka disabled) it routes to nothing -> 5xx, not 401.
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }

    @Test
    void publicAuthRoute_withoutToken_notRejectedAtEdge() {
        ResponseEntity<String> response = client().post().uri("/api/auth/login").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apiCall_withoutStore_isRefusedAtEdge() {
        ResponseEntity<String> response = client(null).get().uri("/api/products").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void apiCall_withUnknownStore_isRefusedAtEdge() {
        ResponseEntity<String> response = client("public").get().uri("/api/products").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tokenFromAnotherStore_isRefusedAtEdge() throws Exception {
        // A valid, correctly signed matessa token replayed against the adventure store.
        ResponseEntity<String> response = client("adventure").get().uri("/api/users/me")
                .header("Authorization", "Bearer " + tokenFor("matessa"))
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void guestStorefrontForms_areNotRejectedAtEdge() {
        // Contact and newsletter posts come from signed-out shoppers; they used to get a 401 here.
        ResponseEntity<String> contact = client().post().uri("/api/contact").retrieve().toEntity(String.class);
        ResponseEntity<String> subscribe = client().post().uri("/api/subscribers").retrieve().toEntity(String.class);
        assertThat(contact.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(subscribe.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
