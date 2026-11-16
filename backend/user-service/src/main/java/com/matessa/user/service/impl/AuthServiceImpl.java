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
import com.matessa.user.util.Emails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration, sign-in, refresh-token rotation and logout.
 *
 * <ul>
 *   <li><b>Refresh tokens</b> are 256-bit random values; only their SHA-256 is stored, so a
 *       database or backup leak yields nothing usable. Each sign-in starts a token family;
 *       every refresh rotates within it. Presenting a token that was already rotated is
 *       treated as theft and revokes the whole family — except within a short grace window,
 *       which covers the storefront's middleware and BFF refreshing at the same moment.</li>
 *   <li><b>Brute force</b> is capped per account (not just per IP at the gateway):
 *       {@value #MAX_FAILURES} failures lock sign-in for {@code LOCK_DURATION}. A locked
 *       account answers exactly like a wrong password, so the lock reveals nothing.</li>
 *   <li><b>Enumeration</b>: an unknown email still runs a BCrypt comparison, so "no such
 *       account" and "wrong password" take the same time and give the same answer.</li>
 * </ul>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String BAD_CREDENTIALS = "Invalid email or password";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final long refreshTokenTtlSeconds;
    /** A rotated token replayed within this window is a concurrent refresh, not theft. */
    private final Duration rotationGrace;
    /** Hash of a throwaway password, compared against when the email is unknown. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           UserMapper userMapper,
                           @Value("${jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds,
                           @Value("${auth.refresh-reuse-grace-seconds:10}") long rotationGraceSeconds) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.rotationGrace = Duration.ofSeconds(rotationGraceSeconds);
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            // Without email verification a sign-up form cannot hide that an account exists;
            // the gateway throttles this endpoint, and the message no longer echoes the address.
            throw new EmailAlreadyExistsException("An account with this email already exists. Sign in instead.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setEnabled(true);
        user.getRoles().add(Role.CUSTOMER);
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Not rolled back on a failed attempt: the failure counter must persist even though the
     * method throws, so the exception is excluded from rollback.
     */
    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public TokenResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(Emails.normalize(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash); // same cost as a real check
            throw new InvalidCredentialsException(BAD_CREDENTIALS);
        }
        User user = found.get();
        Instant now = Instant.now();
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        boolean matches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (locked || !user.isEnabled() || !matches) {
            if (!locked && user.isEnabled()) {
                recordFailure(user, now);
            }
            throw new InvalidCredentialsException(BAD_CREDENTIALS);
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        return issueTokens(user, UUID.randomUUID().toString());
    }

    private void recordFailure(User user, Instant now) {
        int failures = user.getFailedLogins() + 1;
        if (failures >= MAX_FAILURES) {
            user.setFailedLogins(0);
            user.setLockedUntil(now.plus(LOCK_DURATION));
            log.warn("Sign-in locked for user {} after {} failed attempts", user.getId(), failures);
        } else {
            user.setFailedLogins(failures);
        }
        userRepository.save(user);
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public TokenResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(hash(refreshTokenValue))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
        Instant now = Instant.now();
        if (stored.isRevoked()) {
            boolean raceWithinGrace = stored.getRotatedAt() != null
                    && stored.getRotatedAt().plus(rotationGrace).isAfter(now);
            if (!raceWithinGrace) {
                // A token that was already exchanged is being presented again: someone else
                // holds a copy. End every session descended from that sign-in.
                int revoked = refreshTokenRepository.revokeFamily(stored.getFamilyId());
                log.warn("Refresh token reuse for user {}: revoked {} live token(s) in its family",
                        stored.getUserId(), revoked);
            }
            throw new InvalidCredentialsException("Refresh token expired or revoked");
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw new InvalidCredentialsException("Refresh token expired or revoked");
        }
        stored.setRevoked(true);      // rotate: a refresh token is single-use
        stored.setRotatedAt(now);
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("Account is disabled"); // banned users cannot mint new tokens
        }
        return issueTokens(user, stored.getFamilyId());
    }

    @Override
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(hash(refreshTokenValue))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId()));
    }

    private TokenResponse issueTokens(User user, String familyId) {
        String accessToken = jwtService.issueAccessToken(user);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(hash(value));
        refreshToken.setFamilyId(familyId);
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtlSeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        return new TokenResponse(accessToken, value, "Bearer", jwtService.getAccessTokenTtlSeconds());
    }

    static String hash(String token) {
        if (token == null) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
