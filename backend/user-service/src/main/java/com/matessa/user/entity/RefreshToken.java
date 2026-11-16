package com.matessa.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 (hex) of the token handed to the client. The token itself is never stored. */
    @Column(nullable = false, unique = true)
    private String token;

    /** Every rotation of one login shares a family; replaying a rotated token revokes it all. */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    /** Set when this token was exchanged for a new one (as opposed to revoked by logout/ban). */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;
}
