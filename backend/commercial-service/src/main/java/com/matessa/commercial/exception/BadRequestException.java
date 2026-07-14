package com.matessa.commercial.exception;

/** A semantically invalid request (e.g. parent category does not exist). Maps to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
