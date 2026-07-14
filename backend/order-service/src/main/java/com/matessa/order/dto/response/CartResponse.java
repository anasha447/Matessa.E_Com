package com.matessa.order.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long cartId,
        Long userId,
        List<CartLineResponse> items,
        BigDecimal subtotal       // sum of available lines, at live prices
) {
}
