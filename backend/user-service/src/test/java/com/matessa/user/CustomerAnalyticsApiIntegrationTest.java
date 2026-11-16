package com.matessa.user;

import com.matessa.user.entity.Role;
import com.matessa.user.entity.User;
import com.matessa.user.repository.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CustomerAnalyticsApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("users");

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.admin.seed-enabled", () -> "false");   // don't seed an extra admin

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        signingKey = (RSAPrivateKey) keyPair.getPrivate();
        Path dir = Files.createTempDirectory("jwt-test-keys");
        Path privateKey = writePem(dir.resolve("private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        Path publicKey = writePem(dir.resolve("public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        registry.add("jwt.private-key-path", () -> "file:" + privateKey.toAbsolutePath().toString().replace('\\', '/'));
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    private static Path writePem(Path target, String type, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        Files.writeString(target, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
        target.toFile().deleteOnExit();
        return target;
    }

    @Value("${local.server.port}")
    int port;

    @Autowired UserRepository userRepository;

    private RestClient client() {
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject("1").claim("tenant", "matessa")
                .claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private void user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setFirstName("F");
        u.setLastName("L");
        u.setEnabled(true);
        u.getRoles().add(role);
        userRepository.save(u);
    }

    private static double num(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String k) {
        return (Map<String, Object>) m.get(k);
    }

    @Test
    void customerAnalytics_counts_byRole_andAdminOnly() throws Exception {
        user("c1@example.com", Role.CUSTOMER);
        user("c2@example.com", Role.CUSTOMER);
        user("c3@example.com", Role.CUSTOMER);
        user("m@example.com", Role.MANAGER);
        user("a@example.com", Role.ADMIN);

        Map<String, Object> body = client().get().uri("/api/analytics/customers")
                .header("Authorization", "Bearer " + token("ADMIN")).retrieve().toEntity(MAP).getBody();

        assertThat(num(body, "totalUsers")).isEqualTo(5.0);
        assertThat(num(body, "totalCustomers")).isEqualTo(3.0);
        assertThat(num(body, "newThisMonth")).isEqualTo(5.0);
        assertThat(num(body, "newThisYear")).isEqualTo(5.0);
        assertThat(((Number) sub(body, "usersByRole").get("CUSTOMER")).longValue()).isEqualTo(3);
        assertThat(((Number) sub(body, "usersByRole").get("MANAGER")).longValue()).isEqualTo(1);
        assertThat(((Number) sub(body, "usersByRole").get("ADMIN")).longValue()).isEqualTo(1);

        // managers do NOT get customer analytics (admin only)
        ResponseEntity<String> managerForbidden = client().get().uri("/api/analytics/customers")
                .header("Authorization", "Bearer " + token("MANAGER")).retrieve().toEntity(String.class);
        assertThat(managerForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // customers definitely cannot
        ResponseEntity<String> customerForbidden = client().get().uri("/api/analytics/customers")
                .header("Authorization", "Bearer " + token("CUSTOMER")).retrieve().toEntity(String.class);
        assertThat(customerForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
