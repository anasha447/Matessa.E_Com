package com.matessa.order.dto.response;

import java.math.BigDecimal;

public record TopProductResponse(
        Long productVariantId,
        String sku,
        String productName,
        long unitsSold,
        BigDecimal revenue
) {
}
