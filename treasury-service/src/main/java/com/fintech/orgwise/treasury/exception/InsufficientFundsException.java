package com.fintech.orgwise.treasury.exception;

import java.math.BigDecimal;

/** Thrown when the treasury balance cannot cover a disbursement (HTTP 422). */
public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal available;
    private final BigDecimal requested;

    /**
     * @param available current balance
     * @param requested amount that was asked for
     */
    public InsufficientFundsException(BigDecimal available, BigDecimal requested) {
        super("Insufficient treasury funds: available " + available.toPlainString()
                + ", requested " + requested.toPlainString());
        this.available = available;
        this.requested = requested;
    }

    /** @return the balance when the debit was refused */
    public BigDecimal getAvailable() {
        return available;
    }

    /** @return the amount that was refused */
    public BigDecimal getRequested() {
        return requested;
    }
}
