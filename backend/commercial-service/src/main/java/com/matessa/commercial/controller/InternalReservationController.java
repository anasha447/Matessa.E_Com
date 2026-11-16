package com.matessa.commercial.controller;

import com.matessa.commercial.dto.request.StockReservationRequest;
import com.matessa.commercial.dto.response.StockReservationResponse;
import com.matessa.commercial.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
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
 * <p>They carry no user JWT (guest checkout has none), so they are authenticated as a
 * service instead: every call must present the shared {@code X-Internal-Key}
 * ({@code INTERNAL_API_KEY}), checked by {@code InternalApiKeyFilter}. The gateway also denies
 * {@code /internal/**} outright, and production publishes no service ports.
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

    /** Compensates a reservation whose order did not complete. Idempotent; unknown id is a no-op. */
    @PostMapping("/{reservationId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable @jakarta.validation.constraints.Pattern(regexp = "[0-9a-fA-F-]{36}") String reservationId) {
        reservationService.release(reservationId);
    }
}
