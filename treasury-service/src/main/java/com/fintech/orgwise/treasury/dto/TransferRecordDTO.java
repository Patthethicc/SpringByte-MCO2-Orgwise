package com.fintech.orgwise.treasury.dto;

import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public view of one ledger row.
 *
 * @param id                  database ID
 * @param reference           saga / idempotency reference
 * @param expenseId           paid expense
 * @param eventId             event the expense belongs to
 * @param expenseTitle        snapshot of the expense title
 * @param amount              amount paid
 * @param recipientId         officer who was paid
 * @param recipientName       snapshot of the recipient's name
 * @param disbursedById       finance head who disbursed
 * @param disbursedByUsername finance head's username
 * @param status              saga status
 * @param failureReason       why it failed, if it did
 * @param revertPending       whether an expense-side revert is still unconfirmed
 * @param createdAt           creation time
 * @param completedAt         time the saga finished
 */
public record TransferRecordDTO(Long id, String reference, Long expenseId, Long eventId, String expenseTitle,
                                BigDecimal amount, Long recipientId, String recipientName, Long disbursedById,
                                String disbursedByUsername, TransferStatus status, String failureReason,
                                boolean revertPending, Instant createdAt, Instant completedAt) {

    /**
     * @param t entity to convert
     * @return the DTO
     */
    public static TransferRecordDTO from(TransferRecord t) {
        return new TransferRecordDTO(t.getId(), t.getReference(), t.getExpenseId(), t.getEventId(),
                t.getExpenseTitle(), t.getAmount(), t.getRecipientId(), t.getRecipientName(), t.getDisbursedById(),
                t.getDisbursedByUsername(), t.getStatus(), t.getFailureReason(), t.isRevertPending(),
                t.getCreatedAt(), t.getCompletedAt());
    }
}
