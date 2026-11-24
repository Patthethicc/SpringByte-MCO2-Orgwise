package com.fintech.orgwise.treasury.dto;

import com.fintech.orgwise.treasury.entity.TreasuryAccount;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public view of the treasury balance.
 *
 * @param id        account ID
 * @param name      display name
 * @param balance   current balance
 * @param currency  ISO-4217 code
 * @param updatedAt last change
 */
public record TreasuryAccountDTO(Long id, String name, BigDecimal balance, String currency, Instant updatedAt) {

    /**
     * @param account entity to convert
     * @return the DTO
     */
    public static TreasuryAccountDTO from(TreasuryAccount account) {
        return new TreasuryAccountDTO(account.getId(), account.getName(), account.getBalance(),
                account.getCurrency(), account.getUpdatedAt());
    }
}
