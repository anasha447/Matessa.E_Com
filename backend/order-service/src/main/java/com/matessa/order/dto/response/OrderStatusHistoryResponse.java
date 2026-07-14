package com.matessa.order.dto.response;

import com.matessa.order.entity.OrderStatus;

import java.time.Instant;

public record OrderStatusHistoryResponse(
        Long id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String note,
        Long changedBy,
        Instant changedAt
) {
}
