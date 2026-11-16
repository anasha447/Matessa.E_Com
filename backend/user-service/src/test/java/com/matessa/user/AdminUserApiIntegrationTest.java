package com.matessa.user;

import com.matessa.user.dto.request.ChangePasswordRequest;
import com.matessa.user.dto.request.LoginRequest;
import com.matessa.user.dto.request.RefreshRequest;
import com.matessa.user.dto.response.TokenResponse;
import com.matessa.user.dto.response.UserResponse;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdminUserApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("users");

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    /** Admin acts under an id that won't collide with the rows created during the test. */
    private static final long ADMIN_ID = 9999L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.admin.seed-enabled", () -> "false");

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

    private RestClient client() {
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(long subject, String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject(String.valueOf(subject)).claim("tenant", "matessa")
                .claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void adminProvisionsManager_selfService_andBan() throws Exception {
        RestClient client = client();
        String adminToken = token(ADMIN_ID, "ADMIN");

        // 1. admin provisions a MANAGER with a temp password (force-change set)
        Map<String, Object> created = client.post().uri("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "mgr@example.com", "password", "TempPass123!",
                        "firstName", "Mara", "lastName", "Manager"))
                .retrieve().toEntity(MAP).getBody();
        assertThat(created).isNotNull();
        long managerId = ((Number) created.get("id")).longValue();
        assertThat(created.get("roles").toString()).contains("MANAGER");
        assertThat((Boolean) created.get("mustChangePassword")).isTrue();
        assertThat((Boolean) created.get("enabled")).isTrue();

        // 2. duplicate email -> 409
        ResponseEntity<String> duplicate = client.post().uri("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "mgr@example.com", "password", "TempPass123!",
                        "firstName", "Dup", "lastName", "Licate"))
                .retrieve().toEntity(String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 3. a non-admin cannot provision staff
        ResponseEntity<String> managerForbidden = client.post().uri("/api/admin/users")
                .header("Authorization", "Bearer " + token(2L, "MANAGER")).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "nope@example.com", "password", "TempPass123!",
                        "firstName", "N", "lastName", "O"))
                .retrieve().toEntity(String.class);
        assertThat(managerForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. the manager logs in with the temp password; /me flags the forced change
        TokenResponse mgrTokens = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("mgr@example.com", "TempPass123!")).retrieve().body(TokenResponse.class);
        assertThat(mgrTokens).isNotNull();
        UserResponse me = client.get().uri("/api/users/me")
                .header("Authorization", "Bearer " + mgrTokens.accessToken()).retrieve().body(UserResponse.class);
        assertThat(me).isNotNull();
        assertThat(me.mustChangePassword()).isTrue();

        // 5. manager changes own password -> 204; old refresh revoked; flag cleared
        ResponseEntity<Void> changed = client.put().uri("/api/users/me/password")
                .header("Authorization", "Bearer " + mgrTokens.accessToken()).contentType(MediaType.APPLICATION_JSON)
                .body(new ChangePasswordRequest("TempPass123!", "NewPass456!")).retrieve().toBodilessEntity();
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> staleRefresh = client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(mgrTokens.refreshToken())).retrieve().toEntity(String.class);
        assertThat(staleRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("mgr@example.com", "TempPass123!")).retrieve().toEntity(String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        TokenResponse afterChange = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("mgr@example.com", "NewPass456!")).retrieve().body(TokenResponse.class);
        assertThat(afterChange).isNotNull();
        UserResponse me2 = client.get().uri("/api/users/me")
                .header("Authorization", "Bearer " + afterChange.accessToken()).retrieve().body(UserResponse.class);
        assertThat(me2.mustChangePassword()).isFalse();

        // 6. wrong current password -> 400
        ResponseEntity<String> wrongCurrent = client.put().uri("/api/users/me/password")
                .header("Authorization", "Bearer " + afterChange.accessToken()).contentType(MediaType.APPLICATION_JSON)
                .body(new ChangePasswordRequest("not-the-password", "Another789!")).retrieve().toEntity(String.class);
        assertThat(wrongCurrent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 7. admin bans the manager -> login and refresh both blocked
        Map<String, Object> banned = client.put().uri("/api/admin/users/" + managerId + "/status")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", false)).retrieve().toEntity(MAP).getBody();
        assertThat(banned).isNotNull();
        assertThat((Boolean) banned.get("enabled")).isFalse();

        assertThat(client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("mgr@example.com", "NewPass456!")).retrieve().toEntity(String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> bannedRefresh = client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(afterChange.refreshToken())).retrieve().toEntity(String.class);
        assertThat(bannedRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 8. an admin cannot disable their own account
        ResponseEntity<String> selfBan = client.put().uri("/api/admin/users/" + ADMIN_ID + "/status")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", false)).retrieve().toEntity(String.class);
        assertThat(selfBan.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 9. directory search by role returns the manager; status filter respects the ban
        Map<String, Object> managers = client.get().uri("/api/admin/users?role=MANAGER&page=0&size=20")
                .header("Authorization", "Bearer " + adminToken).retrieve().toEntity(MAP).getBody();
        assertThat(managers).isNotNull();
        assertThat(managers.get("content").toString()).contains("mgr@example.com");

        Map<String, Object> disabled = client.get().uri("/api/admin/users?enabled=false&page=0&size=20")
                .header("Authorization", "Bearer " + adminToken).retrieve().toEntity(MAP).getBody();
        assertThat(disabled).isNotNull();
        assertThat(disabled.get("content").toString()).contains("mgr@example.com");
    }
}
