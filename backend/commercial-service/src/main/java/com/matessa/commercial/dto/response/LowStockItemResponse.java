package com.matessa.commercial.dto.response;

public record LowStockItemResponse(
        Long variantId,
        String sku,
        int stockQuantity,
        int restockLevel
) {
}
