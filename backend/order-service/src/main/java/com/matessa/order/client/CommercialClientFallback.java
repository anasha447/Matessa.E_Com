package com.matessa.order.client;

import com.matessa.order.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Invoked when a commercial-service call fails (timeout, connection error, or open
 * circuit). Throws {@link ServiceUnavailableException}; callers decide what to do —
 * checkout/add fail fast (503), while the cart view catches it and degrades the line
 * to "unavailable" so the rest of the cart still renders.
 */
@Component
public class CommercialClientFallback implements FallbackFactory<CommercialClient> {

    private static final Logger log = LoggerFactory.getLogger(CommercialClientFallback.class);

    @Override
    public CommercialClient create(Throwable cause) {
        return new CommercialClient() {
            @Override
            public VariantPriceStock getVariantPriceStock(Long variantId) {
                log.warn("commercial-service unavailable for variant {}: {}", variantId, cause.toString());
                throw unavailable();
            }

            @Override
            public CouponValidation validateCoupon(CouponValidateRequest request) {
                log.warn("commercial-service unavailable validating coupon {}: {}",
                        request == null ? null : request.code(), cause.toString());
                throw unavailable();
            }
        };
    }

    private ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("Catalog service is temporarily unavailable, please retry");
    }
}
