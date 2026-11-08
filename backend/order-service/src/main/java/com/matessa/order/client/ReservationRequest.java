package com.matessa.order.client;

import java.util.List;

/**
 * Asks commercial-service to hold stock for these lines and consume one use of the coupon.
 * A private copy of commercial's request shape, not a shared DTO, so the services stay
 * decoupled.
 */
public record ReservationRequest(
        List<Line> items,
        String couponCode,

        /**
         * How many times this customer has already used {@code couponCode}, or null for a
         * guest (no history to count, so no per-customer limit applies). Counted here because
         * order-service owns the order history; compared against the coupon's perUserLimit on
         * the commercial side, inside the transaction that claims the use.
         */
        Integer priorCouponUses
) {

    public record Line(Long productVariantId, int quantity) {
    }
}
