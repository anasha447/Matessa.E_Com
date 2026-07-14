package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Checkout-time preview: is this code usable for this cart subtotal, and for how much off? */
public record CouponValidateRequest(
        @NotBlank String code,
        @NotNull @DecimalMin("0.0") BigDecimal subtotal
) {
}
