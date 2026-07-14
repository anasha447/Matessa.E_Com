package com.matessa.order.messaging;

import com.matessa.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes OrderRejected from commercial-service and cancels the order (saga
 * compensation). Idempotent via processed_event in the service.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_REJECTED, groupId = "order-service")
    public void onOrderRejected(String payload) {
        OrderRejectedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderRejectedEvent.class);
        } catch (Exception ex) {
            log.error("Skipping malformed OrderRejected payload: {}", ex.toString());
            return;
        }
        orderService.handleRejection(event);
    }
}
