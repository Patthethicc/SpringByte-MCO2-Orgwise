package com.fintech.orgwise.treasury.client.dto;

import java.math.BigDecimal;

/**
 * Body sent to expense-service's {@code disburse} and {@code revert-disburse} internal
 * endpoints. {@code transferReference} is the idempotency key: repeating a call with the same
 * reference must not change the expense twice.
 *
 * @param transferReference   treasury transfer reference
 * @param amount              amount moved
 * @param disbursedById       finance head's officer ID (may be {@code null} for system calls)
 * @param disbursedByUsername finance head's username, or {@code treasury-service} for system calls
 */
public record DisbursementCommandDTO(String transferReference, BigDecimal amount, Long disbursedById,
                                     String disbursedByUsername) {
}
