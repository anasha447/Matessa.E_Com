package com.matessa.commercial.dto.response;

import java.math.BigDecimal;

/** Catalog & inventory KPIs for the admin/manager dashboard. */
public record CatalogAnalyticsResponse(
        Counts counts,
        Inventory inventory,
        long totalReviews,
        Promotions promotions
) {
    public record Counts(long products, long variants, long categories) {
    }

    public record Inventory(BigDecimal totalValue, long lowStock, long outOfStock) {
    }

    public record Promotions(long activeCoupons, long activeDiscounts) {
    }
}
