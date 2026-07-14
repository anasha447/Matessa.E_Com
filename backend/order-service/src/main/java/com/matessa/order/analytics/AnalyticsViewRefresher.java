package com.matessa.order.analytics;

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
 */
@Component
public class AnalyticsViewRefresher {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsViewRefresher.class);
    private static final List<String> VIEWS = List.of("mv_top_products", "mv_top_customers", "mv_revenue_trend");

    private final JdbcTemplate jdbc;

    public AnalyticsViewRefresher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${analytics.refresh-interval-ms:120000}",
            initialDelayString = "${analytics.refresh-initial-delay-ms:30000}")
    public void refresh() {
        for (String view : VIEWS) {
            try {
                jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + view);
            } catch (RuntimeException ex) {
                log.warn("Analytics view refresh failed for {}: {}", view, ex.toString());
            }
        }
    }
}
