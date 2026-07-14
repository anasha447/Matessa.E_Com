package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Create/update payload for a product image. {@code variantId} is optional — set it to
 * scope the image to a specific variant (e.g. the red colourway), or leave it null for a
 * general product photo. The owning product comes from the path, not the body.
 */
public record ProductImageRequest(
        Long variantId,
        @NotBlank String url,
        String altText,
        int displayOrder,
        boolean primary
) {
}
