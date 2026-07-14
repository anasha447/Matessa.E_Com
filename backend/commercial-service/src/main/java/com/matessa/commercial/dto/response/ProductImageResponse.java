package com.matessa.commercial.dto.response;

public record ProductImageResponse(
        Long id,
        Long productId,
        Long variantId,
        String url,
        String altText,
        int displayOrder,
        boolean primary
) {
}
