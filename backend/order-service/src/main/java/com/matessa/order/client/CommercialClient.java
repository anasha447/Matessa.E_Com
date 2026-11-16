package com.matessa.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Synchronous calls to commercial-service. Resolved via Eureka (lb://commercial-service)
 * and wrapped in a circuit breaker; on failure {@link CommercialClientFallback} kicks in.
 */
@FeignClient(name = "commercial-service", fallbackFactory = CommercialClientFallback.class)
public interface CommercialClient {

    /** Live price + stock for a variant (the order-facing lookup; discount-aware). */
    @GetMapping("/api/variants/{variantId}")
    VariantPriceStock getVariantPriceStock(@PathVariable("variantId") Long variantId);

    /** Validate a coupon code against a subtotal (checkout). Forwards the caller's JWT. */
    @PostMapping("/api/coupons/validate")
    CouponValidation validateCoupon(@RequestBody CouponValidateRequest request);

    /**
     * Holds stock for every line and claims one coupon use, in a single transaction on the
     * commercial side, returning the prices it reserved at. 409 if anything is short.
     *
     * <p>This replaces the per-line {@code getVariantPriceStock} loop that used to precede
     * checkout. That loop only read stock, so nothing stopped two buyers of the last unit from
     * both passing and both being charged. It was also one round trip per line; this is one
     * for the whole basket.
     */
    @PostMapping("/internal/reservations")
    ReservationResult reserve(@RequestBody ReservationRequest request);

    /** Hands a reservation back when the order does not complete. Idempotent; unknown id is a no-op. */
    @PostMapping("/internal/reservations/{reservationId}/release")
    void releaseReservation(@PathVariable("reservationId") String reservationId);
}
