package com.matessa.order.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Payload of the OrderPlaced event. {@code eventId} lets consumers de-duplicate
 * (at-least-once delivery). Carries just what consumers need: which variants/quantities
 * to decrement and the coupon to redeem.
 */
public record OrderPlacedEvent(
        String eventId,
        String orderNumber,
        Long userId,
        String couponCode,
        List<Line> items,
        Instant occurredAt
) {
    public record Line(Long productVariantId, int quantity) {
    }
}
