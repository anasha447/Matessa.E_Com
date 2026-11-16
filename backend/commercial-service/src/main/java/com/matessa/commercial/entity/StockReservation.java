package com.matessa.commercial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Stock (and possibly one coupon use) held for a checkout. HELD until order-service's
 * OrderPlaced event confirms it, or until it is released: by order-service when the order
 * fails, or by the expiry job when nothing ever came back.
 */
@Entity
@Table(name = "stock_reservation")
@Getter
@Setter
public class StockReservation {

    public enum Status { HELD, CONFIRMED, RELEASED }

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    /** JSON array of {productVariantId, quantity}, merged per variant. */
    @Column(nullable = false, columnDefinition = "text")
    private String lines;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "customer_key", length = 320)
    private String customerKey;

    /** JSON of the response returned to order-service; replayed for a repeated reserve. */
    @Column(nullable = false, columnDefinition = "text")
    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
