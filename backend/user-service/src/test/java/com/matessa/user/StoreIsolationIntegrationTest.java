package com.matessa.user;

import com.matessa.user.dto.request.LoginRequest;
import com.matessa.user.dto.request.RegisterRequest;
import com.matessa.user.dto.response.TokenResponse;
import com.matessa.user.dto.response.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-per-store isolation: each store is its own user base, its own admin, and a token
 * issued by one store is worthless in the other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StoreIsolationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("users");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.tenancy.tenants", () -> "matessa,adventure");
        registry.add("app.admins.matessa.email", () -> "admin@matessa.test");
        registry.add("app.admins.matessa.password", () -> "MatessaPass123!");
        registry.add("app.admins.adventure.email", () -> "admin@adventure.test");
        registry.add("app.admins.adventure.password", () -> "AdventurePass123!");

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
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

    RestClient matessa;
    RestClient adventure;
    RestClient noStore;

    @BeforeEach
    void setUp() {
        matessa = client("matessa");
        adventure = client("adventure");
        noStore = client(null);
    }

    private RestClient client(String store) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { });
        if (store != null) {
            builder.defaultHeader("X-Tenant-ID", store);
        }
        return builder.build();
    }

    /** Status only: a refused login answers with an error body, not a TokenResponse. */
    private static HttpStatusCode loginStatus(RestClient store, String email, String password) {
        return store.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password)).retrieve().toBodilessEntity().getStatusCode();
    }

    private static ResponseEntity<TokenResponse> login(RestClient store, String email, String password) {
        return store.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password)).retrieve().toEntity(TokenResponse.class);
    }

    @Test
    void each_store_has_its_own_admin() {
        assertThat(loginStatus(matessa, "admin@matessa.test", "MatessaPass123!")).isEqualTo(HttpStatus.OK);
        assertThat(loginStatus(adventure, "admin@adventure.test", "AdventurePass123!")).isEqualTo(HttpStatus.OK);

        // An admin exists only in its own store.
        assertThat(loginStatus(adventure, "admin@matessa.test", "MatessaPass123!"))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus(matessa, "admin@adventure.test", "AdventurePass123!"))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void same_email_registers_independently_and_tokens_do_not_cross_stores() {
        RegisterRequest bob = new RegisterRequest("bob@example.com", "password123", "Bob", "Shopper", null);
        assertThat(matessa.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).body(bob)
                .retrieve().toEntity(UserResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Not a duplicate: the other store is a separate user base.
        assertThat(adventure.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).body(bob)
                .retrieve().toEntity(UserResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String matessaToken = login(matessa, "bob@example.com", "password123").getBody().accessToken();

        assertThat(matessa.get().uri("/api/users/me").header("Authorization", "Bearer " + matessaToken)
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        // The same valid, signed token is rejected by the other store.
        assertThat(adventure.get().uri("/api/users/me").header("Authorization", "Bearer " + matessaToken)
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requests_without_a_known_store_are_refused() {
        assertThat(noStore.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("admin@matessa.test", "MatessaPass123!"))
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(client("public").get().uri("/api/users/me")
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
