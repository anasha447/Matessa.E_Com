package com.matessa.user.exception;

/**
 * A request that is well-formed but violates a domain rule (e.g. an admin trying to
 * disable their own account). Maps to HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
