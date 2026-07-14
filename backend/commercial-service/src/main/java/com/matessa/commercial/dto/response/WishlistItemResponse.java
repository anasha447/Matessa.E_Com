package com.matessa.commercial.dto.response;

import java.time.Instant;

public record WishlistItemResponse(
        Long id,
        Long productVariantId,
        Instant addedAt
) {
}
