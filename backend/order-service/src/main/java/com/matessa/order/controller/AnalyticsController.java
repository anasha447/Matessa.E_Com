package com.matessa.order.controller;

import com.matessa.order.dto.response.RevenueTrendPoint;
import com.matessa.order.dto.response.SalesAnalyticsResponse;
import com.matessa.order.dto.response.StateOrderCountResponse;
import com.matessa.order.dto.response.TopCustomerResponse;
import com.matessa.order.dto.response.TopProductResponse;
import com.matessa.order.service.SalesAnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sales analytics for the store dashboard. MANAGER and ADMIN only (operational store data).
 */
@RestController
@RequestMapping("/api/analytics/sales")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class AnalyticsController {

    private final SalesAnalyticsService salesAnalyticsService;

    public AnalyticsController(SalesAnalyticsService salesAnalyticsService) {
        this.salesAnalyticsService = salesAnalyticsService;
    }

    @GetMapping
    public SalesAnalyticsResponse sales() {
        return salesAnalyticsService.getSalesAnalytics();
    }

    @GetMapping("/revenue-trend")
    public List<RevenueTrendPoint> revenueTrend(@RequestParam(defaultValue = "12") int months) {
        return salesAnalyticsService.getRevenueTrend(months);
    }

    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(@RequestParam(defaultValue = "10") int limit) {
        return salesAnalyticsService.getTopProducts(limit);
    }

    @GetMapping("/top-customers")
    public List<TopCustomerResponse> topCustomers(@RequestParam(defaultValue = "10") int limit) {
        return salesAnalyticsService.getTopCustomers(limit);
    }

    @GetMapping("/orders-by-state")
    public List<StateOrderCountResponse> ordersByState() {
        return salesAnalyticsService.getOrdersByState();
    }
}
