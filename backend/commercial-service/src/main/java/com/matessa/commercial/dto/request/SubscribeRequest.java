package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Newsletter signup from the storefront popup. Public. */
public record SubscribeRequest(
        @NotBlank @Email @Size(max = 320) String email
) {
}
