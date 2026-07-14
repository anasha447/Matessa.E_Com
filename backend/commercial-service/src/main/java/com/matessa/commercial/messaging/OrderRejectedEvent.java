package com.matessa.commercial.messaging;

import java.time.Instant;

/**
 * Emitted by commercial-service when it cannot fulfil an OrderPlaced (insufficient stock).
 * order-service consumes it and cancels the order (saga compensation).
 */
public record OrderRejectedEvent(
        String eventId,
        String orderNumber,
        String reason,
        Instant occurredAt
) {
}
