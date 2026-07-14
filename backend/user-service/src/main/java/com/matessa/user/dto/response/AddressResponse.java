package com.matessa.user.dto.response;

import com.matessa.user.entity.AddressType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AddressResponse(
        Long id,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        AddressType type,
        @JsonProperty("isDefault") boolean defaultAddress
) {
}
