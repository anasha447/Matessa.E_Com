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

    // Stock changed → drop the (display) variant-list cache so PDP badges refresh. This is the
    // in-service eviction the isolation rule requires: commercial evicts its own cache in reaction
    // to the order.placed event it already consumes — order-service never touches this cache.
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;   // already handled (at-least-once delivery)
        }
        List<ProductVariant> toDecrement = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        for (OrderPlacedEvent.Line line : event.items()) {
            ProductVariant variant = variantRepository.findById(line.productVariantId()).orElse(null);
            if (variant == null || !variant.isActive() || variant.getStockQuantity() < line.quantity()) {
                shortages.add("variant " + line.productVariantId() + " (need " + line.quantity() + ")");
            } else {
                variant.setStockQuantity(variant.getStockQuantity() - line.quantity());
                toDecrement.add(variant);
            }
        }

        if (shortages.isEmpty()) {
            variantRepository.saveAll(toDecrement);   // @Version guards against concurrent oversell
            redeemCoupon(event.couponCode());
            log.info("OrderPlaced {}: decremented {} line(s)", event.orderNumber(), toDecrement.size());
        } else {
            String reason = "Insufficient stock for " + String.join(", ", shortages);
            enqueue(KafkaTopics.ORDER_REJECTED, event.orderNumber(),
                    new OrderRejectedEvent(UUID.randomUUID().toString(), event.orderNumber(), reason, Instant.now()));
            log.warn("OrderPlaced {} rejected: {}", event.orderNumber(), reason);
        }
        markProcessed(event.eventId());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.VARIANT_LIST, allEntries = true)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }
        for (OrderCancelledEvent.Line line : event.items()) {
            variantRepository.findById(line.productVariantId()).ifPresent(variant -> {
                variant.setStockQuantity(variant.getStockQuantity() + line.quantity());
                variantRepository.save(variant);
            });
        }
        releaseCoupon(event.couponCode());
        markProcessed(event.eventId());
        log.info("OrderCancelled {}: restored stock", event.orderNumber());
    }

    private void redeemCoupon(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        couponRepository.findByCode(code).ifPresent(coupon -> {
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
        });
    }

    private void releaseCoupon(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        couponRepository.findByCode(code).ifPresent(coupon -> {
            if (coupon.getUsedCount() > 0) {
                coupon.setUsedCount(coupon.getUsedCount() - 1);
                couponRepository.save(coupon);
            }
        });
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
