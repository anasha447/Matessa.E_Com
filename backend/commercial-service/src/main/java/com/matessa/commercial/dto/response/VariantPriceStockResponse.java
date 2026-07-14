package com.matessa.commercial.dto.response;

import java.math.BigDecimal;

/**
 * What order-service fetches (via Feign) at checkout: the live price + stock for a
 * variant, and whether it is purchasable.
 */
public record VariantPriceStockResponse(
        Long variantId,
        Long productId,
        String sku,
        String productName,
        BigDecimal effectivePrice,
        int stockQuantity,
        boolean available     // product is active
) {
}
