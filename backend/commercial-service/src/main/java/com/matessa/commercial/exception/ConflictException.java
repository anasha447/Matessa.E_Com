package com.matessa.commercial.exception;

/** A uniqueness or state conflict (e.g. duplicate slug/SKU). Maps to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
