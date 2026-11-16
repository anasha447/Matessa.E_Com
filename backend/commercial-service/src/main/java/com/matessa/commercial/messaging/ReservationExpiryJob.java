package com.matessa.commercial.messaging;

import com.matessa.commercial.repository.StockReservationRepository;
import com.matessa.commercial.service.ReservationService;
import com.matessa.commercial.tenant.TenantContext;
import com.matessa.commercial.tenant.TenantProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Returns stock and coupon uses held by reservations whose order never materialised.
 *
 * <p>Order-service releases a reservation itself when an order fails, but it cannot when the
 * process dies between reserving and committing, when the reserve call timed out after
 * commercial-service had already committed it, or when the release call itself fails. Those
 * reservations stay HELD; this job releases them once they pass their expiry. One replica at a
 * time (ShedLock), every store in turn.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final StockReservationRepository reservations;
    private final ReservationService reservationService;
    private final TransactionTemplate transactions;
    private final TenantProperties tenants;

    public ReservationExpiryJob(StockReservationRepository reservations, ReservationService reservationService,
                                TransactionTemplate transactions, TenantProperties tenants) {
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.transactions = transactions;
        this.tenants = tenants;
    }

    @Scheduled(fixedDelayString = "${reservation.expiry-interval-ms:60000}", initialDelayString = "${reservation.expiry-initial-delay-ms:60000}")
    @SchedulerLock(name = "reservation-expiry", lockAtMostFor = "PT5M")
    public void releaseExpired() {
        for (String store : tenants.tenants()) {
            TenantContext.runAs(store, () -> releaseExpiredIn(store));
        }
    }

    /** Visible for tests: one pass over one store (the caller sets the store). */
    public int releaseExpiredIn(String store) {
        List<String> ids = transactions.execute(tx -> reservations.findExpiredHeldIds(Instant.now(), PageRequest.of(0, 200)));
        int released = 0;
        for (String id : ids == null ? List.<String>of() : ids) {
            try {
                if (reservationService.release(id)) {
                    released++;
                }
            } catch (RuntimeException e) {
                log.warn("[{}] Could not release expired reservation {}: {}", store, id, e.toString());
            }
        }
        if (released > 0) {
            log.warn("[{}] Released {} expired reservation(s) whose order never arrived", store, released);
        }
        return released;
    }
}
