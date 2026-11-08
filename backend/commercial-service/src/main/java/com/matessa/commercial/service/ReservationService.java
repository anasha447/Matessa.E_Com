package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.StockReservationRequest;
import com.matessa.commercial.dto.response.StockReservationResponse;

/**
 * Holds the scarce things a checkout consumes - stock, and a coupon use - before the caller
 * takes payment.
 *
 * <p>Checkout used to read stock, take the payment, and only then decrement asynchronously
 * when commercial-service consumed the OrderPlaced event. Nothing held the stock across that
 * gap, so every concurrent buyer of the last unit passed the read, every one of them was
 * charged, and all but one order was cancelled afterwards. Reserving first turns that into an
 * ordinary 409 before any money moves.
 */
public interface ReservationService {

    /**
     * Reserves every line and claims one coupon use, or fails having reserved nothing. Also
     * returns the prices the reservation was made at, so the caller snapshots those instead
     * of re-pricing and risking a different answer.
     *
     * @throws com.matessa.commercial.exception.ConflictException if any line is short, the
     *         coupon has no uses left, or the customer is over its per-customer limit
     */
    StockReservationResponse reserve(StockReservationRequest request);

    /** Compensates a reservation whose order did not complete. */
    void release(StockReservationRequest request);
}
