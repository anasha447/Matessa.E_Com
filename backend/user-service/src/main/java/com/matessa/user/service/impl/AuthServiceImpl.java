package com.matessa.user.service.impl;

import com.matessa.user.dto.request.LoginRequest;
import com.matessa.user.dto.request.RegisterRequest;
import com.matessa.user.dto.response.TokenResponse;
import com.matessa.user.dto.response.UserResponse;
import com.matessa.user.entity.RefreshToken;
import com.matessa.user.entity.Role;
import com.matessa.user.entity.User;
import com.matessa.user.exception.EmailAlreadyExistsException;
import com.matessa.user.exception.InvalidCredentialsException;
import com.matessa.user.mapper.UserMapper;
import com.matessa.user.repository.RefreshTokenRepository;
import com.matessa.user.repository.UserRepository;
import com.matessa.user.security.JwtService;
import com.matessa.user.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final long refreshTokenTtlSeconds;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           UserMapper userMapper,
                           @Value("${jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setEnabled(true);
        user.getRoles().add(Role.CUSTOMER);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Refresh token expired or revoked");
        }
        stored.setRevoked(true); // rotate: a refresh token is single-use
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("Account is disabled"); // banned users cannot mint new tokens
        }
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> token.setRevoked(true));
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtlSeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        return new TokenResponse(accessToken, refreshToken.getToken(), "Bearer", jwtService.getAccessTokenTtlSeconds());
    }
}
