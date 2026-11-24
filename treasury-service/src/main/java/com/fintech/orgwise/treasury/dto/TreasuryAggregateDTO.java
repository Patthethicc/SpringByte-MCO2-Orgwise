package com.fintech.orgwise.treasury.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read model served to reporting-service over {@code GET /api/v1/internal/treasury/aggregate}.
 *
 * @param balance            current treasury balance
 * @param currency           ISO-4217 code
 * @param totalDisbursed     sum of COMPLETED transfers
 * @param completedTransfers number of COMPLETED transfers
 * @param pendingTransfers   number of PENDING transfers
 * @param failedTransfers    number of FAILED transfers
 * @param byEvent            completed totals per event
 * @param generatedAt        when the aggregate was computed
 */
public record TreasuryAggregateDTO(BigDecimal balance, String currency, BigDecimal totalDisbursed,
                                   long completedTransfers, long pendingTransfers, long failedTransfers,
                                   List<EventDisbursementDTO> byEvent, Instant generatedAt) {
}
