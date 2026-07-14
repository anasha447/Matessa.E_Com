package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Storefront contact form submission. Public — a guest supplies their email so staff can reply. */
public record ContactQueryRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String queryType,
        @NotBlank @Size(max = 4000) String message
) {
}
