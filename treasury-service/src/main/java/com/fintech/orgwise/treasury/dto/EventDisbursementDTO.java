package com.fintech.orgwise.treasury.dto;

import java.math.BigDecimal;

/**
 * Completed disbursements for one event (part of {@link TreasuryAggregateDTO}).
 *
 * @param eventId       event ID in event-service
 * @param totalDisbursed sum of completed transfers
 * @param transferCount number of completed transfers
 */
public record EventDisbursementDTO(Long eventId, BigDecimal totalDisbursed, long transferCount) {
}
