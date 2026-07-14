package com.matessa.commercial.dto.response;

import com.matessa.commercial.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(
        Long id,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minSpend,
        Integer maxUses,
        int usedCount,
        Integer perUserLimit,
        Instant validFrom,
        Instant validUntil,
        boolean active
) {
}
