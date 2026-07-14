package com.matessa.order.dto.response;

import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.entity.ShippingStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Compact order view for list endpoints (no line items). */
public record OrderSummaryResponse(
        Long id,
        String orderNumber,
        Long userId,
        OrderStatus status,
        BigDecimal total,
        PaymentStatus paymentStatus,
        ShippingStatus shippingStatus,
        Instant createdAt
) {
}
