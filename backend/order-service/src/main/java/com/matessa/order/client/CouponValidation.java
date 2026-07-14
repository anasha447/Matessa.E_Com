package com.matessa.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** order-service's local view of commercial-service's coupon validation result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CouponValidation(
        boolean valid,
        String code,
        String discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        String message
) {
}
