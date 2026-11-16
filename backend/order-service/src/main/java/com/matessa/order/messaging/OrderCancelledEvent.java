package com.matessa.order.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Payload of the OrderCancelled event — tells commercial-service to restore the stock
 * (and release the coupon) for a cancelled order. {@code eventId} enables de-duplication.
 */
public record OrderCancelledEvent(
        String eventId,
        String orderNumber,
        String couponCode,
        List<Line> items,
        Instant occurredAt,
        /** The commercial-service reservation behind the order (null for older orders). */
        String reservationId
) {
    public record Line(Long productVariantId, int quantity) {
    }
}
