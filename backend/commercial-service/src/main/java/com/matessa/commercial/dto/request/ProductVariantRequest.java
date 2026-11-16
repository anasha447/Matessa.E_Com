package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductVariantRequest(
        // SKU is generated server-side (see SkuGenerator); any client-supplied value is ignored.
        String sku,
        String size,
        String color,
        @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "priceOverride cannot be negative")
        BigDecimal priceOverride,                 // null = use the product base price
        @NotNull @Min(0) Integer stockQuantity,
        Integer restockLevel
) {
}
