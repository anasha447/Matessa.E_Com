package com.matessa.order.dto.response;

import java.math.BigDecimal;

/** A top customer by spend. Name resolution (via user-service) is left to the dashboard. */
public record TopCustomerResponse(
        Long userId,
        long orders,
        BigDecimal totalSpent
) {
}
