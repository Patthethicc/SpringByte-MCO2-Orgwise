package com.fintech.orgwise.treasury.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Treasury settings under {@code orgwise.treasury.*} (served by the Config Server).
 *
 * @param accountName    name of the bootstrapped account
 * @param initialBalance opening balance used when the account is first created
 * @param currency       ISO-4217 currency
 * @param reconciliation settings of the stuck-saga reconciler
 */
@ConfigurationProperties(prefix = "orgwise.treasury")
public record TreasuryProperties(String accountName, BigDecimal initialBalance, String currency,
                                 Reconciliation reconciliation) {

    /** Applies defaults for anything not configured. */
    public TreasuryProperties {
        accountName = accountName == null || accountName.isBlank() ? "Main Treasury" : accountName;
        initialBalance = initialBalance == null ? new BigDecimal("100000.00") : initialBalance;
        currency = currency == null || currency.isBlank() ? "PHP" : currency;
        reconciliation = reconciliation == null ? new Reconciliation(true, null) : reconciliation;
    }

    /**
     * @param enabled        whether the scheduled reconciler runs
     * @param pendingTimeout how old a PENDING transfer must be before it is treated as stuck
     */
    public record Reconciliation(boolean enabled, Duration pendingTimeout) {

        /** Applies the default timeout. */
        public Reconciliation {
            pendingTimeout = pendingTimeout == null ? Duration.ofMinutes(2) : pendingTimeout;
        }
    }
}
