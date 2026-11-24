package com.fintech.orgwise.treasury.repository;

import com.fintech.orgwise.treasury.entity.TreasuryAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence for the {@link TreasuryAccount} aggregate. */
public interface TreasuryRepository extends JpaRepository<TreasuryAccount, Long> {

    /**
     * Finds the main treasury account (the first one created).
     *
     * @return the main account, if it was bootstrapped
     */
    Optional<TreasuryAccount> findFirstByOrderByIdAsc();

    /**
     * Loads an account with a {@code PESSIMISTIC_WRITE} row lock ({@code SELECT ... FOR UPDATE}),
     * held until the surrounding transaction ends. Every balance change uses this method so
     * concurrent disbursements are serialised and cannot overspend.
     *
     * @param id account ID
     * @return the locked account
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TreasuryAccount a where a.id = :id")
    Optional<TreasuryAccount> findByIdForUpdate(@Param("id") Long id);
}
