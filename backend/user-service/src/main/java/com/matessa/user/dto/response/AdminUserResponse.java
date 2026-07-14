package com.matessa.user.dto.response;

import com.matessa.user.entity.Role;

import java.time.Instant;
import java.util.Set;

/**
 * Fuller user view for the admin panel — adds account status and provisioning
 * state that the self-facing {@link UserResponse} deliberately omits.
 */
public record AdminUserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Set<Role> roles,
        boolean enabled,
        boolean mustChangePassword,
        Instant createdAt
) {
}
