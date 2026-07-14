package com.matessa.user.service;

import com.matessa.user.dto.request.CreateStaffRequest;
import com.matessa.user.dto.response.AdminUserResponse;
import com.matessa.user.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Admin-only user administration: provision staff, browse the user directory, and
 * control account status. ADMIN domain — managers do not administer other users.
 */
public interface AdminUserService {

    /** Provision a new MANAGER with a temporary password (force-change on first login). */
    AdminUserResponse createStaff(CreateStaffRequest request);

    /** Paginated user directory, optionally filtered by role and/or enabled status. */
    Page<AdminUserResponse> list(Role role, Boolean enabled, Pageable pageable);

    AdminUserResponse getById(Long id);

    /**
     * Enable (un-ban) or disable (ban) an account. Disabling also revokes the user's
     * refresh tokens so they cannot mint new access tokens. An admin cannot change
     * the status of their own account.
     */
    AdminUserResponse setStatus(Long actingUserId, Long targetUserId, boolean enabled);
}
