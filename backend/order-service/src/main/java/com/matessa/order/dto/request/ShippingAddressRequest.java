package com.matessa.order.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Inline shipping address supplied at checkout; snapshotted onto the order as JSON. */
public record ShippingAddressRequest(
        @NotBlank String recipientName,
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        String state,
        @NotBlank String postalCode,
        @NotBlank String country,
        String phone
) {
}
