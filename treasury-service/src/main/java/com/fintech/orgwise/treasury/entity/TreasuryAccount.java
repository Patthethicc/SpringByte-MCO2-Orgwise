package com.fintech.orgwise.treasury.entity;

import com.fintech.orgwise.treasury.exception.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root holding the organisation's treasury balance.
 *
 * <p>All balance changes go through {@link #debit(BigDecimal)} and {@link #credit(BigDecimal)},
 * which enforce the invariant that the balance never becomes negative. Callers must load the
 * row with a {@code PESSIMISTIC_WRITE} lock (see
 * {@link com.fintech.orgwise.treasury.repository.TreasuryRepository#findByIdForUpdate(Long)})
 * so concurrent disbursements are serialised.</p>
 */
@Entity
@Table(name = "treasury_accounts")
public class TreasuryAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected TreasuryAccount() {
    }

    /**
     * Creates a new treasury account.
     *
     * @param name           display name, e.g. "Main Treasury"
     * @param openingBalance starting balance, must be zero or positive
     * @param currency       ISO-4217 currency code, e.g. "PHP"
     * @throws IllegalArgumentException if any argument is missing or the balance is negative
     */
    public TreasuryAccount(String name, BigDecimal openingBalance, String currency) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Treasury account name is required");
        }
        if (openingBalance == null || openingBalance.signum() < 0) {
            throw new IllegalArgumentException("Opening balance must be zero or positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        this.name = name;
        this.balance = openingBalance;
        this.currency = currency;
        this.updatedAt = Instant.now();
    }

    /**
     * Removes money from the treasury.
     *
     * @param amount strictly positive amount to remove
     * @throws IllegalArgumentException   if the amount is null, zero or negative
     * @throws InsufficientFundsException if the balance is lower than the amount
     */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(balance, amount);
        }
        balance = balance.subtract(amount);
    }

    /**
     * Adds money to the treasury (used by the compensating transaction).
     *
     * @param amount strictly positive amount to add
     * @throws IllegalArgumentException if the amount is null, zero or negative
     */
    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
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

    /** @return display name of the account */
    public String getName() {
        return name;
    }

    /** @return current balance */
    public BigDecimal getBalance() {
        return balance;
    }

    /** @return ISO-4217 currency code */
    public String getCurrency() {
        return currency;
    }

    /** @return time of the last change */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TreasuryAccount other)) {
            return false;
        }
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
