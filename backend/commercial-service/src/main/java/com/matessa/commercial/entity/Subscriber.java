package com.matessa.commercial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A guest who traded their email for a welcome coupon via the newsletter popup. Email is
 *  unique so a repeat signup is idempotent (returns the same coupon). */
@Entity
@Table(name = "subscribers")
@Getter
@Setter
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
