package com.matessa.order.client;

import java.math.BigDecimal;

/** Request body sent to commercial-service POST /api/coupons/validate. */
public record CouponValidateRequest(
        String code,
        BigDecimal subtotal
) {
}
