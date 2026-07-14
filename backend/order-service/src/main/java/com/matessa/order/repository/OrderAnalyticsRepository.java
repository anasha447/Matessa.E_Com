package com.matessa.order.repository;

import com.matessa.order.entity.Order;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-only aggregate queries for the sales dashboard. Kept separate from OrderRepository
 * so the write-side repository stays small. "Revenue" counts only paid orders
 * (paymentStatus = COMPLETED); refunds are tracked separately.
 */
public interface OrderAnalyticsRepository extends Repository<Order, Long> {

    @Query("select coalesce(sum(o.total), 0) from Order o where o.paymentStatus = :status and o.createdAt >= :from")
    BigDecimal revenueSince(@Param("status") PaymentStatus status, @Param("from") Instant from);

    @Query("select coalesce(sum(o.total), 0) from Order o "
            + "where o.paymentStatus = :status and o.createdAt >= :from and o.createdAt < :to")
    BigDecimal revenueBetween(@Param("status") PaymentStatus status,
                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.paymentStatus = :status")
    BigDecimal totalRevenue(@Param("status") PaymentStatus status);

    @Query("select count(o) from Order o where o.paymentStatus = :status")
    long countByPaymentStatus(@Param("status") PaymentStatus status);

    @Query("select coalesce(sum(o.discountAmount), 0) from Order o where o.paymentStatus = :status")
    BigDecimal totalDiscounts(@Param("status") PaymentStatus status);

    @Query("select count(o) from Order o where o.paymentStatus = :status and o.couponCode is not null")
    long couponRedemptions(@Param("status") PaymentStatus status);

    long countByStatus(OrderStatus status);

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countGroupedByStatus();

    @Query("select o.paymentMethod, count(o), coalesce(sum(o.total), 0) from Order o "
            + "where o.paymentStatus = :status and o.paymentMethod is not null group by o.paymentMethod")
    List<Object[]> revenueByPaymentMethod(@Param("status") PaymentStatus status);

    // Orders per shipping state (India map). Counts every order except cancelled ones —
    // deliberately NOT paid-only: COD orders stay payment-pending until delivery and
    // would otherwise vanish from the map. Null state rows are mapped to "Other" in the service.
    @Query("select o.shippingState, count(o) from Order o "
            + "where o.status <> :excluded group by o.shippingState order by count(o) desc")
    List<Object[]> countGroupedByShippingState(@Param("excluded") OrderStatus excluded);

    // Phase 6 (CQRS): these read from materialized views (mv_*), refreshed on a schedule, so the
    // heavy group-by/join aggregations don't run against the live OLTP tables on every dashboard
    // load. The views are pre-filtered to paid orders.
    @Query(value = """
            select product_variant_id, sku_snapshot, product_name_snapshot, units, revenue
            from mv_top_products order by revenue desc limit :limit
            """, nativeQuery = true)
    List<Object[]> topProducts(@Param("limit") int limit);

    @Query(value = """
            select user_id, order_count, revenue
            from mv_top_customers order by revenue desc limit :limit
            """, nativeQuery = true)
    List<Object[]> topCustomers(@Param("limit") int limit);

    @Query(value = """
            select month, revenue from mv_revenue_trend
            where month >= to_char(cast(:from as timestamptz), 'YYYY-MM')
            order by month
            """, nativeQuery = true)
    List<Object[]> monthlyRevenueTrend(@Param("from") Instant from);

    @Query(value = """
            select avg(extract(epoch from h.changed_at - o.created_at))
            from orders o join order_status_history h on h.order_id = o.id
            where h.to_status = 'SHIPPED'
            """, nativeQuery = true)
    Double avgSecondsToShip();
}
