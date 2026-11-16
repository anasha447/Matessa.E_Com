package com.matessa.commercial.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/** commercial-service's local view of the OrderPlaced event from order-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEvent(
        String eventId,
        String orderNumber,
        Long userId,
        String couponCode,
        List<Line> items,
        Instant occurredAt,
        /** Null for events published before reservations had ids. */
        String reservationId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(Long productVariantId, int quantity) {
    }
}
