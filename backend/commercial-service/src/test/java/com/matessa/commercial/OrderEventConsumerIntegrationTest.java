package com.matessa.commercial;

import com.matessa.commercial.dto.request.StockReservationRequest;
import com.matessa.commercial.entity.Category;
import com.matessa.commercial.entity.Coupon;
import com.matessa.commercial.entity.DiscountType;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.service.ReservationService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class OrderEventConsumerIntegrationTest {

    // Repositories are called directly from the test thread, which (unlike a request) has no
    // store in context. Data access without one fails by design, so bind the test thread.
    @org.junit.jupiter.api.BeforeEach
    void bindStore() {
        com.matessa.commercial.tenant.TenantContext.setTenantId("matessa");
    }

    @org.junit.jupiter.api.AfterEach
    void unbindStore() {
        com.matessa.commercial.tenant.TenantContext.clear();
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commercial");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> "true");   // turn the consumer on
        registry.add("outbox.poll-interval-ms", () -> "500");

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        Path dir = Files.createTempDirectory("commercial-test-keys");
        Path publicKey = dir.resolve("public.pem");
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        publicKey.toFile().deleteOnExit();
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    /** Every event carries its store, as the outbox pollers stamp it in production. */
    private static org.apache.kafka.clients.producer.ProducerRecord<String, String> storeRecord(String topic, String value) {
        var record = new org.apache.kafka.clients.producer.ProducerRecord<String, String>(topic, value);
        record.headers().add("X-Tenant-ID", "matessa".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return record;
    }
    @Autowired ReservationService reservationService;
    @Autowired TransactionTemplate transactions;

    @Test
    void orderPlaced_confirmsWithoutDecrementingAgain_andCancelReleases() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long productId = saveProduct(suffix);

        ProductVariant variant = new ProductVariant();
        variant.setProductId(productId);
        variant.setSku("SKU-" + suffix);
        variant.setStockQuantity(10);
        variant.setActive(true);
        Long variantId = variantRepository.save(variant).getId();

        Coupon coupon = new Coupon();
        coupon.setCode("REDEEM-" + suffix);
        coupon.setDiscountType(DiscountType.PERCENTAGE);
        coupon.setDiscountValue(new BigDecimal("10"));
        coupon.setUsedCount(0);
        coupon.setValidFrom(Instant.now().minusSeconds(60));
        coupon.setValidUntil(Instant.now().plusSeconds(86400));
        coupon.setActive(true);
        couponRepository.save(coupon);

        // Simulate the checkout that preceded this event: stock and the coupon use are taken
        // synchronously now, by ReservationService, before the payment is charged. By the time
        // OrderPlaced is consumed there is nothing left to decide.
        // Modifying queries need a transaction, exactly as they have inside ReservationService.
        transactions.executeWithoutResult(tx -> {
            variantRepository.reserveStock(variantId, 3);
            couponRepository.claimUse(coupon.getCode());
        });
        assertThat(variantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(7);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getUsedCount()).isEqualTo(1);

        // OrderPlaced is now a confirmation: it must NOT decrement again. This is the
        // regression that matters - while this handler also decremented, the stock for one
        // order could be taken twice.
        kafkaTemplate.send(storeRecord("OrderPlaced", orderPlaced("evt-1", "AD-1", variantId, 3, coupon.getCode())));
        Thread.sleep(2500);
        assertThat(variantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(7);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getUsedCount()).isEqualTo(1);

        // duplicate eventId -> ignored (idempotent)
        kafkaTemplate.send(storeRecord("OrderPlaced", orderPlaced("evt-1", "AD-1", variantId, 3, coupon.getCode())));
        Thread.sleep(2500);
        assertThat(variantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(7);

        // OrderCancelled still does real work: it puts the reservation back.
        kafkaTemplate.send(storeRecord("OrderCancelled", orderCancelled("evt-2", "AD-1", variantId, 3, coupon.getCode())));
        assertThat(awaitStock(variantId, 10, Duration.ofSeconds(30))).isEqualTo(10);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getUsedCount()).isEqualTo(0);
    }

    @Test
    void reserve_isAtomic_andRefusesWhatIsNotThere() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long productId = saveProduct(suffix);
        ProductVariant variant = new ProductVariant();
        variant.setProductId(productId);
        variant.setSku("RES-" + suffix);
        variant.setStockQuantity(1);
        variant.setActive(true);
        Long variantId = variantRepository.save(variant).getId();

        // Two lines of the same variant used to be checked independently against the full
        // stock figure, so 2 x qty-1 both passed against a stock of 1 and the order was only
        // rejected after the card had been charged. They are merged before checking now.
        assertThatThrownBy(() -> reservationService.reserve(req(
                List.of(new StockReservationRequest.Line(variantId, 1),
                        new StockReservationRequest.Line(variantId, 1)),
                null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(variantRepository.findById(variantId).orElseThrow().getStockQuantity())
                .as("a refused reservation must leave the shelf untouched")
                .isEqualTo(1);

        // The one unit can still be reserved, exactly once.
        assertThat(reservationService.reserve(req(
                List.of(new StockReservationRequest.Line(variantId, 1)), null, null)).lines())
                .hasSize(1);
        assertThat(variantRepository.findById(variantId).orElseThrow().getStockQuantity()).isZero();

        assertThatThrownBy(() -> reservationService.reserve(req(
                List.of(new StockReservationRequest.Line(variantId, 1)), null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reserve_enforcesSingleUseCouponAndPerCustomerLimit() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long productId = saveProduct(suffix);
        ProductVariant variant = new ProductVariant();
        variant.setProductId(productId);
        variant.setSku("CPN-" + suffix);
        variant.setStockQuantity(50);
        variant.setActive(true);
        Long variantId = variantRepository.save(variant).getId();

        Coupon single = new Coupon();
        single.setCode("ONCE-" + suffix);
        single.setDiscountType(DiscountType.PERCENTAGE);
        single.setDiscountValue(new BigDecimal("10"));
        single.setMaxUses(1);
        single.setPerUserLimit(1);
        single.setUsedCount(0);
        single.setValidFrom(Instant.now().minusSeconds(60));
        single.setValidUntil(Instant.now().plusSeconds(86400));
        single.setActive(true);
        couponRepository.save(single);

        StockReservationRequest.Line line = new StockReservationRequest.Line(variantId, 1);

        // First use succeeds and consumes the single available use.
        assertThat(reservationService.reserve(
                req(List.of(line), single.getCode(), 0)).couponCode())
                .isEqualTo(single.getCode());

        // maxUses is now spent. This used to pass: the limit was checked while validating and
        // then incremented unconditionally at redemption, in a later transaction, so two
        // checkouts racing through validation both got the discount.
        assertThatThrownBy(() -> reservationService.reserve(
                req(List.of(line), single.getCode(), 0)))
                .isInstanceOf(ConflictException.class);
        assertThat(couponRepository.findByCode(single.getCode()).orElseThrow().getUsedCount())
                .as("a refused claim must not inflate usedCount")
                .isEqualTo(1);

        // perUserLimit: stored and returned to clients, but previously never checked anywhere.
        Coupon reusable = new Coupon();
        reusable.setCode("TWICE-" + suffix);
        reusable.setDiscountType(DiscountType.PERCENTAGE);
        reusable.setDiscountValue(new BigDecimal("10"));
        reusable.setPerUserLimit(2);
        reusable.setUsedCount(0);
        reusable.setValidFrom(Instant.now().minusSeconds(60));
        reusable.setValidUntil(Instant.now().plusSeconds(86400));
        reusable.setActive(true);
        couponRepository.save(reusable);

        assertThatThrownBy(() -> reservationService.reserve(
                req(List.of(line), reusable.getCode(), 2)))
                .as("a customer at their per-customer limit is refused")
                .isInstanceOf(ConflictException.class);
        assertThat(reservationService.reserve(
                req(List.of(line), reusable.getCode(), 1)).couponCode())
                .as("one use left is still allowed")
                .isEqualTo(reusable.getCode());
        // Guests carry no history, so the per-customer limit does not apply to them.
        assertThat(reservationService.reserve(
                req(List.of(line), reusable.getCode(), null)).couponCode())
                .isEqualTo(reusable.getCode());
    }

    /** Old-style call sites: a fresh reservation id; a prior-use count means a known customer. */
    private static StockReservationRequest req(List<StockReservationRequest.Line> items, String coupon, Integer priorUses) {
        return new StockReservationRequest(UUID.randomUUID().toString(), items, coupon,
                priorUses == null ? null : "u:" + UUID.randomUUID(), priorUses);
    }

    private String orderPlaced(String eventId, String orderNumber, Long variantId, int qty, String coupon) {
        String couponField = coupon == null ? "null" : "\"" + coupon + "\"";
        return "{\"eventId\":\"" + eventId + "\",\"orderNumber\":\"" + orderNumber + "\",\"userId\":100,"
                + "\"couponCode\":" + couponField + ",\"items\":[{\"productVariantId\":" + variantId
                + ",\"quantity\":" + qty + "}],\"occurredAt\":\"" + Instant.now() + "\"}";
    }

    private String orderCancelled(String eventId, String orderNumber, Long variantId, int qty, String coupon) {
        String couponField = coupon == null ? "null" : "\"" + coupon + "\"";
        return "{\"eventId\":\"" + eventId + "\",\"orderNumber\":\"" + orderNumber + "\",\"couponCode\":" + couponField
                + ",\"items\":[{\"productVariantId\":" + variantId + ",\"quantity\":" + qty + "}],"
                + "\"occurredAt\":\"" + Instant.now() + "\"}";
    }

    private int awaitStock(Long variantId, int expected, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int stock = -1;
        while (System.currentTimeMillis() < deadline) {
            stock = variantRepository.findById(variantId).map(ProductVariant::getStockQuantity).orElse(-1);
            if (stock == expected) {
                return stock;
            }
            Thread.sleep(300);
        }
        return stock;
    }


    /** An active product under an active category, priced at 100.00. */
    private Long saveProduct(String suffix) {
        Category category = new Category();
        category.setName("Helmets");
        category.setSlug("helmets-" + suffix);
        category.setActive(true);
        Long categoryId = categoryRepository.save(category).getId();

        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setName("Trail Helmet");
        product.setSlug("trail-helmet-" + suffix);
        product.setBasePrice(new BigDecimal("100.00"));
        product.setActive(true);
        return productRepository.save(product).getId();
    }
}
