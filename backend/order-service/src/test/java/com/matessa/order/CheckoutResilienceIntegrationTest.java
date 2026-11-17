package com.matessa.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.service.PaymentResult;
import com.matessa.order.service.PaymentService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * The failure paths of checkout: what happens to held stock and to the idempotency key when
 * something goes wrong after the reservation, and how retries and concurrent submits behave.
 * Commercial-service is WireMock; Kafka is not needed (the outbox simply stays unpublished).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CheckoutResilienceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("orders");

    static final WireMockServer COMMERCIAL = new WireMockServer(options().dynamicPort());

    static {
        COMMERCIAL.start();
    }

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.openfeign.client.config.commercial-service.url",
                () -> "http://localhost:" + COMMERCIAL.port());
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");   // never reached here
        registry.add("outbox.poll-interval-ms", () -> "3600000");

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

    @MockitoSpyBean
    PaymentService payments;

    @Value("${local.server.port}")
    int port;

    @BeforeEach
    void reset() {
        COMMERCIAL.resetAll();
        COMMERCIAL.stubFor(post(urlMatching("/internal/reservations/[^/]+/release")).willReturn(aResponse().withStatus(204)));
        Mockito.reset(payments);
    }

    private RestClient client() {
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private void stubReservation(int delayMs) {
        COMMERCIAL.stubFor(post(urlEqualTo("/internal/reservations")).willReturn(okJson(
                "{\"lines\":[{\"productVariantId\":10,\"productId\":1,\"sku\":\"SKU-10\",\"productName\":\"Item 10\","
                        + "\"unitPrice\":100.00,\"quantity\":1}],\"subtotal\":100.00,\"couponCode\":null,"
                        + "\"discountAmount\":0,\"total\":100.00}").withFixedDelay(delayMs)));
    }

    private Map<String, Object> guestOrder(String email, int qty) {
        return Map.of(
                "guestEmail", email,
                "items", List.of(Map.of("productVariantId", 10, "quantity", qty)),
                "shippingAddress", Map.of("recipientName", "Asha", "line1", "12 MG Road", "city", "Pune",
                        "postalCode", "411001", "country", "India", "state", "Maharashtra", "phone", "9876543210"),
                "paymentMethod", "COD",
                "shippingMethod", "Standard");
    }

    private ResponseEntity<Map<String, Object>> placeGuest(Map<String, Object> body, String key) {
        return client().post().uri("/api/orders/guest").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key).body(body).retrieve().toEntity(MAP);
    }

    private List<String> reservationIdsSent() throws Exception {
        List<String> ids = new ArrayList<>();
        for (LoggedRequest r : COMMERCIAL.findAll(postRequestedFor(urlEqualTo("/internal/reservations")))) {
            ids.add(JSON.readTree(r.getBodyAsString()).get("reservationId").asText());
        }
        return ids;
    }

    @Test
    void declinedPayment_releasesTheReservation_andFreesTheKey() throws Exception {
        stubReservation(0);
        Mockito.doReturn(new PaymentResult(PaymentStatus.FAILED, null))
                .when(payments).charge(any(PaymentMethod.class), any(BigDecimal.class));
        String key = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> declined = placeGuest(guestOrder("decline@example.com", 1), key);
        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String reservationId = reservationIdsSent().getFirst();
        COMMERCIAL.verify(1, postRequestedFor(urlEqualTo("/internal/reservations/" + reservationId + "/release")));

        // The failed attempt freed the key: the same visit can retry and succeed.
        Mockito.reset(payments);
        assertThat(placeGuest(guestOrder("decline@example.com", 1), key).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void reservationConflict_is409_andReleasesNothing() {
        COMMERCIAL.stubFor(post(urlEqualTo("/internal/reservations")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":409,\"error\":\"Conflict\",\"message\":\"Insufficient stock\"}")));
        ResponseEntity<Map<String, Object>> res = placeGuest(guestOrder("short@example.com", 1), UUID.randomUUID().toString());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        COMMERCIAL.verify(0, postRequestedFor(urlMatching("/internal/reservations/[^/]+/release")));
    }

    @Test
    void sameKey_sameBasket_returnsTheSameOrder_differentBasket_is422() throws Exception {
        stubReservation(0);
        String key = UUID.randomUUID().toString();
        ResponseEntity<Map<String, Object>> first = placeGuest(guestOrder("replay@example.com", 1), key);
        ResponseEntity<Map<String, Object>> again = placeGuest(guestOrder("replay@example.com", 1), key);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getBody().get("orderNumber")).isEqualTo(first.getBody().get("orderNumber"));
        assertThat(reservationIdsSent()).hasSize(1); // the replay never reserved again

        ResponseEntity<Map<String, Object>> changed = placeGuest(guestOrder("replay@example.com", 2), key);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(changed.getBody().get("code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void registeredRetry_afterSuccess_returnsTheOrder_althoughTheCartWasEmptied() throws Exception {
        stubReservation(0);
        COMMERCIAL.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/variants/10")).willReturn(okJson(
                "{\"variantId\":10,\"productId\":1,\"sku\":\"SKU-10\",\"productName\":\"Item 10\","
                        + "\"effectivePrice\":100.00,\"stockQuantity\":50,\"available\":true}")));
        String bearer = "Bearer " + token(String.valueOf(9000 + new java.util.Random().nextInt(1000)));
        assertThat(client().post().uri("/api/cart/items").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", 10, "quantity", 1)).retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful())
                .isTrue();
        Map<String, Object> body = new java.util.HashMap<>(guestOrder("ignored@example.com", 1));
        body.remove("guestEmail");
        body.remove("items");
        String key = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> first = client().post().uri("/api/orders").header("Authorization", bearer)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // The response was "lost": the shopper retries with the same key. The cart is now empty.
        ResponseEntity<Map<String, Object>> retry = client().post().uri("/api/orders").header("Authorization", bearer)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody().get("orderNumber")).isEqualTo(first.getBody().get("orderNumber"));
        assertThat(reservationIdsSent()).hasSize(1);

        // Same key, different request: still refused.
        Map<String, Object> changed = new java.util.HashMap<>(body);
        changed.put("shippingMethod", "Express");
        ResponseEntity<Map<String, Object>> other = client().post().uri("/api/orders").header("Authorization", bearer)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).body(changed).retrieve().toEntity(MAP);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void twoSimultaneousSubmits_withOneKey_placeOneOrder() throws Exception {
        stubReservation(1500); // keep the first request in flight while the second arrives
        String key = UUID.randomUUID().toString();
        CompletableFuture<ResponseEntity<Map<String, Object>>> a =
                CompletableFuture.supplyAsync(() -> placeGuest(guestOrder("race@example.com", 1), key));
        Thread.sleep(300);
        CompletableFuture<ResponseEntity<Map<String, Object>>> b =
                CompletableFuture.supplyAsync(() -> placeGuest(guestOrder("race@example.com", 1), key));

        Set<Object> orderNumbers = new HashSet<>();
        int inProgress = 0;
        for (ResponseEntity<Map<String, Object>> r : List.of(a.get(), b.get())) {
            if (r.getStatusCode() == HttpStatus.CREATED) {
                orderNumbers.add(r.getBody().get("orderNumber"));
            } else {
                assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(r.getBody().get("code")).isEqualTo("REQUEST_IN_PROGRESS");
                inProgress++;
            }
        }
        assertThat(orderNumbers).hasSize(1);
        assertThat(reservationIdsSent()).hasSize(1);
        assertThat(inProgress).isLessThanOrEqualTo(1);
    }

    @Test
    void guestCheckout_sendsTheServiceKey_lowercasesEmail_andUsesARandomOrderNumber() throws Exception {
        stubReservation(0);
        ResponseEntity<Map<String, Object>> res = placeGuest(guestOrder("Mixed.Case@Example.COM", 1), UUID.randomUUID().toString());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) res.getBody().get("orderNumber")).matches("MA\\d{6}-[0-9A-HJKMNP-TV-Z]{6}");

        LoggedRequest reserve = COMMERCIAL.findAll(postRequestedFor(urlEqualTo("/internal/reservations"))).getFirst();
        assertThat(reserve.getHeader("X-Internal-Key")).isEqualTo("test-internal-key");
        JsonNode body = JSON.readTree(reserve.getBodyAsString());
        assertThat(body.get("customerKey").asText()).isEqualTo("e:mixed.case@example.com");

        // Tracking works with any casing of the email.
        ResponseEntity<Map<String, Object>> tracked = client().get()
                .uri("/api/orders/track?number={n}&email={e}", res.getBody().get("orderNumber"), "MIXED.case@example.com")
                .retrieve().toEntity(MAP);
        assertThat(tracked.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void oversizedOrder_isRefusedBeforeAnythingIsReserved() {
        ResponseEntity<Map<String, Object>> res = placeGuest(guestOrder("bulk@example.com", 500), UUID.randomUUID().toString());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        COMMERCIAL.verify(0, postRequestedFor(urlEqualTo("/internal/reservations")));
    }

    @Test
    void paymentMethods_arePublic_andListWhatThisDeploymentTakes() {
        ResponseEntity<Map<String, Object>> res = client().get().uri("/api/orders/payment-methods").retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(res.getBody().get("methods"))).contains("COD");
    }

    @SuppressWarnings("unused")
    private static String token(String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject(subject).claim("tenant", "matessa")
                .claim("roles", List.of("CUSTOMER")).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
