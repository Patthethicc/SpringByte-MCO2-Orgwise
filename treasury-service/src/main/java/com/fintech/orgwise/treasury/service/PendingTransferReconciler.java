package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.client.ExpenseClient;
import com.fintech.orgwise.treasury.client.dto.DisbursementCommandDTO;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.config.TreasuryProperties;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes sagas that were interrupted, so money never stays "held" forever.
 *
 * <ul>
 *   <li><b>Stuck PENDING</b> (the process died between saga steps): if expense-service shows the
 *       expense {@code DISBURSED} by this transfer, complete it; otherwise compensate it.</li>
 *   <li><b>FAILED with an unconfirmed revert</b>: retry {@code revert-disburse} until
 *       expense-service confirms.</li>
 * </ul>
 * <p>If expense-service is still down, the transfer is left as-is and retried next round.
 * Calls carry a service token (there is no user request to forward).</p>
 */
@Component
public class PendingTransferReconciler {

    private static final Logger log = LoggerFactory.getLogger(PendingTransferReconciler.class);
    private static final String SYSTEM_USER = "treasury-service";

    private final TransferService transferService;
    private final ExpenseClient expenseClient;
    private final TreasuryProperties properties;

    /**
     * @param transferService local ledger operations
     * @param expenseClient   Feign client for expense-service
     * @param properties      reconciliation settings
     */
    public PendingTransferReconciler(TransferService transferService, ExpenseClient expenseClient,
                                     TreasuryProperties properties) {
        this.transferService = transferService;
        this.expenseClient = expenseClient;
        this.properties = properties;
    }

    /** Scheduled entry point; does nothing when reconciliation is disabled. */
    @Scheduled(fixedDelayString = "${orgwise.treasury.reconciliation.fixed-delay:PT1M}",
            initialDelayString = "${orgwise.treasury.reconciliation.initial-delay:PT1M}")
    public void scheduledRun() {
        if (properties.reconciliation().enabled()) {
            reconcile();
        }
    }

    /**
     * Runs one reconciliation pass.
     *
     * @return number of transfers whose state changed
     */
    public int reconcile() {
        int changed = 0;
        for (TransferRecord t : transferService.findStalePendingTransfers(properties.reconciliation().pendingTimeout())) {
            changed += resolvePending(t) ? 1 : 0;
        }
        for (TransferRecord t : transferService.findTransfersAwaitingRevert()) {
            changed += retryRevert(t) ? 1 : 0;
        }
        return changed;
    }

    private boolean resolvePending(TransferRecord t) {
        ExpenseSnapshotDTO expense;
        try {
            expense = expenseClient.getExpense(t.getExpenseId());
        } catch (ResourceNotFoundException e) {
            transferService.compensateTransfer(t.getReference(), "Reconciler: expense no longer exists", false);
            return true;
        } catch (RuntimeException e) {
            log.info("Reconciler: expense-service unavailable, transfer {} stays PENDING", t.getReference());
            return false;
        }
        try {
            if (expense != null && expense.isDisbursedBy(t.getReference())) {
                transferService.completeTransfer(t.getReference());
                log.info("Reconciler completed transfer {}", t.getReference());
            } else {
                transferService.compensateTransfer(t.getReference(),
                        "Reconciler: expense-service never confirmed the disbursement", false);
                log.info("Reconciler compensated transfer {}", t.getReference());
            }
            return true;
        } catch (RuntimeException e) {
            log.warn("Reconciler could not resolve transfer {}: {}", t.getReference(), e.getMessage());
            return false;
        }
    }

    private boolean retryRevert(TransferRecord t) {
        try {
            expenseClient.revertDisbursement(t.getExpenseId(),
                    new DisbursementCommandDTO(t.getReference(), t.getAmount(), null, SYSTEM_USER));
            transferService.confirmRevert(t.getReference());
            log.info("Reconciler confirmed revert for transfer {}", t.getReference());
            return true;
        } catch (RuntimeException e) {
            log.info("Reconciler: revert for transfer {} still unconfirmed ({})", t.getReference(), e.getMessage());
            return false;
        }
    }
}
