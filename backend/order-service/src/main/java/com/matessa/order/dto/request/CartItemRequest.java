package com.matessa.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(
        @NotNull Long productVariantId,
        @NotNull @Min(1) @jakarta.validation.constraints.Max(value = 20, message = "At most 20 of one item per order") Integer quantity
) {
}
