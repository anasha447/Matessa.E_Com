package com.matessa.order.dto.request;

import com.matessa.order.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Place an order without an account. The cart lives client-side for guests, so the lines
 * are supplied inline (and re-priced server-side). The email lets the buyer track the
 * order afterwards via /track.
 */
public record GuestCheckoutRequest(
        @NotBlank @Email String guestEmail,
        @NotEmpty @jakarta.validation.constraints.Size(max = 50, message = "At most 50 different items per order") List<@Valid CartItemRequest> items,
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        @NotNull PaymentMethod paymentMethod,
        @jakarta.validation.constraints.Size(max = 64) String couponCode,
        @jakarta.validation.constraints.Size(max = 64) String shippingMethod
) {
}
