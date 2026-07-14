package com.matessa.order.service;

import com.matessa.order.dto.response.RevenueTrendPoint;
import com.matessa.order.dto.response.SalesAnalyticsResponse;
import com.matessa.order.dto.response.StateOrderCountResponse;
import com.matessa.order.dto.response.TopCustomerResponse;
import com.matessa.order.dto.response.TopProductResponse;

import java.util.List;

public interface SalesAnalyticsService {

    SalesAnalyticsResponse getSalesAnalytics();

    List<RevenueTrendPoint> getRevenueTrend(int months);

    List<TopProductResponse> getTopProducts(int limit);

    List<TopCustomerResponse> getTopCustomers(int limit);

    List<StateOrderCountResponse> getOrdersByState();
}
