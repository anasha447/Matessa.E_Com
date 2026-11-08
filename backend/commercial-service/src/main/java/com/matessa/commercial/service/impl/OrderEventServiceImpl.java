package com.matessa.commercial.service.impl;

import com.matessa.commercial.entity.OutboxEvent;
import com.matessa.commercial.entity.ProcessedEvent;
import com.matessa.commercial.entity.ProductVariant;
import com.matessa.commercial.messaging.KafkaTopics;
import com.matessa.commercial.messaging.OrderCancelledEvent;
import com.matessa.commercial.messaging.OrderPlacedEvent;
import com.matessa.commercial.messaging.OrderRejectedEvent;
import com.matessa.commercial.repository.CouponRepository;
import com.matessa.commercial.repository.OutboxRepository;
import com.matessa.commercial.repository.ProcessedEventRepository;
import com.matessa.commercial.repository.ProductVariantRepository;
import com.matessa.commercial.service.OrderEventService;
import com.matessa.commercial.config.CacheConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderEventServiceImpl implements OrderEventService {

    private static final Logger log = LoggerFactory.getLogger(OrderEventServiceImpl.class);

    private final ProductVariantRepository variantRepository;
    private final CouponRepository couponRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderEventServiceImpl(ProductVariantRepository variantRepository,
                                 CouponRepository couponRepository,
                                 ProcessedEventRepository processedEventRepository,
                                 OutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.variantRepository = variantRepository;
        this.couponRepository = couponRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Settles an order whose stock and coupon use were already taken synchronously, during
     * checkout, by {@link ReservationServiceImpl}.
     *
     * <p>This handler used to be where stock was decremented. That made it the second half of
     * a read-then-write spanning two services and a Kafka hop, with the payment taken in the
     * middle: every concurrent buyer of the last unit passed the stock read in order-service,
     * every one of them was charged, and this handler then rejected all but one — so customers
     * were charged for orders that were immediately cancelled. (The old comment claiming
     * {@code @Version} prevented that was wrong: optimistic locking only stops a lost update
     * between two writers here, long after the money moved.)
     *
     * <p>Stock is now held before payment, so by the time this event arrives there is nothing
     * left to decide. What remains is genuinely asynchronous: evict the display cache so stock
     * badges refresh, and record the event as processed for at-least-once de-duplication. The
     * compensating direction still does real work — see {@link #handleOrderCancelled}.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;   // already handled (at-least-once delivery)
        }
        markProcessed(event.eventId());
        log.info("OrderPlaced {}: confirmed ({} line(s) already reserved at checkout)",
                event.orderNumber(), event.items().size());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        for (OrderCancelledEvent.Line line : event.items()) {
            // Atomic increment rather than read-modify-write: two cancellations landing at once
            // would otherwise each read the same starting figure and one increment would be lost.
            if (variantRepository.releaseStock(line.productVariantId(), line.quantity()) == 0) {
                log.warn("OrderCancelled {}: variant {} no longer exists, stock not restored",
                        event.orderNumber(), line.productVariantId());
            }
        }
        releaseCoupon(event.couponCode());
        markProcessed(event.eventId());
        log.info("OrderCancelled {}: restored stock", event.orderNumber());
    }

    /**
     * Gives the coupon use back. Atomic, for the same reason as the stock increment above:
     * the previous read-then-write pair could lose a decrement under concurrency, and it also
     * had no floor beyond a read-time check, so usedCount could drift.
     */
    private void releaseCoupon(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        couponRepository.returnUse(code.trim());
    }

    private void enqueue(String eventType, String aggregateId, Object payload) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.setAggregateType("Order");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outboxRepository.save(outbox);
    }

    private void markProcessed(String eventId) {
        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize event payload", ex);
        }
    }
}
