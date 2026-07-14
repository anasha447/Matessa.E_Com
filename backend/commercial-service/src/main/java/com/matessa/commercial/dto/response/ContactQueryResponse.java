package com.matessa.commercial.dto.response;

import com.matessa.commercial.entity.ContactQueryStatus;

import java.time.Instant;

public record ContactQueryResponse(
        Long id,
        String email,
        String queryType,
        String message,
        ContactQueryStatus status,
        Instant createdAt
) {
}
