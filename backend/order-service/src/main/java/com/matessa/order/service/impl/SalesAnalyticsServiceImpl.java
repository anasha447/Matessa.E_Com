package com.matessa.order.service.impl;

import com.matessa.order.dto.response.RevenueTrendPoint;
import com.matessa.order.dto.response.SalesAnalyticsResponse;
import com.matessa.order.dto.response.StateOrderCountResponse;
import com.matessa.order.dto.response.TopCustomerResponse;
import com.matessa.order.dto.response.TopProductResponse;
import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.repository.OrderAnalyticsRepository;
import com.matessa.order.service.SalesAnalyticsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesAnalyticsServiceImpl implements SalesAnalyticsService {

    private final OrderAnalyticsRepository analytics;

    public SalesAnalyticsServiceImpl(OrderAnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    @Override
    @Transactional(readOnly = true)
    public SalesAnalyticsResponse getSalesAnalytics() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfWeek = today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfYear = today.withDayOfYear(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfLastMonth = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal thisMonth = analytics.revenueSince(PaymentStatus.COMPLETED, startOfMonth);
        BigDecimal lastMonth = analytics.revenueBetween(PaymentStatus.COMPLETED, startOfLastMonth, startOfMonth);
        Double momPct = lastMonth.signum() > 0
                ? thisMonth.subtract(lastMonth).multiply(BigDecimal.valueOf(100))
                        .divide(lastMonth, 1, RoundingMode.HALF_UP).doubleValue()
                : null;

        SalesAnalyticsResponse.Revenue revenue = new SalesAnalyticsResponse.Revenue(
                analytics.revenueSince(PaymentStatus.COMPLETED, startOfDay),
                analytics.revenueSince(PaymentStatus.COMPLETED, startOfWeek),
                thisMonth,
                analytics.revenueSince(PaymentStatus.COMPLETED, startOfYear),
                analytics.totalRevenue(PaymentStatus.COMPLETED),
                momPct);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        long totalOrders = 0;
        for (Object[] row : analytics.countGroupedByStatus()) {
            long count = ((Number) row[1]).longValue();
            byStatus.put(((OrderStatus) row[0]).name(), count);
            totalOrders += count;
        }

        long paidOrders = analytics.countByPaymentStatus(PaymentStatus.COMPLETED);
        BigDecimal aov = paidOrders > 0
                ? analytics.totalRevenue(PaymentStatus.COMPLETED).divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        SalesAnalyticsResponse.Refunds refunds = new SalesAnalyticsResponse.Refunds(
                analytics.countByPaymentStatus(PaymentStatus.REFUNDED),
                analytics.totalRevenue(PaymentStatus.REFUNDED));

        List<SalesAnalyticsResponse.PaymentMethodRevenue> byPayment = new ArrayList<>();
        for (Object[] row : analytics.revenueByPaymentMethod(PaymentStatus.COMPLETED)) {
            byPayment.add(new SalesAnalyticsResponse.PaymentMethodRevenue(
                    String.valueOf(row[0]), ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }

        Double avgSeconds = analytics.avgSecondsToShip();
        Double avgHoursToShip = avgSeconds == null ? null
                : BigDecimal.valueOf(avgSeconds / 3600.0).setScale(1, RoundingMode.HALF_UP).doubleValue();
        SalesAnalyticsResponse.Operations operations = new SalesAnalyticsResponse.Operations(
                analytics.countByStatus(OrderStatus.PENDING),
                analytics.countByStatus(OrderStatus.PAID),
                avgHoursToShip);

        return new SalesAnalyticsResponse(revenue, totalOrders, byStatus, aov,
                analytics.totalDiscounts(PaymentStatus.COMPLETED),
                analytics.couponRedemptions(PaymentStatus.COMPLETED),
                refunds, byPayment, operations);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenueTrendPoint> getRevenueTrend(int months) {
        int window = Math.max(1, Math.min(months, 36));
        Instant from = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .minusMonths(window - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<RevenueTrendPoint> points = new ArrayList<>();
        for (Object[] row : analytics.monthlyRevenueTrend(from)) {
            points.add(new RevenueTrendPoint(String.valueOf(row[0]), (BigDecimal) row[1]));
        }
        return points;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopProductResponse> getTopProducts(int limit) {
        List<TopProductResponse> result = new ArrayList<>();
        for (Object[] row : analytics.topProducts(clamp(limit))) {
            result.add(new TopProductResponse(
                    ((Number) row[0]).longValue(), String.valueOf(row[1]), String.valueOf(row[2]),
                    ((Number) row[3]).longValue(), (BigDecimal) row[4]));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopCustomerResponse> getTopCustomers(int limit) {
        List<TopCustomerResponse> result = new ArrayList<>();
        for (Object[] row : analytics.topCustomers(clamp(limit))) {
            result.add(new TopCustomerResponse(
                    ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), (BigDecimal) row[2]));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StateOrderCountResponse> getOrdersByState() {
        List<StateOrderCountResponse> result = new ArrayList<>();
        for (Object[] row : analytics.countGroupedByShippingState(OrderStatus.CANCELLED)) {
            String state = row[0] == null ? "Other" : String.valueOf(row[0]);
            result.add(new StateOrderCountResponse(state, ((Number) row[1]).longValue()));
        }
        return result;
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
