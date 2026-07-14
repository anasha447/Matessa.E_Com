package com.matessa.commercial.dto.response;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long productId,
        Long userId,
        int rating,
        String text,
        Instant createdAt
) {
}
