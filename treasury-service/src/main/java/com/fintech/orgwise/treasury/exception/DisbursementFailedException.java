package com.fintech.orgwise.treasury.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown after the disbursement saga was compensated: the balance has been restored and the
 * transfer is {@code FAILED}. Carries the transfer reference so the UI can show it.
 */
public class DisbursementFailedException extends RuntimeException {

    private final String reference;
    private final HttpStatus status;

    /**
     * @param message   explanation for the client
     * @param reference reference of the FAILED transfer
     * @param status    HTTP status to return (503 for outages, 409 for rejections)
     * @param cause     the failure that triggered compensation
     */
    public DisbursementFailedException(String message, String reference, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.reference = reference;
        this.status = status;
    }

    /** @return reference of the compensated transfer */
    public String getReference() {
        return reference;
    }

    /** @return HTTP status to send back */
    public HttpStatus getStatus() {
        return status;
    }
}
