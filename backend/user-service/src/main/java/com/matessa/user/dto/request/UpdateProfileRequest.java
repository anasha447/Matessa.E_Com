package com.matessa.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Self-service profile edit (the caller's own account). */
public record UpdateProfileRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phone
) {
}
