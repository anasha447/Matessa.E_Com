package com.matessa.user.controller;

import com.matessa.user.dto.request.CreateStaffRequest;
import com.matessa.user.dto.request.UpdateUserStatusRequest;
import com.matessa.user.dto.response.AdminUserResponse;
import com.matessa.user.entity.Role;
import com.matessa.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user administration. ADMIN only — provisioning staff and controlling account
 * status is the admin's domain (managers run the store, not the user directory).
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody CreateStaffRequest request) {
        return adminUserService.createStaff(request);
    }

    @GetMapping
    public Page<AdminUserResponse> list(@RequestParam(required = false) Role role,
                                        @RequestParam(required = false) Boolean enabled,
                                        Pageable pageable) {
        return adminUserService.list(role, enabled, pageable);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable Long id) {
        return adminUserService.getById(id);
    }

    @PutMapping("/{id}/status")
    public AdminUserResponse setStatus(Authentication authentication,
                                       @PathVariable Long id,
                                       @Valid @RequestBody UpdateUserStatusRequest request) {
        Long actingUserId = (Long) authentication.getPrincipal();
        return adminUserService.setStatus(actingUserId, id, request.enabled());
    }
}
