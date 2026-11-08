package com.matessa.commercial.dto.response;

/** Returned to the storefront popup after signup. {@code couponCode} is the freshly minted
 *  welcome code on a FIRST signup, and {@code null} for a repeat signup — an existing
 *  subscriber's stored code is never echoed back to an unauthenticated caller. */
public record SubscribeResponse(
        String email,
        String couponCode,
        int discountPercent,
        String message
) {
}
