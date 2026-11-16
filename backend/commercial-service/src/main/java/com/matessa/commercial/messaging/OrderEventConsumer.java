package com.matessa.commercial.messaging;

import com.matessa.commercial.service.OrderEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order events and applies inventory side-effects. Deserialization failures are
 * logged and skipped (poison messages); processing errors propagate so Kafka re-delivers
 * (the service is idempotent via processed_event).
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderEventService orderEventService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(OrderEventService orderEventService, ObjectMapper objectMapper) {
        this.orderEventService = orderEventService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_PLACED, groupId = "commercial-service")
    public void onOrderPlaced(String payload) {
        com.matessa.commercial.tenant.KafkaTenancy.requireStore(KafkaTopics.ORDER_PLACED);
        orderEventService.handleOrderPlaced(parse(payload, OrderPlacedEvent.class));
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "commercial-service")
    public void onOrderCancelled(String payload) {
        com.matessa.commercial.tenant.KafkaTenancy.requireStore(KafkaTopics.ORDER_CANCELLED);
        orderEventService.handleOrderCancelled(parse(payload, OrderCancelledEvent.class));
    }

    private <T> T parse(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            // Non-retryable (see KafkaErrorHandlerConfig): a malformed payload goes straight to
            // <topic>.DLT instead of being silently dropped or re-delivered forever.
            throw new IllegalArgumentException("Malformed " + type.getSimpleName() + " payload", ex);
        }
    }
}
