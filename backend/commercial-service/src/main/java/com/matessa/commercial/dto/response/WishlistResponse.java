package com.matessa.commercial.dto.response;

import java.util.List;

public record WishlistResponse(
        Long userId,
        List<WishlistItemResponse> items
) {
}
