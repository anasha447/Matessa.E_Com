package com.matessa.commercial.dto.response;

import java.time.Instant;

/** Staff-facing subscriber row for the admin Contacts page. */
public record SubscriberResponse(
        Long id,
        String email,
        String couponCode,
        Instant createdAt
) {
}
