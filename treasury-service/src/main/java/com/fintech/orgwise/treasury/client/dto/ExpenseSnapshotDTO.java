package com.fintech.orgwise.treasury.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * What treasury needs to know about an expense, as returned by expense-service's internal API
 * ({@code GET /api/v1/internal/expenses/{id}}). Unknown fields are ignored so expense-service
 * can add fields without breaking treasury.
 *
 * @param id                expense ID
 * @param title             expense title
 * @param amount            requested amount
 * @param status            expense status, e.g. {@code APPROVED} or {@code DISBURSED}
 * @param requesterId       officer ID of the requester
 * @param requesterName     requester's display name
 * @param eventId           event the expense is charged to, may be {@code null}
 * @param transferReference reference of the transfer that disbursed it, if {@code DISBURSED}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpenseSnapshotDTO(Long id, String title, BigDecimal amount, String status, Long requesterId,
                                 String requesterName, Long eventId, String transferReference) {

    /** Expense status that may be disbursed. */
    public static final String STATUS_APPROVED = "APPROVED";
    /** Expense status after a successful disbursement. */
    public static final String STATUS_DISBURSED = "DISBURSED";

    /** @return {@code true} if the expense is ready to be paid */
    public boolean isApproved() {
        return STATUS_APPROVED.equalsIgnoreCase(status);
    }

    /**
     * @param reference a transfer reference
     * @return {@code true} if the expense is {@code DISBURSED} by exactly that transfer
     */
    public boolean isDisbursedBy(String reference) {
        return STATUS_DISBURSED.equalsIgnoreCase(status) && reference != null && reference.equals(transferReference);
    }
}
