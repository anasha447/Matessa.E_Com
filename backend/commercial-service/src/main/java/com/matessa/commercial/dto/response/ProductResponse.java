package com.matessa.commercial.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        Long categoryId,
        String name,
        String slug,
        String description,
        BigDecimal basePrice,
        String brand,
        boolean active,
        Instant createdAt
) {
}
