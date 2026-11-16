package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(
        @NotNull Long categoryId,
        @NotBlank String name,
        @NotBlank String slug,
        String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal basePrice,
        String brand,
        /**
         * Product-level stock, used only on create (for a product sold without variations). It
         * goes onto the product's hidden default variant; later changes are made on that variant.
         */
        @jakarta.validation.constraints.Min(0) Integer stockQuantity
) {
}
