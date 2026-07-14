package com.matessa.user.controller;

import com.matessa.user.dto.response.CustomerAnalyticsResponse;
import com.matessa.user.service.CustomerAnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer/account analytics. ADMIN only — this is user data, the admin's domain
 * (managers get store sales + catalog analytics, but not customer analytics).
 */
@RestController
@RequestMapping("/api/analytics/customers")
@PreAuthorize("hasRole('ADMIN')")
public class CustomerAnalyticsController {

    private final CustomerAnalyticsService customerAnalyticsService;

    public CustomerAnalyticsController(CustomerAnalyticsService customerAnalyticsService) {
        this.customerAnalyticsService = customerAnalyticsService;
    }

    @GetMapping
    public CustomerAnalyticsResponse customers() {
        return customerAnalyticsService.getCustomerAnalytics();
    }
}
