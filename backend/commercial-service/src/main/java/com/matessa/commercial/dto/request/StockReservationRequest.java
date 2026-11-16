package com.matessa.commercial.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Internal checkout request: hold stock for these lines and, if a coupon is given, consume
 * one of its uses — all in one transaction, before the caller takes payment.
 */
public record StockReservationRequest(
        /** Chosen by order-service; makes reserve and release idempotent and addressable. */
        @NotBlank
        @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "reservationId must be a UUID")
        String reservationId,
        @NotEmpty(message = "At least one line is required")
        @Size(max = 50, message = "A reservation may not exceed 50 lines")
        @Valid
        List<Line> items,
        @Size(max = 64)
        String couponCode,
        /**
         * Who is buying, for the per-customer coupon limit: {@code u:<userId>} or
         * {@code e:<lower-cased email>} for a guest. Null means no per-customer limit check.
         */
        @Size(max = 320)
        String customerKey,
        /**
         * Uses of {@code couponCode} by this customer in orders placed before reservations were
         * recorded here (order-service's history). The limit check uses whichever is larger,
         * this or the redemptions recorded in this service.
         */
        @Min(value = 0, message = "priorCouponUses cannot be negative")
        Integer priorCouponUses
) {
    public record Line(
            @NotNull(message = "productVariantId is required")
            Long productVariantId,
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 20, message = "Quantity may not exceed 20 per line")
            int quantity
    ) {
    }
}
