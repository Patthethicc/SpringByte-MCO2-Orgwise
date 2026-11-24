package com.fintech.orgwise.treasury.entity;

/**
 * Lifecycle of a {@link TransferRecord} inside the disbursement saga.
 *
 * <ul>
 *   <li>{@link #PENDING} - funds were debited locally, expense-service has not confirmed yet.</li>
 *   <li>{@link #COMPLETED} - expense-service marked the expense {@code DISBURSED}; final.</li>
 *   <li>{@link #FAILED} - the compensating transaction refunded the balance; final.</li>
 * </ul>
 */
public enum TransferStatus {
    /** Funds are held: the local debit committed but the remote step is still outstanding. */
    PENDING,
    /** Both saga steps succeeded. */
    COMPLETED,
    /** The saga was compensated and the funds were returned to the treasury. */
    FAILED;

    /**
     * Tells whether a transfer in this status blocks a new disbursement of the same expense.
     *
     * @return {@code true} for {@link #PENDING} and {@link #COMPLETED}
     */
    public boolean isActive() {
        return this == PENDING || this == COMPLETED;
    }
}
