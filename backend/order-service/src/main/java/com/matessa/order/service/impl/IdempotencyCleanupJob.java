package com.matessa.order.service.impl;

import com.matessa.order.tenant.TenantProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Idempotency keys are kept for 24 hours — long enough for any retry — then deleted. */
@Component
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);
    private static final Duration KEEP = Duration.ofHours(24);

    private final IdempotencyStore store;
    private final TenantProperties tenants;

    public IdempotencyCleanupJob(IdempotencyStore store, TenantProperties tenants) {
        this.store = store;
        this.tenants = tenants;
    }

    @Scheduled(fixedDelayString = "${idempotency.cleanup-interval-ms:3600000}", initialDelayString = "${idempotency.cleanup-initial-delay-ms:300000}")
    @SchedulerLock(name = "idempotency-cleanup", lockAtMostFor = "PT10M")
    public void purge() {
        for (String store : tenants.tenants()) {
            try {
                int n = this.store.purgeOlderThan(store, KEEP);
                if (n > 0) {
                    log.info("[{}] Purged {} idempotency key(s) older than {}", store, n, KEEP);
                }
            } catch (RuntimeException e) {
                log.warn("[{}] Idempotency key cleanup failed: {}", store, e.toString());
            }
        }
    }
}
