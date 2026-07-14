package com.matessa.commercial.dto.response;

public record TopRatedProductResponse(
        Long productId,
        String productName,
        double averageRating,
        long reviewCount
) {
}
