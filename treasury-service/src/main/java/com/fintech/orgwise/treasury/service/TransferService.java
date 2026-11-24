package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.client.ExpenseClient;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.dto.EventDisbursementDTO;
import com.fintech.orgwise.treasury.dto.PageResponseDTO;
import com.fintech.orgwise.treasury.dto.TransferRecordDTO;
import com.fintech.orgwise.treasury.dto.TreasuryAccountDTO;
import com.fintech.orgwise.treasury.dto.TreasuryAggregateDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import com.fintech.orgwise.treasury.entity.TreasuryAccount;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.repository.TransferRepository;
import com.fintech.orgwise.treasury.repository.TreasuryRepository;
import com.fintech.orgwise.treasury.security.JwtPrincipal;
import com.fintech.orgwise.treasury.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Local ACID operations on the treasury ledger, plus read queries.
 *
 * <p>Each write method is one local transaction and one step of the disbursement saga
 * ({@link DisbursementSaga} orchestrates them):</p>
 * <ol>
 *   <li>{@link #reserveFunds} - lock balance, check rules, debit, write a PENDING transfer</li>
 *   <li>{@link #completeTransfer} - mark COMPLETED after expense-service confirmed</li>
 *   <li>{@link #compensateTransfer} - refund and mark FAILED when it did not</li>
 * </ol>
 * <p>All balance changes lock the account row first ({@code SELECT ... FOR UPDATE}), so
 * disbursements are serialised and the "no double disbursement" check cannot race.</p>
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    /** Statuses that block another disbursement of the same expense. */
    static final Set<TransferStatus> ACTIVE = EnumSet.of(TransferStatus.PENDING, TransferStatus.COMPLETED);

    private final TreasuryRepository treasuryRepository;
    private final TransferRepository transferRepository;
    private final ExpenseClient expenseClient;
    private final Clock clock;

    /**
     * @param treasuryRepository account persistence
     * @param transferRepository ledger persistence
     * @param expenseClient      Feign client for expense-service
     * @param clock              time source
     */
    public TransferService(TreasuryRepository treasuryRepository, TransferRepository transferRepository,
                           ExpenseClient expenseClient, Clock clock) {
        this.treasuryRepository = treasuryRepository;
        this.transferRepository = transferRepository;
        this.expenseClient = expenseClient;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ saga steps

    /**
     * Saga step 1: debits the treasury and records a PENDING transfer, in one transaction.
     *
     * @param expense snapshot of an APPROVED expense from expense-service
     * @param actor   finance head performing the disbursement
     * @return the new PENDING transfer
     * @throws BusinessRuleException      if the expense is not approved, has no positive amount,
     *                                    or already has a pending/completed transfer
     * @throws com.fintech.orgwise.treasury.exception.InsufficientFundsException if the balance is too low
     */
    @Transactional
    public TransferRecord reserveFunds(ExpenseSnapshotDTO expense, JwtPrincipal actor) {
        if (!expense.isApproved()) {
            throw new BusinessRuleException("Expense " + expense.id() + " cannot be disbursed: status is "
                    + expense.status() + ", expected APPROVED");
        }
        if (expense.amount() == null || expense.amount().signum() <= 0) {
            throw new BusinessRuleException("Expense " + expense.id() + " has no positive amount to disburse");
        }
        TreasuryAccount account = lockMainAccount();
        if (transferRepository.existsByExpenseIdAndStatusIn(expense.id(), ACTIVE)) {
            throw new BusinessRuleException("Expense " + expense.id() + " has already been disbursed or is being disbursed");
        }
        account.debit(expense.amount());
        TransferRecord transfer = TransferRecord.builder()
                .expenseId(expense.id())
                .eventId(expense.eventId())
                .expenseTitle(expense.title())
                .amount(expense.amount())
                .recipientId(expense.requesterId())
                .recipientName(expense.requesterName())
                .disbursedById(actor.officerId())
                .disbursedByUsername(actor.username())
                .build();
        TransferRecord saved = transferRepository.save(transfer);
        log.info("Reserved {} for expense {} (transfer {}), balance now {}", expense.amount(), expense.id(),
                saved.getReference(), account.getBalance());
        return saved;
    }

    /**
     * Saga step 3a: marks a PENDING transfer COMPLETED. Idempotent.
     *
     * @param reference transfer reference
     * @return the completed transfer
     * @throws ResourceNotFoundException if no such transfer exists
     * @throws BusinessRuleException     if the transfer already FAILED
     */
    @Transactional
    public TransferRecord completeTransfer(String reference) {
        TransferRecord transfer = lockTransfer(reference);
        if (transfer.getStatus() == TransferStatus.FAILED) {
            throw new BusinessRuleException("Transfer " + reference + " already failed and cannot be completed");
        }
        transfer.markCompleted();
        return transfer;
    }

    /**
     * Saga step 3b (compensating transaction): refunds the amount and marks the transfer FAILED.
     * Idempotent: a FAILED transfer is returned unchanged and is not refunded twice.
     *
     * @param reference     transfer reference
     * @param reason        why the saga failed
     * @param revertPending {@code true} if expense-service has not confirmed its side was undone
     * @return the failed transfer
     * @throws ResourceNotFoundException if no such transfer exists
     * @throws BusinessRuleException     if the transfer is already COMPLETED
     */
    @Transactional
    public TransferRecord compensateTransfer(String reference, String reason, boolean revertPending) {
        TransferRecord transfer = lockTransfer(reference);
        if (transfer.getStatus() == TransferStatus.FAILED) {
            return transfer;
        }
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new BusinessRuleException("Transfer " + reference + " is COMPLETED and cannot be compensated");
        }
        TreasuryAccount account = lockMainAccount();
        account.credit(transfer.getAmount());
        transfer.markFailed(reason, revertPending);
        log.warn("Compensated transfer {}: refunded {}, balance now {} ({})", reference, transfer.getAmount(),
                account.getBalance(), reason);
        return transfer;
    }

    /**
     * Records that expense-service confirmed the revert of a FAILED transfer.
     *
     * @param reference transfer reference
     * @return the updated transfer
     * @throws ResourceNotFoundException if no such transfer exists
     * @throws IllegalStateException     if the transfer is not FAILED
     */
    @Transactional
    public TransferRecord confirmRevert(String reference) {
        TransferRecord transfer = lockTransfer(reference);
        transfer.confirmRevert();
        return transfer;
    }

    // ------------------------------------------------------------------ queries

    /**
     * @return the main treasury account
     * @throws ResourceNotFoundException if it was not bootstrapped
     */
    @Transactional(readOnly = true)
    public TreasuryAccountDTO getTreasuryAccount() {
        return TreasuryAccountDTO.from(mainAccount());
    }

    /**
     * Lists the whole ledger, newest first.
     *
     * @param status   optional status filter ({@code null} for all)
     * @param pageable page request
     * @return a page of transfers
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<TransferRecordDTO> getTransfers(TransferStatus status, Pageable pageable) {
        Page<TransferRecord> page = status == null
                ? transferRepository.findAll(pageable)
                : transferRepository.findByStatus(status, pageable);
        return PageResponseDTO.from(page, TransferRecordDTO::from);
    }

    /**
     * Lists the transfers paid to one officer (the Associate "Transfer History" page).
     *
     * @param recipientId officer ID
     * @param pageable    page request
     * @return a page of that officer's transfers
     * @throws IllegalArgumentException if {@code recipientId} is null
     */
    @Transactional(readOnly = true)
    public PageResponseDTO<TransferRecordDTO> getTransfersForRecipient(Long recipientId, Pageable pageable) {
        if (recipientId == null) {
            throw new IllegalArgumentException("The caller's token has no officerId claim");
        }
        return PageResponseDTO.from(transferRepository.findByRecipientId(recipientId, pageable),
                TransferRecordDTO::from);
    }

    /**
     * Gets one transfer. Finance head, president and admin see any transfer; other officers
     * only see transfers paid to them.
     *
     * @param reference transfer reference
     * @param viewer    the caller
     * @return the transfer
     * @throws ResourceNotFoundException if it does not exist
     * @throws AccessDeniedException     if the caller may not see it
     */
    @Transactional(readOnly = true)
    public TransferRecordDTO getTransfer(String reference, JwtPrincipal viewer) {
        TransferRecord transfer = transferRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer " + reference + " not found"));
        boolean privileged = viewer.hasAnyRole(Roles.FINANCE_HEAD, Roles.PRESIDENT, Roles.ADMIN);
        boolean recipient = viewer.officerId() != null && viewer.officerId().equals(transfer.getRecipientId());
        if (!privileged && !recipient) {
            throw new AccessDeniedException("You can only view transfers paid to you");
        }
        return TransferRecordDTO.from(transfer);
    }

    /**
     * Approved expenses waiting to be paid: expense-service's APPROVED list minus those that
     * already have a pending or completed transfer here. Fails closed (503) if expense-service
     * is down.
     *
     * @return expenses the finance head can disburse
     */
    @Transactional(readOnly = true)
    public List<ExpenseSnapshotDTO> getDisbursableExpenses() {
        List<ExpenseSnapshotDTO> approved = expenseClient.getExpensesByStatus(ExpenseSnapshotDTO.STATUS_APPROVED);
        if (approved == null || approved.isEmpty()) {
            return List.of();
        }
        List<Long> ids = approved.stream().map(ExpenseSnapshotDTO::id).toList();
        Set<Long> busy = new HashSet<>(transferRepository.findExpenseIdsWithStatusIn(ids, ACTIVE));
        return approved.stream().filter(e -> !busy.contains(e.id())).toList();
    }

    /**
     * Builds the read model consumed by reporting-service.
     *
     * @return balance, totals, counts and per-event totals
     */
    @Transactional(readOnly = true)
    public TreasuryAggregateDTO getAggregate() {
        TreasuryAccount account = mainAccount();
        BigDecimal total = transferRepository.sumAmountByStatus(TransferStatus.COMPLETED);
        List<EventDisbursementDTO> byEvent = transferRepository.sumCompletedByEvent();
        return new TreasuryAggregateDTO(account.getBalance(), account.getCurrency(),
                total == null ? BigDecimal.ZERO : total,
                transferRepository.countByStatus(TransferStatus.COMPLETED),
                transferRepository.countByStatus(TransferStatus.PENDING),
                transferRepository.countByStatus(TransferStatus.FAILED),
                byEvent, Instant.now(clock));
    }

    /**
     * @param olderThan minimum age
     * @return PENDING transfers created before {@code now - olderThan} (stuck sagas)
     */
    @Transactional(readOnly = true)
    public List<TransferRecord> findStalePendingTransfers(Duration olderThan) {
        return transferRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TransferStatus.PENDING,
                Instant.now(clock).minus(olderThan));
    }

    /**
     * @return FAILED transfers whose expense-side revert is still unconfirmed
     */
    @Transactional(readOnly = true)
    public List<TransferRecord> findTransfersAwaitingRevert() {
        return transferRepository.findByStatusAndRevertPendingTrue(TransferStatus.FAILED);
    }

    // ------------------------------------------------------------------ helpers

    private TreasuryAccount mainAccount() {
        return treasuryRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException("Treasury account has not been initialised"));
    }

    private TreasuryAccount lockMainAccount() {
        Long id = mainAccount().getId();
        return treasuryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Treasury account has not been initialised"));
    }

    private TransferRecord lockTransfer(String reference) {
        return transferRepository.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer " + reference + " not found"));
    }
}
