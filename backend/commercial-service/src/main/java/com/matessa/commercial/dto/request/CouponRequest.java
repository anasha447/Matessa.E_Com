package com.matessa.commercial.dto.request;

import com.matessa.commercial.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponRequest(
        @NotBlank String code,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.0") BigDecimal discountValue,
        @DecimalMin("0.0") BigDecimal minSpend,
        @Min(1) Integer maxUses,
        @Min(1) Integer perUserLimit,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil,
        Boolean active
) {
}
