package com.matessa.commercial.dto.response;

/** Returned to the storefront popup after signup — carries the welcome coupon code to show. */
public record SubscribeResponse(
        String email,
        String couponCode,
        int discountPercent,
        String message
) {
}
