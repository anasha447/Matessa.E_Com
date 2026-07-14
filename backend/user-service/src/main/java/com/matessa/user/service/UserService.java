package com.matessa.user.service;

import com.matessa.user.dto.request.ChangePasswordRequest;
import com.matessa.user.dto.request.UpdateProfileRequest;
import com.matessa.user.dto.response.UserResponse;

public interface UserService {

    UserResponse getById(Long id);

    /** Self-service: update the caller's own name/phone. */
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * Self-service: change the caller's own password after verifying the current one.
     * Clears the force-change flag and revokes the caller's other refresh tokens.
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
