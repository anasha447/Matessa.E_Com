package com.matessa.order;

import com.matessa.order.entity.Order;
import com.matessa.order.entity.OrderItem;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.entity.ShippingStatus;
import com.matessa.order.repository.OrderItemRepository;
import com.matessa.order.repository.OrderRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderManagementApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("orders");

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("outbox.poll-interval-ms", () -> "3600000");   // keep the poller idle during the test

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        signingKey = (RSAPrivateKey) keyPair.getPrivate();
        Path dir = Files.createTempDirectory("order-test-keys");
        Path publicKey = dir.resolve("public.pem");
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        publicKey.toFile().deleteOnExit();
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    private RestClient client() {
        return RestClient.builder()
                .defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
    }

    private String token(String role, String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("matessa-user-service").subject(subject).claim("tenant", "matessa")
                .claim("roles", List.of(role))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private Long seedOrder(long userId, OrderStatus status, PaymentStatus payment, ShippingStatus shipping) {
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNumber("AD-T-" + UUID.randomUUID().toString().substring(0, 8));
        order.setStatus(status);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setDiscountAmount(new BigDecimal("0.00"));
        order.setTotal(new BigDecimal("100.00"));
        order.setShippingAddressSnapshot("{\"recipientName\":\"Test\"}");
        order.setPaymentStatus(payment);
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setShippingStatus(shipping);
        Order saved = orderRepository.save(order);
        OrderItem item = new OrderItem();
        item.setOrderId(saved.getId());
        item.setProductVariantId(10L);
        item.setProductNameSnapshot("Item");
        item.setSkuSnapshot("SKU-10");
        item.setUnitPriceSnapshot(new BigDecimal("100.00"));
        item.setQuantity(1);
        orderItemRepository.save(item);
        return saved.getId();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    @Test
    void orders_ownership_cancel_staffLifecycle_history() throws Exception {
        String alice = token("CUSTOMER", "100");
        String bob = token("CUSTOMER", "200");
        String manager = token("MANAGER", "1");

        Long aPaid = seedOrder(100, OrderStatus.PAID, PaymentStatus.COMPLETED, ShippingStatus.PENDING);
        Long aShipped = seedOrder(100, OrderStatus.SHIPPED, PaymentStatus.COMPLETED, ShippingStatus.SHIPPED);
        Long bPending = seedOrder(200, OrderStatus.PENDING, PaymentStatus.PENDING, ShippingStatus.PENDING);
        Long dPaid = seedOrder(200, OrderStatus.PAID, PaymentStatus.COMPLETED, ShippingStatus.PENDING);

        // Alice sees only her two orders
        Map<String, Object> mine = client().get().uri("/api/orders").header("Authorization", "Bearer " + alice)
                .retrieve().toEntity(MAP).getBody();
        assertThat(content(mine)).hasSize(2);

        // ownership: her own -> 200, someone else's -> 404
        assertThat(client().get().uri("/api/orders/" + aPaid).header("Authorization", "Bearer " + alice)
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(client().get().uri("/api/orders/" + bPending).header("Authorization", "Bearer " + alice)
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // cancel a PAID order -> CANCELLED + refunded
        ResponseEntity<Map<String, Object>> cancelled = client().post().uri("/api/orders/" + aPaid + "/cancel")
                .header("Authorization", "Bearer " + alice).retrieve().toEntity(MAP);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(cancelled.getBody().get("paymentStatus")).isEqualTo("REFUNDED");

        // cannot cancel a shipped order
        assertThat(client().post().uri("/api/orders/" + aShipped + "/cancel").header("Authorization", "Bearer " + alice)
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // staff sees all; customers can't reach the staff list
        Map<String, Object> all = client().get().uri("/api/orders/admin").header("Authorization", "Bearer " + manager)
                .retrieve().toEntity(MAP).getBody();
        assertThat(content(all)).hasSize(4);
        assertThat(client().get().uri("/api/orders/admin").header("Authorization", "Bearer " + alice)
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // staff advances a PAID order to SHIPPED (drives shippingStatus too)
        ResponseEntity<Map<String, Object>> shipped = client().put().uri("/api/orders/" + dPaid + "/status")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", "SHIPPED", "note", "Handed to courier")).retrieve().toEntity(MAP);
        assertThat(shipped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shipped.getBody().get("status")).isEqualTo("SHIPPED");
        assertThat(shipped.getBody().get("shippingStatus")).isEqualTo("SHIPPED");

        // illegal transition (PENDING -> DELIVERED) rejected
        assertThat(client().put().uri("/api/orders/" + bPending + "/status").header("Authorization", "Bearer " + manager)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("status", "DELIVERED"))
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // staff sets shipping carrier/tracking
        ResponseEntity<Map<String, Object>> shipping = client().put().uri("/api/orders/" + dPaid + "/shipping")
                .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("shippingMethod", "UPS Ground", "trackingNumber", "1Z999")).retrieve().toEntity(MAP);
        assertThat(shipping.getBody().get("shippingMethod")).isEqualTo("UPS Ground");
        assertThat(shipping.getBody().get("trackingNumber")).isEqualTo("1Z999");

        // a customer cannot drive status
        assertThat(client().put().uri("/api/orders/" + dPaid + "/status").header("Authorization", "Bearer " + bob)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("status", "DELIVERED"))
                .retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // history records the cancellation transition
        List<Map<String, Object>> history = client().get().uri("/api/orders/" + aPaid + "/history")
                .header("Authorization", "Bearer " + alice)
                .retrieve().toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() { }).getBody();
        assertThat(history).isNotEmpty();
        assertThat(history.get(history.size() - 1).get("toStatus")).isEqualTo("CANCELLED");
    }
}
