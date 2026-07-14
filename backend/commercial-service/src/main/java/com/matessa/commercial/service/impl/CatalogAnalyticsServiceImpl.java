package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.response.CatalogAnalyticsResponse;
import com.matessa.commercial.dto.response.LowStockItemResponse;
import com.matessa.commercial.dto.response.MostWishlistedResponse;
import com.matessa.commercial.dto.response.TopRatedProductResponse;
import com.matessa.commercial.repository.CatalogAnalyticsRepository;
import com.matessa.commercial.service.CatalogAnalyticsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class CatalogAnalyticsServiceImpl implements CatalogAnalyticsService {

    private final CatalogAnalyticsRepository analytics;

    public CatalogAnalyticsServiceImpl(CatalogAnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogAnalyticsResponse getCatalogAnalytics() {
        return new CatalogAnalyticsResponse(
                new CatalogAnalyticsResponse.Counts(
                        analytics.activeProductCount(), analytics.activeVariantCount(), analytics.activeCategoryCount()),
                new CatalogAnalyticsResponse.Inventory(
                        analytics.totalInventoryValue().setScale(2, RoundingMode.HALF_UP),
                        analytics.lowStockCount(), analytics.outOfStockCount()),
                analytics.reviewCount(),
                new CatalogAnalyticsResponse.Promotions(
                        analytics.activeCouponCount(), analytics.activeDiscountCount(Instant.now())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LowStockItemResponse> getLowStock(int limit) {
        return analytics.lowStockItems(PageRequest.of(0, clamp(limit))).stream()
                .map(r -> new LowStockItemResponse(((Number) r[0]).longValue(), String.valueOf(r[1]),
                        ((Number) r[2]).intValue(), ((Number) r[3]).intValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopRatedProductResponse> getTopRated(int limit) {
        return analytics.topRatedProducts(PageRequest.of(0, clamp(limit))).stream()
                .map(r -> new TopRatedProductResponse(((Number) r[0]).longValue(), String.valueOf(r[1]),
                        round(((Number) r[2]).doubleValue()), ((Number) r[3]).longValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MostWishlistedResponse> getMostWishlisted(int limit) {
        return analytics.mostWishlisted(PageRequest.of(0, clamp(limit))).stream()
                .map(r -> new MostWishlistedResponse(((Number) r[0]).longValue(), String.valueOf(r[1]),
                        ((Number) r[2]).longValue()))
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
