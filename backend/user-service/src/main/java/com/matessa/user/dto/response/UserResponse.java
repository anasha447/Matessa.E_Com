package com.matessa.user.dto.response;

import com.matessa.user.entity.Role;

import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Set<Role> roles,
        boolean mustChangePassword
) {
}
