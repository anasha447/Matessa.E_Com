package com.matessa.commercial;

import com.matessa.commercial.entity.Category;
import com.matessa.commercial.entity.Coupon;
import com.matessa.commercial.entity.DiscountType;
import com.matessa.commercial.entity.Product;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.repository.ProductRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
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

@SpringBootTest
@Testcontainers
class OrderEventConsumerIntegrationTest {

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

    @Test
    void orderPlaced_decrementsStock_idempotently_redeemsCoupon_andRejectsShortfall() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
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
        Long productId = productRepository.save(product).getId();

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

        // OrderPlaced -> decrement 3, redeem coupon
        kafkaTemplate.send("OrderPlaced", orderPlaced("evt-1", "AD-1", variantId, 3, coupon.getCode()));
        assertThat(awaitStock(variantId, 7, Duration.ofSeconds(30))).isEqualTo(7);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getUsedCount()).isEqualTo(1);

        // duplicate eventId -> ignored (idempotent), stock stays 7
        kafkaTemplate.send("OrderPlaced", orderPlaced("evt-1", "AD-1", variantId, 3, coupon.getCode()));
        Thread.sleep(2500);
        assertThat(variantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(7);

        // OrderCancelled -> restore stock + release coupon
        kafkaTemplate.send("OrderCancelled", orderCancelled("evt-2", "AD-1", variantId, 3, coupon.getCode()));
        assertThat(awaitStock(variantId, 10, Duration.ofSeconds(30))).isEqualTo(10);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getUsedCount()).isEqualTo(0);

        // OrderPlaced for more than is in stock -> rejected (stock untouched, OrderRejected emitted)
        kafkaTemplate.send("OrderPlaced", orderPlaced("evt-3", "AD-2", variantId, 100, null));
        String rejected = awaitRejected("AD-2", Duration.ofSeconds(30));
        assertThat(rejected).isNotNull();
        assertThat(rejected).contains("AD-2").contains("Insufficient stock");
        assertThat(variantRepository.findById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
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

    private String awaitRejected(String orderNumber, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("OrderRejected"));
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
}
