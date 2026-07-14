package com.matessa.commercial.dto.request;

import com.matessa.commercial.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** Set exactly one of {@code productId} or {@code categoryId} (validated in the service). */
public record DiscountRequest(
        Long productId,
        Long categoryId,
        @NotNull DiscountType type,
        @NotNull @DecimalMin("0.0") BigDecimal value,
        @NotNull Instant validFrom,
        @NotNull Instant validTo,
        Boolean active
) {
}
