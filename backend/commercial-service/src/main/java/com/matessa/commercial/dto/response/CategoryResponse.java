package com.matessa.commercial.dto.response;

import java.util.List;

public record CategoryResponse(
        Long id,
        Long parentId,
        String name,
        String slug,
        String description,
        String imageUrl,
        int displayOrder,
        boolean active,
        List<CategoryResponse> children   // populated by the tree endpoint; empty for flat responses
) {
}
