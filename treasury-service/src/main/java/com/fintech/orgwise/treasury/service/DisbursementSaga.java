package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.client.ExpenseClient;
import com.fintech.orgwise.treasury.client.dto.DisbursementCommandDTO;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.dto.TransferRecordDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.DisbursementFailedException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.security.JwtPrincipal;
import com.fintech.orgwise.treasury.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the disbursement saga that replaced MCO1's single-database
 * {@code TransferService.disburse} transaction (RFC-001 section 4).
 *
 * <pre>
 *  0. GET expense (Feign)            fail closed: 503/404/409, nothing changed
 *  1. reserveFunds (local tx)        lock balance, debit, transfer = PENDING
 *  2. POST expense/disburse (Feign)  idempotent on the transfer reference
 *  3a. success: completeTransfer     transfer = COMPLETED
 *  3b. failure: revert-disburse (best effort, Feign) then
 *      compensateTransfer (local tx) refund balance, transfer = FAILED
 * </pre>
 *
 * <p>This class is deliberately not {@code @Transactional}: each step commits on its own, so a
 * remote call never runs while the balance row is locked. A crash between steps leaves a PENDING
 * transfer that {@link PendingTransferReconciler} finishes later.</p>
 */
@Service
public class DisbursementSaga {

    private static final Logger log = LoggerFactory.getLogger(DisbursementSaga.class);

    private final TransferService transferService;
    private final ExpenseClient expenseClient;

    /**
     * @param transferService local ledger operations
     * @param expenseClient   Feign client for expense-service
     */
    public DisbursementSaga(TransferService transferService, ExpenseClient expenseClient) {
        this.transferService = transferService;
        this.expenseClient = expenseClient;
    }

    /**
     * Pays out an approved expense.
     *
     * @param expenseId expense to disburse
     * @param actor     the caller; must be a finance head
     * @return the COMPLETED transfer
     * @throws AccessDeniedException       if the caller is not a finance head
     * @throws ResourceNotFoundException   if the expense does not exist
     * @throws BusinessRuleException       if the expense is not approved or already disbursed
     * @throws com.fintech.orgwise.treasury.exception.InsufficientFundsException if the balance is too low
     * @throws com.fintech.orgwise.treasury.exception.ServiceUnavailableException if expense-service is down
     *                                     before any money moved
     * @throws DisbursementFailedException if step 2 failed and the saga was compensated
     */
    public TransferRecordDTO disburse(Long expenseId, JwtPrincipal actor) {
        if (actor == null || !actor.hasAnyRole(Roles.FINANCE_HEAD)) {
            throw new AccessDeniedException("Only the Finance Head can disburse funds");
        }
        if (expenseId == null || expenseId <= 0) {
            throw new IllegalArgumentException("expenseId must be a positive number");
        }

        ExpenseSnapshotDTO expense = expenseClient.getExpense(expenseId);
        if (expense == null) {
            throw new ResourceNotFoundException("Expense " + expenseId + " not found");
        }

        TransferRecord pending = transferService.reserveFunds(expense, actor);
        DisbursementCommandDTO command = new DisbursementCommandDTO(pending.getReference(), pending.getAmount(),
                actor.officerId(), actor.username());

        try {
            expenseClient.markDisbursed(expenseId, command);
        } catch (RuntimeException failure) {
            throw compensate(expenseId, pending, command, failure);
        }

        TransferRecord completed = transferService.completeTransfer(pending.getReference());
        log.info("Disbursed expense {} via transfer {}", expenseId, completed.getReference());
        return TransferRecordDTO.from(completed);
    }

    private DisbursementFailedException compensate(Long expenseId, TransferRecord pending,
                                                   DisbursementCommandDTO command, RuntimeException failure) {
        log.warn("Step 2 failed for transfer {} (expense {}): {}. Compensating.", pending.getReference(), expenseId,
                failure.getMessage());
        boolean revertPending = !tryRevert(expenseId, command);
        TransferRecord failed = transferService.compensateTransfer(pending.getReference(),
                "Expense service did not confirm the disbursement: " + failure.getMessage(), revertPending);

        boolean rejected = failure instanceof BusinessRuleException || failure instanceof ResourceNotFoundException;
        HttpStatus status = rejected ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE;
        String message = (rejected
                ? "Expense service rejected the disbursement: " + failure.getMessage() + ". "
                : "Expense service could not confirm the disbursement. ")
                + "The balance was restored and transfer " + failed.getReference() + " is FAILED.";
        return new DisbursementFailedException(message, failed.getReference(), status, failure);
    }

    /**
     * Asks expense-service to undo step 2 in case it applied it before failing (e.g. timeout).
     *
     * @return {@code true} if the revert was confirmed
     */
    private boolean tryRevert(Long expenseId, DisbursementCommandDTO command) {
        try {
            expenseClient.revertDisbursement(expenseId, command);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not confirm revert of expense {} for transfer {}: {}. The reconciler will retry.",
                    expenseId, command.transferReference(), e.getMessage());
            return false;
        }
    }
}
