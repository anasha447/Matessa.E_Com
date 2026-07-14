package com.matessa.commercial.controller;

import com.matessa.commercial.dto.response.CatalogAnalyticsResponse;
import com.matessa.commercial.dto.response.LowStockItemResponse;
import com.matessa.commercial.dto.response.MostWishlistedResponse;
import com.matessa.commercial.dto.response.TopRatedProductResponse;
import com.matessa.commercial.service.CatalogAnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalog & inventory analytics for the store dashboard. MANAGER and ADMIN only.
 */
@RestController
@RequestMapping("/api/analytics/catalog")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class CatalogAnalyticsController {

    private final CatalogAnalyticsService catalogAnalyticsService;

    public CatalogAnalyticsController(CatalogAnalyticsService catalogAnalyticsService) {
        this.catalogAnalyticsService = catalogAnalyticsService;
    }

    @GetMapping
    public CatalogAnalyticsResponse catalog() {
        return catalogAnalyticsService.getCatalogAnalytics();
    }

    @GetMapping("/low-stock")
    public List<LowStockItemResponse> lowStock(@RequestParam(defaultValue = "20") int limit) {
        return catalogAnalyticsService.getLowStock(limit);
    }

    @GetMapping("/top-rated")
    public List<TopRatedProductResponse> topRated(@RequestParam(defaultValue = "10") int limit) {
        return catalogAnalyticsService.getTopRated(limit);
    }

    @GetMapping("/most-wishlisted")
    public List<MostWishlistedResponse> mostWishlisted(@RequestParam(defaultValue = "10") int limit) {
        return catalogAnalyticsService.getMostWishlisted(limit);
    }
}
