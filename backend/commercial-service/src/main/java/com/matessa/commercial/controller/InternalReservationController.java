package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.StockReservationRequest;
import com.matessa.commercial.dto.response.StockReservationResponse;
import com.matessa.commercial.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only: order-service calls these while placing an order.
 *
 * <p><b>Not reachable from outside.</b> The /internal prefix has no gateway route and is
 * additionally denied in the gateway security configuration, and docker-compose.prod.yml
 * stops publishing this service port - so these endpoints are only addressable from inside
 * the Docker network. The prefix exists so that rule can be written once and cover anything
 * added here later.
 *
 * <p>They are permitAll within this service because they must also work for guest checkout,
 * which carries no JWT at all. So the protection here is network isolation, not
 * authentication. The proper fix is real service-to-service auth - a client-credentials
 * token minted by user-service for order-service - which this project does not have yet; the
 * existing public GET /api/variants/{id} has the same character. Until then the published
 * service ports in the development compose file are a development-only convenience, which is
 * why docker-compose.prod.yml removes them.
 */
@RestController
@RequestMapping("/internal/reservations")
public class InternalReservationController {

    private final ReservationService reservationService;

    public InternalReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Holds stock plus one coupon use, or 409s having reserved nothing. */
    @PostMapping
    public StockReservationResponse reserve(@Valid @RequestBody StockReservationRequest request) {
        return reservationService.reserve(request);
    }

    /** Compensates a reservation whose order did not complete. */
    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@Valid @RequestBody StockReservationRequest request) {
        reservationService.release(request);
    }
}
