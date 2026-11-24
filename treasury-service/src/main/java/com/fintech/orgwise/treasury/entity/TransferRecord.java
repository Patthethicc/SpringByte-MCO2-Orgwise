package com.fintech.orgwise.treasury.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of the append-only treasury ledger: a single attempt to pay out one expense.
 *
 * <p>Cross-context data is held as ID references plus snapshot fields (RFC-001 section 4):
 * {@code expenseId}, {@code eventId} and {@code recipientId} point at other services, while
 * {@code recipientName} and {@code expenseTitle} are copied at disbursement time so the
 * ledger stays readable even when those services are down.</p>
 *
 * <p>Rows are never deleted. A failed attempt stays as {@link TransferStatus#FAILED}, and a
 * retry creates a new row with a new {@link #getReference() reference}.</p>
 */
@Entity
@Table(name = "transfer_records", indexes = {
        @Index(name = "idx_transfer_expense", columnList = "expenseId"),
        @Index(name = "idx_transfer_recipient", columnList = "recipientId"),
        @Index(name = "idx_transfer_status", columnList = "status")
})
public class TransferRecord {

    /** Longest failure reason kept on the row. */
    public static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String reference;

    @Column(nullable = false, updatable = false)
    private Long expenseId;

    @Column(updatable = false)
    private Long eventId;

    @Column(length = 200, updatable = false)
    private String expenseTitle;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(updatable = false)
    private Long recipientId;

    @Column(length = 100, updatable = false)
    private String recipientName;

    @Column(updatable = false)
    private Long disbursedById;

    @Column(nullable = false, length = 100, updatable = false)
    private String disbursedByUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(length = MAX_REASON_LENGTH)
    private String failureReason;

    /** True when compensation could not confirm that expense-service reverted its side. */
    @Column(nullable = false)
    private boolean revertPending;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    /** Required by JPA. */
    protected TransferRecord() {
    }

    private TransferRecord(Builder b) {
        if (b.expenseId == null) {
            throw new IllegalArgumentException("expenseId is required");
        }
        if (b.amount == null || b.amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        if (b.disbursedByUsername == null || b.disbursedByUsername.isBlank()) {
            throw new IllegalArgumentException("disbursedByUsername is required");
        }
        this.reference = UUID.randomUUID().toString();
        this.expenseId = b.expenseId;
        this.eventId = b.eventId;
        this.expenseTitle = b.expenseTitle;
        this.amount = b.amount;
        this.recipientId = b.recipientId;
        this.recipientName = b.recipientName;
        this.disbursedById = b.disbursedById;
        this.disbursedByUsername = b.disbursedByUsername;
        this.status = TransferStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Starts building a new {@link TransferStatus#PENDING} transfer.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Marks the saga as finished successfully. Calling it on a completed transfer does nothing.
     *
     * @throws IllegalStateException if the transfer already failed
     */
    public void markCompleted() {
        if (status == TransferStatus.COMPLETED) {
            return;
        }
        if (status != TransferStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING transfer can be completed, but " + reference + " is " + status);
        }
        status = TransferStatus.COMPLETED;
        completedAt = Instant.now();
    }

    /**
     * Marks the saga as compensated. Calling it on a failed transfer does nothing.
     *
     * @param reason        why the transfer failed (truncated to {@value #MAX_REASON_LENGTH} chars)
     * @param revertPending {@code true} if expense-service still has to confirm its revert
     * @throws IllegalStateException if the transfer already completed
     */
    public void markFailed(String reason, boolean revertPending) {
        if (status == TransferStatus.FAILED) {
            return;
        }
        if (status != TransferStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING transfer can fail, but " + reference + " is " + status);
        }
        status = TransferStatus.FAILED;
        failureReason = truncate(reason);
        this.revertPending = revertPending;
        completedAt = Instant.now();
    }

    /**
     * Records that expense-service confirmed the revert of a failed transfer.
     *
     * @throws IllegalStateException if the transfer is not {@link TransferStatus#FAILED}
     */
    public void confirmRevert() {
        if (status != TransferStatus.FAILED) {
            throw new IllegalStateException("Only a FAILED transfer can have its revert confirmed");
        }
        revertPending = false;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Unknown failure";
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** @return database identifier */
    public Long getId() {
        return id;
    }

    /** @return unique, idempotency key shared with expense-service */
    public String getReference() {
        return reference;
    }

    /** @return ID of the expense in expense-service */
    public Long getExpenseId() {
        return expenseId;
    }

    /** @return ID of the event in event-service, or {@code null} */
    public Long getEventId() {
        return eventId;
    }

    /** @return snapshot of the expense title */
    public String getExpenseTitle() {
        return expenseTitle;
    }

    /** @return amount paid out */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @return officer ID of the requester being paid */
    public Long getRecipientId() {
        return recipientId;
    }

    /** @return snapshot of the requester's name */
    public String getRecipientName() {
        return recipientName;
    }

    /** @return officer ID of the finance head who disbursed */
    public Long getDisbursedById() {
        return disbursedById;
    }

    /** @return username of the finance head who disbursed */
    public String getDisbursedByUsername() {
        return disbursedByUsername;
    }

    /** @return current saga status */
    public TransferStatus getStatus() {
        return status;
    }

    /** @return failure reason, or {@code null} if not failed */
    public String getFailureReason() {
        return failureReason;
    }

    /** @return whether an expense-side revert still has to be confirmed */
    public boolean isRevertPending() {
        return revertPending;
    }

    /** @return creation time */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return last update time */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** @return time the saga finished (completed or failed), or {@code null} */
    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransferRecord other)) {
            return false;
        }
        return Objects.equals(reference, other.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(reference);
    }

    /** Fluent builder for new transfers; every built transfer starts as {@link TransferStatus#PENDING}. */
    public static final class Builder {
        private Long expenseId;
        private Long eventId;
        private String expenseTitle;
        private BigDecimal amount;
        private Long recipientId;
        private String recipientName;
        private Long disbursedById;
        private String disbursedByUsername;

        private Builder() {
        }

        /** @param v expense ID @return this builder */
        public Builder expenseId(Long v) {
            this.expenseId = v;
            return this;
        }

        /** @param v event ID @return this builder */
        public Builder eventId(Long v) {
            this.eventId = v;
            return this;
        }

        /** @param v expense title snapshot @return this builder */
        public Builder expenseTitle(String v) {
            this.expenseTitle = v;
            return this;
        }

        /** @param v amount @return this builder */
        public Builder amount(BigDecimal v) {
            this.amount = v;
            return this;
        }

        /** @param v recipient officer ID @return this builder */
        public Builder recipientId(Long v) {
            this.recipientId = v;
            return this;
        }

        /** @param v recipient name snapshot @return this builder */
        public Builder recipientName(String v) {
            this.recipientName = v;
            return this;
        }

        /** @param v disbursing officer ID @return this builder */
        public Builder disbursedById(Long v) {
            this.disbursedById = v;
            return this;
        }

        /** @param v disbursing officer username @return this builder */
        public Builder disbursedByUsername(String v) {
            this.disbursedByUsername = v;
            return this;
        }

        /**
         * Builds the transfer.
         *
         * @return a new PENDING transfer with a random reference
         * @throws IllegalArgumentException if required fields are missing or the amount is not positive
         */
        public TransferRecord build() {
            return new TransferRecord(this);
        }
    }
}
