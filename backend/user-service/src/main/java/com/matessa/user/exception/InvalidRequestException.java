package com.matessa.user.exception;

/**
 * A request whose values are rejected by business validation beyond bean constraints
 * (e.g. a wrong current password on change-password). Maps to HTTP 400 Bad Request.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
