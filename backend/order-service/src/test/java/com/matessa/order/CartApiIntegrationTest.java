package com.matessa.order;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CartApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("orders");

    static final WireMockServer COMMERCIAL = new WireMockServer(options().dynamicPort());

    static {
        COMMERCIAL.start();
    }

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        // point the commercial-service Feign client at WireMock instead of discovery
        registry.add("spring.cloud.openfeign.client.config.commercial-service.url",
                () -> "http://localhost:" + COMMERCIAL.port());

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        signingKey = (RSAPrivateKey) keyPair.getPrivate();
        Path dir = Files.createTempDirectory("order-test-keys");
        Path publicKey = dir.resolve("public.pem");
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        publicKey.toFile().deleteOnExit();
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @AfterAll
    static void stopWireMock() {
        COMMERCIAL.stop();
    }

    @BeforeEach
    void resetStubs() {
        COMMERCIAL.resetAll();
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

    private void stubVariant(long id, boolean available, String price, int stock) {
        COMMERCIAL.stubFor(get(urlEqualTo("/api/variants/" + id)).willReturn(okJson(
                "{\"variantId\":" + id + ",\"productId\":1,\"sku\":\"SKU-" + id + "\",\"productName\":\"Item " + id
                        + "\",\"effectivePrice\":" + price + ",\"stockQuantity\":" + stock
                        + ",\"available\":" + available + "}")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> cart) {
        return (List<Map<String, Object>>) cart.get("items");
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }

    @Test
    void cart_livePricing_merge_update_remove_andFeignResilience() throws Exception {
        String alice = token("CUSTOMER", "100");
        stubVariant(10, true, "100.00", 5);

        // add variant 10 x2 -> live-priced line, subtotal 200
        ResponseEntity<Map<String, Object>> added = client().post().uri("/api/cart/items")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", 10, "quantity", 2)).retrieve().toEntity(MAP);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(number(added.getBody(), "subtotal")).isEqualTo(200.00);
        Long itemId = ((Number) items(added.getBody()).get(0).get("id")).longValue();
        assertThat(items(added.getBody()).get(0).get("available")).isEqualTo(true);

        // adding the same variant merges quantity -> 3, subtotal 300
        ResponseEntity<Map<String, Object>> merged = client().post().uri("/api/cart/items")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", 10, "quantity", 1)).retrieve().toEntity(MAP);
        assertThat(items(merged.getBody())).hasSize(1);
        assertThat(number(merged.getBody(), "subtotal")).isEqualTo(300.00);

        // update quantity to 1 -> subtotal 100
        ResponseEntity<Map<String, Object>> updated = client().put().uri("/api/cart/items/" + itemId)
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", 1)).retrieve().toEntity(MAP);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(updated.getBody(), "subtotal")).isEqualTo(100.00);

        // an unavailable variant is rejected
        stubVariant(20, false, "50.00", 0);
        ResponseEntity<String> unavailable = client().post().uri("/api/cart/items")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", 20, "quantity", 1)).retrieve().toEntity(String.class);
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // commercial down on add -> 503 (fail fast, don't add)
        COMMERCIAL.stubFor(get(urlEqualTo("/api/variants/30")).willReturn(aResponse().withStatus(500)));
        ResponseEntity<String> down = client().post().uri("/api/cart/items")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", 30, "quantity", 1)).retrieve().toEntity(String.class);
        assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // commercial down on cart view -> line degrades to unavailable, cart still renders
        COMMERCIAL.stubFor(get(urlEqualTo("/api/variants/10")).willReturn(aResponse().withStatus(500)));
        ResponseEntity<Map<String, Object>> degraded = client().get().uri("/api/cart")
                .header("Authorization", "Bearer " + alice).retrieve().toEntity(MAP);
        assertThat(degraded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(items(degraded.getBody())).hasSize(1);
        assertThat(items(degraded.getBody()).get(0).get("available")).isEqualTo(false);
        assertThat(number(degraded.getBody(), "subtotal")).isEqualTo(0.00);

        // anonymous -> 401
        assertThat(client().get().uri("/api/cart").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // remove the line
        ResponseEntity<Void> removed = client().delete().uri("/api/cart/items/" + itemId)
                .header("Authorization", "Bearer " + alice).retrieve().toBodilessEntity();
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map<String, Object>> empty = client().get().uri("/api/cart")
                .header("Authorization", "Bearer " + alice).retrieve().toEntity(MAP);
        assertThat(items(empty.getBody())).isEmpty();
    }
}
