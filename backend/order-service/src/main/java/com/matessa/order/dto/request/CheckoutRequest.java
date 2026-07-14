package com.matessa.order.dto.request;

import com.matessa.order.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Place an order from the caller's current cart. Shipping address is provided inline. */
public record CheckoutRequest(
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        @NotNull PaymentMethod paymentMethod,
        String couponCode,
        String shippingMethod
) {
}
