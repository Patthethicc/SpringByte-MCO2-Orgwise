package com.fintech.orgwise.treasury.repository;

import com.fintech.orgwise.treasury.dto.EventDisbursementDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence for the append-only {@link TransferRecord} ledger. */
public interface TransferRepository extends JpaRepository<TransferRecord, Long> {

    /**
     * @param reference transfer reference
     * @return the transfer, if any
     */
    Optional<TransferRecord> findByReference(String reference);

    /**
     * Loads a transfer with a row lock so saga steps (complete, compensate) for the same
     * transfer cannot run at the same time.
     *
     * @param reference transfer reference
     * @return the locked transfer
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferRecord t where t.reference = :reference")
    Optional<TransferRecord> findByReferenceForUpdate(@Param("reference") String reference);

    /**
     * Checks for a transfer that blocks a new disbursement (the "no double disbursement" rule).
     *
     * @param expenseId expense ID
     * @param statuses  statuses that count as active
     * @return {@code true} if one exists
     */
    boolean existsByExpenseIdAndStatusIn(Long expenseId, Collection<TransferStatus> statuses);

    /**
     * @param status   status filter
     * @param pageable page request
     * @return transfers in that status
     */
    Page<TransferRecord> findByStatus(TransferStatus status, Pageable pageable);

    /**
     * @param recipientId officer ID of the person who was paid
     * @param pageable    page request
     * @return that officer's transfers
     */
    Page<TransferRecord> findByRecipientId(Long recipientId, Pageable pageable);

    /**
     * Finds transfers stuck in a status since before a cut-off (used by the reconciler).
     *
     * @param status status to look for
     * @param cutoff only rows created before this instant
     * @return matching transfers, oldest first
     */
    List<TransferRecord> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TransferStatus status, Instant cutoff);

    /**
     * @param status usually {@link TransferStatus#FAILED}
     * @return transfers whose expense-side revert is still unconfirmed
     */
    List<TransferRecord> findByStatusAndRevertPendingTrue(TransferStatus status);

    /**
     * @param expenseIds candidate expense IDs
     * @param statuses   statuses that count as active
     * @return the subset of IDs that already have an active transfer
     */
    @Query("select distinct t.expenseId from TransferRecord t where t.expenseId in :expenseIds and t.status in :statuses")
    List<Long> findExpenseIdsWithStatusIn(@Param("expenseIds") Collection<Long> expenseIds,
                                          @Param("statuses") Collection<TransferStatus> statuses);

    /**
     * @param status status to sum
     * @return total amount of transfers in that status (zero if none)
     */
    @Query("select coalesce(sum(t.amount), 0) from TransferRecord t where t.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") TransferStatus status);

    /**
     * @param status status to count
     * @return number of transfers in that status
     */
    long countByStatus(TransferStatus status);

    /**
     * @return completed totals grouped by event, used by reporting-service
     */
    @Query("select new com.fintech.orgwise.treasury.dto.EventDisbursementDTO(t.eventId, sum(t.amount), count(t)) "
            + "from TransferRecord t where t.status = com.fintech.orgwise.treasury.entity.TransferStatus.COMPLETED "
            + "and t.eventId is not null group by t.eventId order by t.eventId")
    List<EventDisbursementDTO> sumCompletedByEvent();
}
