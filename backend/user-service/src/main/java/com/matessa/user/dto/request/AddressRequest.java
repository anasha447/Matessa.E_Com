package com.matessa.user.dto.request;

import com.matessa.user.entity.AddressType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddressRequest(
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        String state,
        @NotBlank String postalCode,
        @NotBlank String country,
        @NotNull AddressType type,
        @JsonProperty("isDefault") boolean defaultAddress
) {
}
