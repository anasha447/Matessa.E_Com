-- Phase 6 (CQRS read model): materialized views for the heavy dashboard aggregations so
-- analytics reads stop contending with checkout writes on the OLTP tables. Pre-filtered to
-- paid orders (payment_status = 'COMPLETED'); refreshed on a schedule by AnalyticsViewRefresher.
-- Each has a UNIQUE index so it can be REFRESHed CONCURRENTLY (no read lock during refresh).

CREATE MATERIALIZED VIEW mv_top_products AS
SELECT oi.product_variant_id,
       oi.sku_snapshot,
       oi.product_name_snapshot,
       sum(oi.quantity)::bigint                                  AS units,
       coalesce(sum(oi.unit_price_snapshot * oi.quantity), 0)    AS revenue
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.payment_status = 'COMPLETED'
GROUP BY oi.product_variant_id, oi.sku_snapshot, oi.product_name_snapshot;
CREATE UNIQUE INDEX ux_mv_top_products ON mv_top_products (product_variant_id, sku_snapshot, product_name_snapshot);

CREATE MATERIALIZED VIEW mv_top_customers AS
SELECT o.user_id,
       count(*)::bigint                    AS order_count,
       coalesce(sum(o.total), 0)           AS revenue
FROM orders o
WHERE o.payment_status = 'COMPLETED' AND o.user_id IS NOT NULL
GROUP BY o.user_id;
CREATE UNIQUE INDEX ux_mv_top_customers ON mv_top_customers (user_id);

CREATE MATERIALIZED VIEW mv_revenue_trend AS
SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month,
       coalesce(sum(total), 0)                             AS revenue
FROM orders
WHERE payment_status = 'COMPLETED'
GROUP BY 1;
CREATE UNIQUE INDEX ux_mv_revenue_trend ON mv_revenue_trend (month);
