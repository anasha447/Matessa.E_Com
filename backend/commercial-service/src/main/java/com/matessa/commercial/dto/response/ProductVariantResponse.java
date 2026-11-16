package com.matessa.commercial.dto.response;

import java.math.BigDecimal;

public record ProductVariantResponse(
        Long id,
        Long productId,
        String sku,
        String size,
        String color,
        BigDecimal priceOverride,
        BigDecimal listPrice,         // priceOverride, or the product base price (pre-discount)
        BigDecimal effectivePrice,    // listPrice after the best active automatic discount
        int stockQuantity,
        Integer restockLevel,
        boolean active,
        /** True for the hidden variant of a product with no variations (product-level stock). */
        boolean defaultVariant
) {
}
