package com.matessa.order;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
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
import org.testcontainers.kafka.KafkaContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CheckoutApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("orders");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

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
        registry.add("spring.cloud.openfeign.client.config.commercial-service.url",
                () -> "http://localhost:" + COMMERCIAL.port());
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("outbox.poll-interval-ms", () -> "500");   // publish quickly in the test

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

    @Value("${local.server.port}")
    int port;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(subject).claim("roles", List.of("CUSTOMER"))
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

    private void addToCart(String token, long variantId, int qty) {
        ResponseEntity<String> res = client().post().uri("/api/cart/items")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("productVariantId", variantId, "quantity", qty)).retrieve().toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Map<String, Object> address() {
        return Map.of("recipientName", "Alice", "line1", "1 Trail Rd", "city", "Boulder",
                "postalCode", "80301", "country", "USA");
    }

    @Test
    void checkout_snapshotsTotalsCoupon_paysAndPublishesOrderPlaced() throws Exception {
        String alice = token("100");
        stubVariant(10, true, "100.00", 5);
        addToCart(alice, 10, 2);

        // coupon FLAT20 -> 20 off
        COMMERCIAL.stubFor(post(urlEqualTo("/api/coupons/validate")).willReturn(okJson(
                "{\"valid\":true,\"code\":\"FLAT20\",\"discountType\":\"FIXED_AMOUNT\",\"discountValue\":20,"
                        + "\"discountAmount\":20.00,\"message\":\"Valid\"}")));

        ResponseEntity<Map<String, Object>> order = client().post().uri("/api/orders")
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("shippingAddress", address(), "paymentMethod", "CARD", "couponCode", "FLAT20"))
                .retrieve().toEntity(MAP);
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = order.getBody();
        assertThat(number(body, "subtotal")).isEqualTo(200.00);
        assertThat(number(body, "discountAmount")).isEqualTo(20.00);
        assertThat(number(body, "total")).isEqualTo(180.00);
        assertThat(body.get("status")).isEqualTo("PAID");
        assertThat(body.get("paymentStatus")).isEqualTo("COMPLETED");
        assertThat(body.get("transactionId")).isNotNull();
        assertThat((String) body.get("shippingAddress")).contains("Alice");
        String orderNumber = (String) body.get("orderNumber");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("sku")).isEqualTo("SKU-10");
        assertThat(number(items.get(0), "unitPrice")).isEqualTo(100.00);
        assertThat(number(items.get(0), "lineTotal")).isEqualTo(200.00);

        // cart is cleared after checkout
        ResponseEntity<Map<String, Object>> cart = client().get().uri("/api/cart")
                .header("Authorization", "Bearer " + alice).retrieve().toEntity(MAP);
        assertThat((List<?>) cart.getBody().get("items")).isEmpty();

        // the OrderPlaced event reaches Kafka (via the outbox poller)
        String payload = awaitOrderPlaced(orderNumber, Duration.ofSeconds(40));
        assertThat(payload).isNotNull();
        assertThat(payload).contains(orderNumber).contains("\"productVariantId\":10").contains("\"quantity\":2");
    }

    @Test
    void checkout_outOfStock_returns409_andEmptyCart_returns400() throws Exception {
        String bob = token("200");

        // empty cart -> 400
        ResponseEntity<String> empty = client().post().uri("/api/orders")
                .header("Authorization", "Bearer " + bob).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("shippingAddress", address(), "paymentMethod", "CARD")).retrieve().toEntity(String.class);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // stock 1 but cart wants 3 -> 409
        stubVariant(40, true, "50.00", 1);
        addToCart(bob, 40, 3);
        ResponseEntity<String> oos = client().post().uri("/api/orders")
                .header("Authorization", "Bearer " + bob).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("shippingAddress", address(), "paymentMethod", "CARD")).retrieve().toEntity(String.class);
        assertThat(oos.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private String awaitOrderPlaced(String orderNumber, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("OrderPlaced"));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.value() != null && record.value().contains(orderNumber)) {
                        return record.value();
                    }
                }
            }
        }
        return null;
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }
}
