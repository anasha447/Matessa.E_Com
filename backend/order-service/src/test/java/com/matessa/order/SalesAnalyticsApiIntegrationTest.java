package com.matessa.order;

import com.matessa.order.entity.Order;
import com.matessa.order.entity.OrderItem;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.OrderStatusHistory;
import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.entity.ShippingStatus;
import com.matessa.order.repository.OrderItemRepository;
import com.matessa.order.repository.OrderRepository;
import com.matessa.order.repository.OrderStatusHistoryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SalesAnalyticsApiIntegrationTest {

    // Repositories are called directly from the test thread, which (unlike a request) has no
    // store in context. Data access without one fails by design, so bind the test thread.
    @org.junit.jupiter.api.BeforeEach
    void bindStore() {
        com.matessa.order.tenant.TenantContext.setTenantId("matessa");
    }

    @org.junit.jupiter.api.AfterEach
    void unbindStore() {
        com.matessa.order.tenant.TenantContext.clear();
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("orders");

    private static RSAPrivateKey signingKey;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
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

    @Value("${local.server.port}")
    int port;

    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired OrderStatusHistoryRepository statusHistoryRepository;
    @Autowired com.matessa.order.analytics.AnalyticsViewRefresher viewRefresher;

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

    private Order order(long userId, OrderStatus status, PaymentStatus pay, PaymentMethod method,
                        String total, String discount, String coupon) {
        Order o = new Order();
        o.setUserId(userId);
        o.setOrderNumber("AD-A-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        o.setStatus(status);
        o.setSubtotal(new BigDecimal(total));
        o.setDiscountAmount(new BigDecimal(discount));
        o.setTotal(new BigDecimal(total));
        o.setCouponCode(coupon);
        o.setShippingAddressSnapshot("{}");
        o.setPaymentStatus(pay);
        o.setPaymentMethod(method);
        o.setShippingStatus(ShippingStatus.PENDING);
        return orderRepository.save(o);
    }

    private void item(Long orderId, long variantId, int qty, String unitPrice) {
        OrderItem i = new OrderItem();
        i.setOrderId(orderId);
        i.setProductVariantId(variantId);
        i.setProductNameSnapshot("Item " + variantId);
        i.setSkuSnapshot("SKU-" + variantId);
        i.setUnitPriceSnapshot(new BigDecimal(unitPrice));
        i.setQuantity(qty);
        orderItemRepository.save(i);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String k) {
        return (Map<String, Object>) m.get(k);
    }

    private static double num(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).doubleValue();
    }

    @Test
    void salesAnalytics_kpis_andRoleGate() throws Exception {
        Order a = order(100, OrderStatus.DELIVERED, PaymentStatus.COMPLETED, PaymentMethod.CARD, "200.00", "20.00", "SAVE20");
        item(a.getId(), 10, 2, "100.00");
        Order b = order(100, OrderStatus.SHIPPED, PaymentStatus.COMPLETED, PaymentMethod.CARD, "100.00", "0.00", null);
        item(b.getId(), 10, 1, "100.00");
        Order c = order(200, OrderStatus.PAID, PaymentStatus.COMPLETED, PaymentMethod.PAYPAL, "50.00", "0.00", null);
        item(c.getId(), 20, 1, "50.00");
        order(200, OrderStatus.PENDING, PaymentStatus.PENDING, PaymentMethod.COD, "80.00", "0.00", null);
        order(300, OrderStatus.CANCELLED, PaymentStatus.REFUNDED, PaymentMethod.CARD, "120.00", "0.00", null);

        OrderStatusHistory shipped = new OrderStatusHistory();
        shipped.setOrderId(b.getId());
        shipped.setFromStatus(OrderStatus.PAID);
        shipped.setToStatus(OrderStatus.SHIPPED);
        statusHistoryRepository.save(shipped);

        String manager = token("MANAGER", "1");
        // The top-products / trend read model is a materialized view refreshed on a schedule;
        // refresh it now rather than depend on the 30 s timer having fired.
        viewRefresher.refresh();

        ResponseEntity<Map<String, Object>> res = client().get().uri("/api/analytics/sales")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(MAP);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = res.getBody();

        // revenue = paid orders only: 200 + 100 + 50 = 350
        assertThat(num(sub(body, "revenue"), "allTime")).isEqualTo(350.00);
        assertThat(num(sub(body, "revenue"), "thisMonth")).isEqualTo(350.00);
        assertThat(num(sub(body, "revenue"), "today")).isEqualTo(350.00);

        assertThat(num(body, "totalOrders")).isEqualTo(5.0);
        assertThat(((Number) sub(body, "ordersByStatus").get("DELIVERED")).longValue()).isEqualTo(1);
        assertThat(((Number) sub(body, "ordersByStatus").get("PENDING")).longValue()).isEqualTo(1);

        assertThat(num(body, "averageOrderValue")).isEqualTo(116.67);   // 350 / 3 paid
        assertThat(num(body, "totalDiscountsGiven")).isEqualTo(20.00);
        assertThat(num(body, "couponRedemptions")).isEqualTo(1.0);
        assertThat(num(sub(body, "refunds"), "count")).isEqualTo(1.0);
        assertThat(num(sub(body, "refunds"), "amount")).isEqualTo(120.00);

        // operations: 1 awaiting payment (PENDING), 1 awaiting shipment (PAID), a ship time exists
        assertThat(num(sub(body, "operations"), "awaitingPayment")).isEqualTo(1.0);
        assertThat(num(sub(body, "operations"), "awaitingShipment")).isEqualTo(1.0);
        assertThat(sub(body, "operations").get("avgHoursToShip")).isNotNull();

        // revenue by payment method present
        assertThat((List<?>) body.get("revenueByPaymentMethod")).isNotEmpty();

        // top products: variant 10 leads (3 units, 300 revenue)
        List<Map<String, Object>> top = client().get().uri("/api/analytics/sales/top-products?limit=5")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(LIST).getBody();
        assertThat(top).isNotEmpty();
        assertThat(((Number) top.get(0).get("productVariantId")).longValue()).isEqualTo(10L);
        assertThat(num(top.get(0), "unitsSold")).isEqualTo(3.0);
        assertThat(num(top.get(0), "revenue")).isEqualTo(300.00);

        // revenue trend includes the current month
        List<Map<String, Object>> trend = client().get().uri("/api/analytics/sales/revenue-trend?months=3")
                .header("Authorization", "Bearer " + manager).retrieve().toEntity(LIST).getBody();
        assertThat(trend).isNotEmpty();

        // customers cannot see store analytics
        ResponseEntity<String> forbidden = client().get().uri("/api/analytics/sales")
                .header("Authorization", "Bearer " + token("CUSTOMER", "100")).retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // anonymous -> 401
        assertThat(client().get().uri("/api/analytics/sales").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
