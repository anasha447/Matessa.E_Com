package com.matessa.order.client;

import com.matessa.order.exception.BadRequestException;
import com.matessa.order.exception.ConflictException;
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
                throw translate(cause);
            }

            @Override
            public CouponValidation validateCoupon(CouponValidateRequest request) {
                log.warn("commercial-service unavailable validating coupon {}: {}",
                        request == null ? null : request.code(), cause.toString());
                throw translate(cause);
            }

            @Override
            public ReservationResult reserve(ReservationRequest request) {
                log.warn("commercial-service unavailable reserving {} line(s): {}",
                        request == null || request.items() == null ? 0 : request.items().size(),
                        cause.toString());
                throw translate(cause);
            }

            @Override
            public void releaseReservation(ReservationRequest request) {
                // Nothing to throw towards: the caller is already compensating a failure and
                // must not be derailed. Log loudly instead - this is stock left held, which a
                // human (or a future reconciliation job) has to put back.
                log.error("commercial-service unavailable releasing a reservation; stock may stay "
                        + "held for lines {}: {}",
                        request == null ? null : request.items(), cause.toString());
            }
        };
    }

    /**
     * The fallback runs for ANY throwable, including the business refusals that
     * {@link CommercialErrorDecoder} produced from a 409 or 400. Those must pass through
     * unchanged - replacing "that is out of stock" with "the store is down" both misinforms
     * the customer and hides a legitimate 409 behind a 503. Only genuine transport failures
     * become unavailability.
     */
    private RuntimeException translate(Throwable cause) {
        if (cause instanceof ConflictException || cause instanceof BadRequestException) {
            return (RuntimeException) cause;
        }
        return new ServiceUnavailableException("Catalog service is temporarily unavailable, please retry");
    }
}
