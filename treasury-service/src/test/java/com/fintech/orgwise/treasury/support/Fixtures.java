package com.fintech.orgwise.treasury.support;

import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.security.JwtPrincipal;

import java.math.BigDecimal;

/** Shared test data. */
public final class Fixtures {

    /** Finance head principal (officer 2). */
    public static final JwtPrincipal FINANCE = new JwtPrincipal(2L, "finance", "FINANCE_HEAD");
    /** President principal (officer 4). */
    public static final JwtPrincipal PRESIDENT = new JwtPrincipal(4L, "president", "PRESIDENT");
    /** Associate principal (officer 7). */
    public static final JwtPrincipal ASSOCIATE = new JwtPrincipal(7L, "assoc", "ASSOCIATE");

    private Fixtures() {
    }

    /**
     * @param id     expense ID
     * @param amount amount
     * @param status expense status
     * @return a snapshot requested by officer 7 for event 3
     */
    public static ExpenseSnapshotDTO expense(long id, String amount, String status) {
        return new ExpenseSnapshotDTO(id, "Expense " + id, amount == null ? null : new BigDecimal(amount), status,
                7L, "Assoc Member", 3L, null);
    }

    /**
     * @param expenseId expense ID
     * @param amount    amount
     * @return a new PENDING transfer paid to officer 7
     */
    public static TransferRecord pendingTransfer(long expenseId, String amount) {
        return TransferRecord.builder().expenseId(expenseId).eventId(3L).expenseTitle("Expense " + expenseId)
                .amount(new BigDecimal(amount)).recipientId(7L).recipientName("Assoc Member")
                .disbursedById(2L).disbursedByUsername("finance").build();
    }
}
