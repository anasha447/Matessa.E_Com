package com.matessa.user.service.impl;

import com.matessa.user.dto.request.CreateStaffRequest;
import com.matessa.user.dto.response.AdminUserResponse;
import com.matessa.user.entity.Role;
import com.matessa.user.entity.User;
import com.matessa.user.exception.BusinessRuleException;
import com.matessa.user.exception.EmailAlreadyExistsException;
import com.matessa.user.exception.ResourceNotFoundException;
import com.matessa.user.mapper.UserMapper;
import com.matessa.user.repository.RefreshTokenRepository;
import com.matessa.user.repository.UserRepository;
import com.matessa.user.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AdminUserServiceImpl(UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                UserMapper userMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public AdminUserResponse createStaff(CreateStaffRequest request) {
        String email = com.matessa.user.util.Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("An account with this email already exists.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setEnabled(true);
        user.setMustChangePassword(true); // temp password — change on first login
        user.getRoles().add(Role.MANAGER);
        return userMapper.toAdminResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(Role role, Boolean enabled, Pageable pageable) {
        return userRepository.search(role, enabled, pageable).map(userMapper::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getById(Long id) {
        return userMapper.toAdminResponse(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id)));
    }

    @Override
    @Transactional
    public AdminUserResponse setStatus(Long actingUserId, Long targetUserId, boolean enabled) {
        if (actingUserId.equals(targetUserId)) {
            throw new BusinessRuleException("You cannot change the status of your own account");
        }
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));
        user.setEnabled(enabled);
        if (!enabled) {
            // a banned user keeps a valid access token until it expires (~minutes),
            // but must not be able to mint new ones via refresh
            refreshTokenRepository.revokeAllForUser(user.getId());
        }
        return userMapper.toAdminResponse(userRepository.save(user));
    }
}
