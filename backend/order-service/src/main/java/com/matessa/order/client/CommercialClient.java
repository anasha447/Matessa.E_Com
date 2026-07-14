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
}
