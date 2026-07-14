package com.matessa.order.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Headline sales KPIs for the admin/manager dashboard. Revenue = paid orders only. */
public record SalesAnalyticsResponse(
        Revenue revenue,
        long totalOrders,
        Map<String, Long> ordersByStatus,
        BigDecimal averageOrderValue,
        BigDecimal totalDiscountsGiven,
        long couponRedemptions,
        Refunds refunds,
        List<PaymentMethodRevenue> revenueByPaymentMethod,
        Operations operations
) {
    public record Revenue(
            BigDecimal today,
            BigDecimal thisWeek,
            BigDecimal thisMonth,
            BigDecimal thisYear,
            BigDecimal allTime,
            Double monthOverMonthPct      // null when there was no previous-month revenue
    ) {
    }

    public record Refunds(long count, BigDecimal amount) {
    }

    public record PaymentMethodRevenue(String paymentMethod, long orders, BigDecimal revenue) {
    }

    public record Operations(
            long awaitingPayment,         // status PENDING
            long awaitingShipment,        // status PAID
            Double avgHoursToShip         // null until something has shipped
    ) {
    }
}
