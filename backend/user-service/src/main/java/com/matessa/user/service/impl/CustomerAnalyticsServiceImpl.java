package com.matessa.user.service.impl;

import com.matessa.user.dto.response.CustomerAnalyticsResponse;
import com.matessa.user.entity.Role;
import com.matessa.user.repository.UserAnalyticsRepository;
import com.matessa.user.service.CustomerAnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CustomerAnalyticsServiceImpl implements CustomerAnalyticsService {

    private final UserAnalyticsRepository analytics;

    public CustomerAnalyticsServiceImpl(UserAnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerAnalyticsResponse getCustomerAnalytics() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfYear = today.withDayOfYear(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Object[] row : analytics.countGroupedByRole()) {
            byRole.put(((Role) row[0]).name(), ((Number) row[1]).longValue());
        }

        return new CustomerAnalyticsResponse(
                analytics.totalUsers(),
                analytics.countByRole(Role.CUSTOMER),
                analytics.newSince(startOfMonth),
                analytics.newSince(startOfYear),
                byRole);
    }
}
