package com.matessa.commercial;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
class CategoryApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commercial");

    private static RSAPrivateKey signingKey;

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
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", "matessa")
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    private String token(String role) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service")
                .subject("1")
                .claim("tenant", "matessa")
                .claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private Map<String, Object> category(Long parentId, String name, String slug) {
        Map<String, Object> body = new HashMap<>();
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        body.put("name", name);
        body.put("slug", slug);
        body.put("imageUrl", "https://cdn.example/" + slug + ".jpg");
        body.put("displayOrder", 0);
        return body;
    }

    @Test
    void publicReadStartsEmpty() {
        ResponseEntity<String> res = client().get().uri("/api/categories").retrieve().toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void writeWithoutToken_is401() {
        ResponseEntity<String> res = client().post().uri("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Helmets", "slug", "helmets"))
                .retrieve().toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void writeAsCustomer_is403() throws Exception {
        ResponseEntity<String> res = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + token("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Helmets", "slug", "helmets-customer"))
                .retrieve().toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerBuildsTree_thenPublicCanBrowseIt() throws Exception {
        String manager = token("MANAGER");

        // main category
        ResponseEntity<Map<String, Object>> men = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).body(category(null, "Men", "men"))
                .retrieve().toEntity(new org.springframework.core.ParameterizedTypeReference<>() { });
        assertThat(men.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long menId = ((Number) men.getBody().get("id")).longValue();
        assertThat(men.getBody().get("imageUrl")).isNotNull();

        // branch under it
        ResponseEntity<String> tshirts = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).body(category(menId, "T-shirts", "men-tshirts"))
                .retrieve().toEntity(String.class);
        assertThat(tshirts.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // duplicate slug -> 409
        ResponseEntity<String> dup = client().post().uri("/api/categories")
                .header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).body(category(null, "Men Again", "men"))
                .retrieve().toEntity(String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // public tree shows the nesting
        ResponseEntity<String> tree = client().get().uri("/api/categories/tree").retrieve().toEntity(String.class);
        assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree.getBody()).contains("\"men\"").contains("\"men-tshirts\"");

        // soft delete
        ResponseEntity<Void> deleted = client().delete().uri("/api/categories/" + menId)
                .header("Authorization", "Bearer " + manager).retrieve().toBodilessEntity();
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
