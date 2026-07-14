package com.matessa.order.dto.response;

import java.math.BigDecimal;

/**
 * A cart line enriched with the live price/stock from commercial-service. If the catalog
 * is unreachable or the variant is gone, {@code available} is false and price fields are
 * null so the rest of the cart still renders.
 */
public record CartLineResponse(
        Long id,
        Long productVariantId,
        String productName,
        String sku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        boolean available,
        int stockQuantity
) {
}
