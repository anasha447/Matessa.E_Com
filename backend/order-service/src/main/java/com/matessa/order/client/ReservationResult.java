package com.matessa.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * A successful reservation: the stock is already held and the coupon use already claimed.
 *
 * <p>The prices are the ones the reservation was made at, so they are snapshotted onto the
 * order rather than re-derived. Pricing and discounts belong to commercial-service, so the
 * money figures are computed there once instead of being reassembled here from a second set
 * of reads that could disagree.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationResult(
        List<Line> lines,
        BigDecimal subtotal,
        String couponCode,
        BigDecimal discountAmount,
        BigDecimal total
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            Long productVariantId,
            Long productId,
            String sku,
            String productName,
            BigDecimal unitPrice,
            int quantity
    ) {
    }
}
