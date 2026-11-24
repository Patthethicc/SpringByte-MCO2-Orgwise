package com.fintech.orgwise.treasury.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/v1/transfers}: which approved expense to pay out.
 *
 * @param expenseId ID of an {@code APPROVED} expense in expense-service
 */
public record DisburseRequestDTO(
        @NotNull(message = "expenseId is required")
        @Positive(message = "expenseId must be a positive number")
        Long expenseId) {
}
