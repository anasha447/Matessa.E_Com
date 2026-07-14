package com.matessa.order.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productVariantId,
        String productName,
        String sku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
