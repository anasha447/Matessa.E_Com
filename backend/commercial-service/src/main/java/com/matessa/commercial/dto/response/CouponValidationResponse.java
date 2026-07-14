package com.matessa.commercial.dto.response;

import com.matessa.commercial.entity.DiscountType;

import java.math.BigDecimal;

/**
 * Result of validating a coupon code against a cart subtotal. {@code discountAmount} is
 * the money off when valid (zero otherwise); {@code message} explains an invalid result.
 */
public record CouponValidationResponse(
        boolean valid,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        String message
) {
}
