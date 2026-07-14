package com.matessa.order.exception;

/** A downstream dependency (e.g. commercial-service via Feign) is unavailable. Maps to 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
