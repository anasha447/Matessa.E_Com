package com.matessa.commercial;

import com.matessa.commercial.entity.Category;
import com.matessa.commercial.entity.Coupon;
import com.matessa.commercial.entity.DiscountType;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.entity.StockReservation;
import com.matessa.commercial.messaging.ReservationExpiryJob;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.repository.StockReservationRepository;
import com.matessa.commercial.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** Checkout reservations: idempotent reserve/release, expiry, per-customer coupon limit, service key. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commercial");

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("reservation.expiry-initial-delay-ms", () -> "3600000"); // driven by the test
        var keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        Path publicKey = Files.createTempDirectory("k").resolve("public.pem");
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n");
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Value("${local.server.port}")
    int port;

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired CouponRepository coupons;
    @Autowired StockReservationRepository reservations;
    @Autowired ReservationExpiryJob expiryJob;
    @Autowired TransactionTemplate transactions;

    @BeforeEach
    void bind() {
        TenantContext.setTenantId("matessa");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    private RestClient internal(String key) {
        RestClient.Builder b = RestClient.builder().defaultHeader("X-Tenant-ID", "matessa").baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { });
        if (key != null) {
            b.defaultHeader("X-Internal-Key", key);
        }
        return b.build();
    }

    private long variantWithStock(int stock) {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Category c = new Category();
        c.setName("C" + s);
        c.setSlug("c-" + s);
        c.setDisplayOrder(0);
        Long categoryId = categories.save(c).getId();
        Product p = new Product();
        p.setCategoryId(categoryId);
        p.setName("P" + s);
        p.setSlug("p-" + s);
        p.setBasePrice(new BigDecimal("100.00"));
        p.setActive(true);
        Long productId = products.save(p).getId();
        ProductVariant v = new ProductVariant();
        v.setProductId(productId);
        v.setSku("SKU-" + s);
        v.setStockQuantity(stock);
        v.setActive(true);
        return variants.save(v).getId();
    }

    private String coupon(Integer perUserLimit) {
        Coupon c = new Coupon();
        c.setCode("C" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setDiscountType(DiscountType.FIXED_AMOUNT);
        c.setDiscountValue(new BigDecimal("10"));
        c.setUsedCount(0);
        c.setPerUserLimit(perUserLimit);
        c.setValidFrom(Instant.now().minusSeconds(60));
        c.setValidUntil(Instant.now().plusSeconds(3600));
        c.setActive(true);
        return coupons.save(c).getCode();
    }

    private Map<String, Object> request(String id, long variantId, int qty, String coupon, String customer) {
        Map<String, Object> body = new HashMap<>();
        body.put("reservationId", id);
        body.put("items", List.of(Map.of("productVariantId", variantId, "quantity", qty)));
        body.put("couponCode", coupon);
        body.put("customerKey", customer);
        return body;
    }

    private ResponseEntity<Map<String, Object>> reserve(Map<String, Object> body) {
        return internal("test-internal-key").post().uri("/internal/reservations").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(MAP);
    }

    private int stock(long variantId) {
        return variants.findById(variantId).orElseThrow().getStockQuantity();
    }

    @Test
    void internalEndpoints_needTheServiceKey() {
        long v = variantWithStock(5);
        Map<String, Object> body = request(UUID.randomUUID().toString(), v, 1, null, null);
        assertThat(internal(null).post().uri("/internal/reservations").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(internal("wrong").post().uri("/internal/reservations").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(stock(v)).isEqualTo(5);
    }

    @Test
    void reserveIsIdempotent_andReleaseGivesStockBackExactlyOnce() {
        long v = variantWithStock(5);
        String id = UUID.randomUUID().toString();
        assertThat(reserve(request(id, v, 2, null, "u:1")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reserve(request(id, v, 2, null, "u:1")).getStatusCode()).isEqualTo(HttpStatus.OK); // retry
        assertThat(stock(v)).isEqualTo(3); // taken once

        for (int i = 0; i < 2; i++) {
            assertThat(internal("test-internal-key").post().uri("/internal/reservations/" + id + "/release")
                    .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        assertThat(stock(v)).isEqualTo(5); // given back once
        // Releasing an id that was never reserved (reserve timed out before commit) is a no-op.
        assertThat(internal("test-internal-key").post().uri("/internal/reservations/" + UUID.randomUUID() + "/release")
                .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void expiredHeldReservation_isReturnedByTheExpiryJob_confirmedOneIsNot() {
        long v = variantWithStock(10);
        String abandoned = UUID.randomUUID().toString();
        String confirmed = UUID.randomUUID().toString();
        reserve(request(abandoned, v, 3, null, null));
        reserve(request(confirmed, v, 2, null, null));
        assertThat(stock(v)).isEqualTo(5);

        transactions.executeWithoutResult(tx -> {
            for (String id : List.of(abandoned, confirmed)) {
                StockReservation r = reservations.findById(id).orElseThrow();
                r.setExpiresAt(Instant.now().minusSeconds(1));
                if (id.equals(confirmed)) {
                    r.setStatus(StockReservation.Status.CONFIRMED); // its order arrived
                }
            }
        });
        assertThat(expiryJob.releaseExpiredIn("matessa")).isEqualTo(1);
        assertThat(stock(v)).isEqualTo(8);
        assertThat(reservations.findById(abandoned).orElseThrow().getStatus()).isEqualTo(StockReservation.Status.RELEASED);
    }

    @Test
    void perCustomerCouponLimit_holdsUnderConcurrentCheckouts() throws Exception {
        long v = variantWithStock(10);
        String code = coupon(1);
        CompletableFuture<ResponseEntity<Map<String, Object>>> a = CompletableFuture.supplyAsync(() ->
                reserve(request(UUID.randomUUID().toString(), v, 1, code, "e:same@example.com")));
        CompletableFuture<ResponseEntity<Map<String, Object>>> b = CompletableFuture.supplyAsync(() ->
                reserve(request(UUID.randomUUID().toString(), v, 1, code, "e:same@example.com")));
        List<HttpStatus> statuses = List.of((HttpStatus) a.get().getStatusCode(), (HttpStatus) b.get().getStatusCode());
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        // A different customer can still use it.
        assertThat(reserve(request(UUID.randomUUID().toString(), v, 1, code, "e:other@example.com")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
