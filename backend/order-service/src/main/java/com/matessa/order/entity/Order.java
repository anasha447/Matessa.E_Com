package com.matessa.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A placed order. Totals, coupon code and shipping address are snapshots frozen at
 * checkout; payment and shipping are folded in as fields (no separate service in v1).
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")   // null for guest orders (placed without an account)
    private Long userId;

    @Column(name = "guest_email")   // set only for guest orders; used for /track lookup
    private String guestEmail;

    /** The commercial-service stock reservation this order consumed. */
    @Column(name = "reservation_id", length = 36)
    private String reservationId;

    @Column(name = "idempotency_key")   // client-supplied; unique when present (double-submit guard)
    private String idempotencyKey;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "coupon_code")
    private String couponCode;            // snapshot

    @Column(name = "shipping_address_snapshot", nullable = false)
    private String shippingAddressSnapshot;   // JSON snapshot, not an FK

    @Column(name = "shipping_state")   // promoted from the snapshot for orders-by-state analytics
    private String shippingState;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "transaction_id")
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_status", nullable = false)
    private ShippingStatus shippingStatus;

    @Column(name = "shipping_method")
    private String shippingMethod;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;   // optimistic lock for concurrent staff updates
}
