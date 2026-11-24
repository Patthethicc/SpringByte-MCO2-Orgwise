package com.fintech.orgwise.treasury.exception;

/**
 * Thrown when a downstream service (expense-service) is unreachable or its circuit is open.
 * Money-moving operations fail closed with HTTP 503 and leave the balance unchanged.
 *
 * <p>Mirrors {@code com.fintech.orgwise.common.exception.ServiceUnavailableException}; switch to
 * the shared class once {@code orgwise-common} is published.</p>
 */
public class ServiceUnavailableException extends RuntimeException {

    /**
     * @param message human-readable message shown to the client
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * @param message human-readable message shown to the client
     * @param cause   the underlying failure
     */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
