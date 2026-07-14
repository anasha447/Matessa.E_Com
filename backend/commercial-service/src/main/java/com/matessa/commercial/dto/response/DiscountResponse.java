package com.matessa.commercial.dto.response;

import com.matessa.commercial.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record DiscountResponse(
        Long id,
        Long productId,
        Long categoryId,
        DiscountType type,
        BigDecimal value,
        Instant validFrom,
        Instant validTo,
        boolean active,
        Long createdBy
) {
}
