package com.fintech.orgwise.treasury.exception;

/** Thrown when a requested treasury, transfer or remote expense does not exist (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * @param message human-readable description of what was not found
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
