package com.matessa.order.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** order-service's local view of the OrderRejected event from commercial-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderRejectedEvent(
        String eventId,
        String orderNumber,
        String reason,
        Instant occurredAt
) {
}
