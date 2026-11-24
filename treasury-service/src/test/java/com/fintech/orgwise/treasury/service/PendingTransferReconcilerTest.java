package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.client.ExpenseClient;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.config.TreasuryProperties;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import com.fintech.orgwise.treasury.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingTransferReconcilerTest {

    @Mock
    private TransferService transferService;
    @Mock
    private ExpenseClient expenseClient;

    private PendingTransferReconciler reconciler;
    private TransferRecord stuck;

    @BeforeEach
    void setUp() {
        TreasuryProperties props = new TreasuryProperties("Main", BigDecimal.TEN, "PHP",
                new TreasuryProperties.Reconciliation(true, Duration.ofMinutes(2)));
        reconciler = new PendingTransferReconciler(transferService, expenseClient, props);
        stuck = Fixtures.pendingTransfer(10, "100.00");
        when(transferService.findStalePendingTransfers(Duration.ofMinutes(2))).thenReturn(List.of(stuck));
        when(transferService.findTransfersAwaitingRevert()).thenReturn(List.of());
    }

    private ExpenseSnapshotDTO expenseWith(String status, String reference) {
        return new ExpenseSnapshotDTO(10L, "t", new BigDecimal("100.00"), status, 7L, "A", 3L, reference);
    }

    @Test
    @DisplayName("RC-01 completes a stuck PENDING transfer that expense-service shows as DISBURSED by it")
    void completesConfirmed() {
        when(expenseClient.getExpense(10L)).thenReturn(expenseWith("DISBURSED", stuck.getReference()));
        assertThat(reconciler.reconcile()).isEqualTo(1);
        verify(transferService).completeTransfer(stuck.getReference());
        verify(transferService, never()).compensateTransfer(anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("RC-02 compensates a stuck transfer whose expense is still APPROVED")
    void compensatesUnconfirmed() {
        when(expenseClient.getExpense(10L)).thenReturn(expenseWith("APPROVED", null));
        assertThat(reconciler.reconcile()).isEqualTo(1);
        verify(transferService).compensateTransfer(eq(stuck.getReference()), anyString(), eq(false));
    }

    @Test
    @DisplayName("RC-03 compensates when DISBURSED by a different reference")
    void compensatesOtherReference() {
        when(expenseClient.getExpense(10L)).thenReturn(expenseWith("DISBURSED", "someone-else"));
        reconciler.reconcile();
        verify(transferService).compensateTransfer(eq(stuck.getReference()), anyString(), eq(false));
    }

    @Test
    @DisplayName("RC-04 compensates when the expense no longer exists")
    void compensatesMissing() {
        when(expenseClient.getExpense(10L)).thenThrow(new ResourceNotFoundException("gone"));
        assertThat(reconciler.reconcile()).isEqualTo(1);
        verify(transferService).compensateTransfer(eq(stuck.getReference()), anyString(), eq(false));
    }

    @Test
    @DisplayName("RC-05 leaves the transfer PENDING while expense-service is down")
    void skipsWhenDown() {
        when(expenseClient.getExpense(10L)).thenThrow(new ServiceUnavailableException("down"));
        assertThat(reconciler.reconcile()).isZero();
        verify(transferService, never()).completeTransfer(any());
        verify(transferService, never()).compensateTransfer(anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("RC-06 a local failure while resolving is logged and counted as unchanged")
    void localFailure() {
        when(expenseClient.getExpense(10L)).thenReturn(expenseWith("DISBURSED", stuck.getReference()));
        when(transferService.completeTransfer(stuck.getReference())).thenThrow(new BusinessRuleException("race"));
        assertThat(reconciler.reconcile()).isZero();
    }

    @Test
    @DisplayName("RC-07 retries the revert of FAILED transfers and confirms on success")
    void retriesRevert() {
        TransferRecord failed = Fixtures.pendingTransfer(11, "50.00");
        failed.markFailed("x", true);
        when(transferService.findStalePendingTransfers(any())).thenReturn(List.of());
        when(transferService.findTransfersAwaitingRevert()).thenReturn(List.of(failed));

        assertThat(reconciler.reconcile()).isEqualTo(1);
        verify(expenseClient).revertDisbursement(eq(11L), any());
        verify(transferService).confirmRevert(failed.getReference());
    }

    @Test
    @DisplayName("RC-08 keeps revertPending when the revert still fails")
    void revertStillFails() {
        TransferRecord failed = Fixtures.pendingTransfer(11, "50.00");
        failed.markFailed("x", true);
        when(transferService.findStalePendingTransfers(any())).thenReturn(List.of());
        when(transferService.findTransfersAwaitingRevert()).thenReturn(List.of(failed));
        when(expenseClient.revertDisbursement(eq(11L), any())).thenThrow(new ServiceUnavailableException("down"));

        assertThat(reconciler.reconcile()).isZero();
        verify(transferService, never()).confirmRevert(any());
    }

    @Test
    @DisplayName("RC-09 scheduledRun does nothing when reconciliation is disabled")
    void disabled() {
        PendingTransferReconciler off = new PendingTransferReconciler(transferService, expenseClient,
                new TreasuryProperties(null, null, null, new TreasuryProperties.Reconciliation(false, null)));
        off.scheduledRun();
        verifyNoInteractions(expenseClient);
        verify(transferService, never()).findStalePendingTransfers(any());
    }

    @Test
    @DisplayName("RC-10 scheduledRun reconciles when enabled")
    void enabled() {
        when(expenseClient.getExpense(10L)).thenReturn(expenseWith("APPROVED", null));
        reconciler.scheduledRun();
        verify(transferService).compensateTransfer(eq(stuck.getReference()), anyString(), eq(false));
    }

    @Test
    @DisplayName("RC-11 scheduledRun with nothing stale or awaiting revert changes nothing")
    void nothingToDo() {
        when(transferService.findStalePendingTransfers(any())).thenReturn(List.of());
        reconciler.scheduledRun();
        assertThat(reconciler.reconcile()).isZero();
        verifyNoInteractions(expenseClient);
    }
}
