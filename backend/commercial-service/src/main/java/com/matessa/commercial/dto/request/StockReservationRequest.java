package com.matessa.commercial.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Internal checkout request: hold stock for these lines and, if a coupon is given, consume
 * one of its uses — all in one transaction, before the caller takes payment.
 */
public record StockReservationRequest(
        @NotEmpty(message = "At least one line is required")
        @Size(max = 100, message = "A reservation may not exceed 100 lines")
        @Valid
        List<Line> items,

        String couponCode,

        /**
         * How many times the customer has already used {@code couponCode}, supplied by the
         * caller because the order history that figure comes from is not ours. Null means a
         * guest, or no coupon - either way the per-customer limit does not apply.
         */
        @Min(value = 0, message = "priorCouponUses cannot be negative")
        Integer priorCouponUses
) {
    public record Line(
            @NotNull(message = "productVariantId is required")
            Long productVariantId,

            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1000, message = "Quantity may not exceed 1000 per line")
            int quantity
    ) {
    }
}
