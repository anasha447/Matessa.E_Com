package com.matessa.order.dto.request;

import com.matessa.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        String note
) {
}
