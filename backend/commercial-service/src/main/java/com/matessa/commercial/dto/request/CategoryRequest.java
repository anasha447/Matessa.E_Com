package com.matessa.commercial.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        Long parentId,                 // null = main (top-level) category
        @NotBlank String name,
        @NotBlank String slug,
        String description,
        String imageUrl,
        int displayOrder
) {
}
