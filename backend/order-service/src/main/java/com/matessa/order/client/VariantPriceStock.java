package com.matessa.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * order-service's local view of commercial-service's variant price/stock response. It is
 * deliberately a private copy (not a shared DTO) so the services stay decoupled.
 * {@code effectivePrice} already reflects any active automatic discount.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VariantPriceStock(
        Long variantId,
        Long productId,
        String sku,
        String productName,
        BigDecimal effectivePrice,
        int stockQuantity,
        boolean available
) {
}
