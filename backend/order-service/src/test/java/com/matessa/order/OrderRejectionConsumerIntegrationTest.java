package com.matessa.order;

import com.matessa.order.entity.Order;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.entity.ShippingStatus;
import com.matessa.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderRejectionConsumerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("orders");

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
        registry.add("spring.kafka.listener.auto-startup", () -> "true");   // turn the rejection consumer on

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
        Path dir = Files.createTempDirectory("order-test-keys");
        Path publicKey = dir.resolve("public.pem");
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keyPair.getPublic().getEncoded());
        Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
        publicKey.toFile().deleteOnExit();
        registry.add("jwt.public-key-path", () -> "file:" + publicKey.toAbsolutePath().toString().replace('\\', '/'));
    }

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;


    /** Every event carries its store, as the outbox pollers stamp it in production. */

    private static org.apache.kafka.clients.producer.ProducerRecord<String, String> storeRecord(String topic, String value) {

        var record = new org.apache.kafka.clients.producer.ProducerRecord<String, String>(topic, value);

        record.headers().add("X-Tenant-ID", "matessa".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return record;

    }

    @Test
    void orderRejected_cancelsTheOrder() throws Exception {
        String orderNumber = "AD-REJ-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = new Order();
        order.setUserId(100L);
        order.setOrderNumber(orderNumber);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setDiscountAmount(new BigDecimal("0.00"));
        order.setTotal(new BigDecimal("100.00"));
        order.setShippingAddressSnapshot("{\"recipientName\":\"Test\"}");
        order.setPaymentStatus(PaymentStatus.COMPLETED);
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setShippingStatus(ShippingStatus.PENDING);
        orderRepository.save(order);

        String payload = "{\"eventId\":\"rej-1\",\"orderNumber\":\"" + orderNumber
                + "\",\"reason\":\"Insufficient stock\",\"occurredAt\":\"" + Instant.now() + "\"}";
        kafkaTemplate.send(storeRecord("OrderRejected", payload));

        Order cancelled = awaitStatus(orderNumber, OrderStatus.CANCELLED, Duration.ofSeconds(30));
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    private Order awaitStatus(String orderNumber, OrderStatus expected, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);
            if (order != null && order.getStatus() == expected) {
                return order;
            }
            Thread.sleep(300);
        }
        return orderRepository.findByOrderNumber(orderNumber).orElse(null);
    }
}
