package com.matessa.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin-provisioned staff account. The admin sets a temporary password; the new
 * MANAGER is forced to change it on first login (see {@code mustChangePassword}).
 */
public record CreateStaffRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phone
) {
}
