package com.matessa.order.dto.response;

import java.math.BigDecimal;

/** One month of the revenue trend, e.g. {"2026-06", 4820.00}. */
public record RevenueTrendPoint(String month, BigDecimal revenue) {
}
