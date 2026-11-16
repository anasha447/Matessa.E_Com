package com.matessa.order.exception;

/**
 * An idempotency-key answer with its own status and machine-readable code, so the storefront
 * can tell "check your orders" situations apart from ordinary errors.
 */
public class IdempotencyException extends RuntimeException {

    private final int status;
    private final String code;

    public IdempotencyException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
