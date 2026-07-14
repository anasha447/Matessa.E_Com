package com.matessa.user;

import com.matessa.user.dto.request.AddressRequest;
import com.matessa.user.dto.request.LoginRequest;
import com.matessa.user.dto.request.RegisterRequest;
import com.matessa.user.dto.response.AddressResponse;
import com.matessa.user.dto.response.TokenResponse;
import com.matessa.user.dto.response.UserResponse;
import com.matessa.user.entity.AddressType;
import com.matessa.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("users");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("app.admin.email", () -> "seed-admin@test.local");
        registry.add("app.admin.password", () -> "SeedPass123!");

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

    RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }) // never throw; assert on status
                .build();
    }

    @Test
    void auth_flow_register_login_me() {
        RegisterRequest register = new RegisterRequest("alice@example.com", "password123", "Alice", "Adventurer", null);

        ResponseEntity<UserResponse> registered = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).body(register).retrieve().toEntity(UserResponse.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody()).isNotNull();
        assertThat(registered.getBody().email()).isEqualTo("alice@example.com");
        assertThat(registered.getBody().roles()).containsExactly(Role.CUSTOMER);

        ResponseEntity<String> duplicate = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).body(register).retrieve().toEntity(String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<TokenResponse> login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).body(new LoginRequest("alice@example.com", "password123"))
                .retrieve().toEntity(TokenResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().accessToken()).isNotBlank();
        assertThat(login.getBody().refreshToken()).isNotBlank();

        ResponseEntity<String> badLogin = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).body(new LoginRequest("alice@example.com", "wrong-password"))
                .retrieve().toEntity(String.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(client.get().uri("/api/users/me").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<UserResponse> me = client.get().uri("/api/users/me")
                .header("Authorization", "Bearer " + login.getBody().accessToken())
                .retrieve().toEntity(UserResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().email()).isEqualTo("alice@example.com");
    }

    @Test
    void address_crud_and_ownership() {
        String tokenA = registerAndLogin("addr-a@example.com");

        // create with isDefault=true
        AddressRequest create = new AddressRequest("1 Trail Rd", null, "Boulder", "CO", "80301", "USA", AddressType.HOME, true);
        ResponseEntity<AddressResponse> created = client.post().uri("/api/users/me/addresses")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON).body(create).retrieve().toEntity(AddressResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().type()).isEqualTo(AddressType.HOME);
        assertThat(created.getBody().defaultAddress()).isTrue();
        Long addressId = created.getBody().id();

        // list contains it
        ResponseEntity<String> listA = client.get().uri("/api/users/me/addresses")
                .header("Authorization", "Bearer " + tokenA).retrieve().toEntity(String.class);
        assertThat(listA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listA.getBody()).contains("Boulder");

        // update -> WORK, not default
        AddressRequest update = new AddressRequest("2 Summit Ave", "Apt 5", "Denver", "CO", "80202", "USA", AddressType.WORK, false);
        ResponseEntity<AddressResponse> updated = client.put().uri("/api/users/me/addresses/" + addressId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON).body(update).retrieve().toEntity(AddressResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().city()).isEqualTo("Denver");
        assertThat(updated.getBody().type()).isEqualTo(AddressType.WORK);
        assertThat(updated.getBody().defaultAddress()).isFalse();

        // ownership: user B sees none of A's, and gets 404 deleting A's address
        String tokenB = registerAndLogin("addr-b@example.com");
        ResponseEntity<String> listB = client.get().uri("/api/users/me/addresses")
                .header("Authorization", "Bearer " + tokenB).retrieve().toEntity(String.class);
        assertThat(listB.getBody()).doesNotContain("Denver");
        ResponseEntity<String> bDeletesA = client.delete().uri("/api/users/me/addresses/" + addressId)
                .header("Authorization", "Bearer " + tokenB).retrieve().toEntity(String.class);
        assertThat(bDeletesA.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // unauthenticated -> 401
        assertThat(client.get().uri("/api/users/me/addresses").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // delete as owner -> 204
        ResponseEntity<Void> deleted = client.delete().uri("/api/users/me/addresses/" + addressId)
                .header("Authorization", "Bearer " + tokenA).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void seededAdmin_canLogIn_withAdminAndManagerRoles() {
        // AdminSeeder runs on startup with app.admin.* supplied via @DynamicPropertySource
        ResponseEntity<TokenResponse> login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("seed-admin@test.local", "SeedPass123!"))
                .retrieve().toEntity(TokenResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().accessToken()).isNotBlank();

        ResponseEntity<UserResponse> me = client.get().uri("/api/users/me")
                .header("Authorization", "Bearer " + login.getBody().accessToken())
                .retrieve().toEntity(UserResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().email()).isEqualTo("seed-admin@test.local");
        assertThat(me.getBody().roles()).contains(Role.ADMIN, Role.MANAGER);
    }

    private String registerAndLogin(String email) {
        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, "password123", "First", "Last", null)).retrieve().toBodilessEntity();
        TokenResponse tokens = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "password123")).retrieve().body(TokenResponse.class);
        return tokens.accessToken();
    }
}
