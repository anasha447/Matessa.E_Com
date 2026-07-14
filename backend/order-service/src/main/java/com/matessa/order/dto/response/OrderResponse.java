package com.matessa.order.dto.response;

import com.matessa.order.entity.OrderStatus;
import com.matessa.order.entity.PaymentMethod;
import com.matessa.order.entity.PaymentStatus;
import com.matessa.order.entity.ShippingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        Long userId,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        String couponCode,
        String shippingAddress,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        String transactionId,
        ShippingStatus shippingStatus,
        String shippingMethod,
        String trackingNumber,
        List<OrderItemResponse> items,
        Instant createdAt
) {
}
