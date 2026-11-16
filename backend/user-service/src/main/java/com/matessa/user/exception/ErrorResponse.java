package com.matessa.user.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical API error shape for Adventure Dose. Reused (copied) across all services
 * so every service returns errors in the same structure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors,
        /** Machine-readable reason when a client must react to it (e.g. IDEMPOTENCY_KEY_REUSED). */
        String code
) {
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path,
                         Map<String, String> fieldErrors) {
        this(timestamp, status, error, message, path, fieldErrors, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null, null);
    }
}
