package com.matessa.commercial.service;

import com.matessa.commercial.dto.response.CatalogAnalyticsResponse;
import com.matessa.commercial.dto.response.LowStockItemResponse;
import com.matessa.commercial.dto.response.MostWishlistedResponse;
import com.matessa.commercial.dto.response.TopRatedProductResponse;

import java.util.List;

public interface CatalogAnalyticsService {

    CatalogAnalyticsResponse getCatalogAnalytics();

    List<LowStockItemResponse> getLowStock(int limit);

    List<TopRatedProductResponse> getTopRated(int limit);

    List<MostWishlistedResponse> getMostWishlisted(int limit);
}
