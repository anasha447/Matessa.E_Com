package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.NotNull;

public record WishlistItemRequest(
        @NotNull Long productVariantId
) {
}
