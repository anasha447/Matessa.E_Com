package com.matessa.commercial.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The outcome of a successful reservation: stock is already held and the coupon use (if any)
 * is already consumed. Prices are the values the reservation was made at, so the caller
 * snapshots these rather than re-pricing and risking a different answer.
 */
public record StockReservationResponse(
        List<ReservedLine> lines,
        BigDecimal subtotal,
        String couponCode,
        BigDecimal discountAmount,
        BigDecimal total
) {
    public record ReservedLine(
            Long productVariantId,
            Long productId,
            String sku,
            String productName,
            BigDecimal unitPrice,
            int quantity
    ) {
    }
}
