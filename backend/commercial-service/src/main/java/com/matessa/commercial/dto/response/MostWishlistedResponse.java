package com.matessa.commercial.dto.response;

public record MostWishlistedResponse(
        Long productVariantId,
        String sku,
        long wishlistCount
) {
}
