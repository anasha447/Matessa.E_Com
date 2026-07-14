package com.matessa.order.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateShippingRequest(
        @NotBlank String shippingMethod,
        String trackingNumber
) {
}
