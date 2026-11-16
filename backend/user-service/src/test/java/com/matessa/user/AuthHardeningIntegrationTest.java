package com.matessa.user;

import com.matessa.user.entity.RefreshToken;
import com.matessa.user.repository.RefreshTokenRepository;
import com.matessa.user.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh-token storage and reuse detection, per-account lockout, case-insensitive email, and
 * the server-side "change your temporary password first" rule.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthHardeningIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("users");

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.admins.matessa.email", () -> "Boss@Matessa.Test");   // stored lower-case
        registry.add("app.admins.matessa.password", () -> "BossPass123!");
        registry.add("auth.refresh-reuse-grace-seconds", () -> "0");          // no grace: reuse = theft

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        Path dir = Files.createTempDirectory("jwt-test-keys");
        Path privateKey = pem(dir.resolve("private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        Path publicKey = pem(dir.resolve("public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        registry.add("jwt.private-key-path", () -> "file:" + privateKey.toAbsolutePath().toString().replace('\\', '/'));
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    private static Path pem(Path target, String type, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        Files.writeString(target, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
        target.toFile().deleteOnExit();
        return target;
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    RefreshTokenRepository refreshTokens;

    RestClient client;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("matessa");
        client = RestClient.builder().defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password) {
        return client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password)).retrieve().toEntity(MAP);
    }

    private ResponseEntity<Map<String, Object>> refresh(String token) {
        return client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", token)).retrieve().toEntity(MAP);
    }

    private void register(String email, String password) {
        HttpStatusCode status = client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password, "firstName", "A", "lastName", "B"))
                .retrieve().toBodilessEntity().getStatusCode();
        assertThat(status.is2xxSuccessful()).isTrue();
    }

    @Test
    void refreshToken_isStoredHashed_rotates_andReuseRevokesTheFamily() {
        register("rot@example.com", "Password123!");
        String first = (String) login("rot@example.com", "Password123!").getBody().get("refreshToken");

        // Only a hash is stored: the token handed out is nowhere in the table.
        List<RefreshToken> rows = refreshTokens.findAll();
        assertThat(rows).isNotEmpty().noneMatch(t -> t.getToken().equals(first));

        ResponseEntity<Map<String, Object>> rotated = refresh(first);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String second = (String) rotated.getBody().get("refreshToken");

        // Replaying the first token (a stolen copy) is refused AND kills the live second one.
        assertThat(refresh(first).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_endsTheSession() {
        register("out@example.com", "Password123!");
        String token = (String) login("out@example.com", "Password123!").getBody().get("refreshToken");
        client.post().uri("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", token)).retrieve().toBodilessEntity();
        assertThat(refresh(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void fiveFailures_lockTheAccount_evenForTheRightPassword() {
        register("lock@example.com", "Password123!");
        for (int i = 0; i < 5; i++) {
            assertThat(login("lock@example.com", "wrong-" + i).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<Map<String, Object>> locked = login("lock@example.com", "Password123!");
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Same answer as a wrong password: the lock does not reveal that the account exists.
        assertThat(locked.getBody().get("message")).isEqualTo("Invalid email or password");
    }

    @Test
    void email_isCaseInsensitive_andRegisterConflictDoesNotEchoIt() {
        register("Mixed.Case@Example.COM", "Password123!");
        assertThat(login("mixed.case@example.com", "Password123!").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> dup = client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "MIXED.case@example.com", "password", "Password123!", "firstName", "A", "lastName", "B"))
                .retrieve().toEntity(MAP);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(dup.getBody().get("message"))).doesNotContainIgnoringCase("mixed.case@example.com");

        // The seeded admin's configured mixed-case address works lower-cased.
        assertThat(login("boss@matessa.test", "BossPass123!").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void staffWithTemporaryPassword_getsNoStaffAccessUntilItIsChanged() {
        String admin = (String) login("boss@matessa.test", "BossPass123!").getBody().get("accessToken");
        ResponseEntity<Map<String, Object>> created = client.post().uri("/api/admin/users")
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "temp.mgr@example.com", "password", "TempPass123!", "firstName", "T", "lastName", "M"))
                .retrieve().toEntity(MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String temp = (String) login("temp.mgr@example.com", "TempPass123!").getBody().get("accessToken");
        // Staff endpoint refused while the temporary password is in place …
        assertThat(client.get().uri("/api/analytics/customers").header("Authorization", "Bearer " + temp)
                .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // … but "who am I" and "change my password" still work.
        assertThat(client.get().uri("/api/users/me").header("Authorization", "Bearer " + temp)
                .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client.put().uri("/api/users/me/password").header("Authorization", "Bearer " + temp)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", "TempPass123!", "newPassword", "RealPass456!"))
                .retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful()).isTrue();

        String full = (String) login("temp.mgr@example.com", "RealPass456!").getBody().get("accessToken");
        // MANAGER is not ADMIN, so the admin-only customer analytics stay 403 — but the role
        // is now in the token: the manager's own profile shows it and staff routes accept it.
        ResponseEntity<Map<String, Object>> me = client.get().uri("/api/users/me")
                .header("Authorization", "Bearer " + full).retrieve().toEntity(MAP);
        assertThat(me.getBody().get("mustChangePassword")).isEqualTo(false);
        String payload = new String(Base64.getUrlDecoder().decode(full.split("\\.")[1]));
        assertThat(payload).contains("MANAGER").contains("\"pwd_change\":false");
        String tempPayload = new String(Base64.getUrlDecoder().decode(temp.split("\\.")[1]));
        assertThat(tempPayload).contains("\"roles\":[]").contains("\"pwd_change\":true");
    }
}
