package com.matessa.user.dto.response;

import java.util.Map;

/** Customer/account KPIs for the admin dashboard. */
public record CustomerAnalyticsResponse(
        long totalUsers,
        long totalCustomers,
        long newThisMonth,
        long newThisYear,
        Map<String, Long> usersByRole
) {
}
