package com.fintech.orgwise.treasury.exception;

/**
 * Thrown when a request is well-formed but breaks a treasury business rule, for example
 * disbursing an expense that is not {@code APPROVED} or was already paid (HTTP 409).
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * @param message human-readable description of the broken rule
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
