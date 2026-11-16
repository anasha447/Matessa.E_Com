package com.matessa.order.analytics;

import com.matessa.order.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refreshes the analytics materialized views on a fixed schedule (Phase 6). Uses JdbcTemplate
 * (autocommit, no surrounding transaction) because {@code REFRESH MATERIALIZED VIEW CONCURRENTLY}
 * cannot run inside a transaction block. CONCURRENTLY keeps the dashboard readable during the
 * refresh (each view has a unique index). The dashboard is eventually consistent by the refresh
 * interval — fine for analytics; checkout writes never wait on these reads.
 *
 * <p>Every store has its own copy of the views in its own schema, so each is refreshed by
 * schema-qualified name. Store ids are validated identifiers ({@link TenantProperties}),
 * which is what makes the string concatenation safe.
 */
@Component
public class AnalyticsViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsViewRefresher.class);

    private static final List<String> VIEWS = List.of("mv_top_products", "mv_top_customers", "mv_revenue_trend");

    private final JdbcTemplate jdbc;
    private final TenantProperties tenants;

    public AnalyticsViewRefresher(JdbcTemplate jdbc, TenantProperties tenants) {
        this.jdbc = jdbc;
        this.tenants = tenants;
    }

    @Scheduled(fixedDelayString = "${analytics.refresh-interval-ms:120000}",
            initialDelayString = "${analytics.refresh-initial-delay-ms:30000}")
    // One replica refreshes at a time: REFRESH ... CONCURRENTLY is a full recompute, and N
    // replicas each running it every interval only competed for the same lock.
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "analytics-view-refresh", lockAtMostFor = "PT10M")
    public void refresh() {
        for (String store : tenants.tenants()) {
            for (String view : VIEWS) {
                try {
                    jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + store + "." + view);
                } catch (RuntimeException ex) {
                    log.warn("Analytics view refresh failed for {}.{}: {}", store, view, ex.toString());
                }
            }
        }
    }
}
