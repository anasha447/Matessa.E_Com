package com.matessa.commercial.dto.response;

/** Aggregate rating for a product — handy for catalog listings and product pages. */
public record ReviewSummaryResponse(
        Long productId,
        double averageRating,
        long reviewCount
) {
}
