package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.client.ExpenseClient;
import com.fintech.orgwise.treasury.client.dto.DisbursementCommandDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.DisbursementFailedException;
import com.fintech.orgwise.treasury.exception.InsufficientFundsException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import com.fintech.orgwise.treasury.security.JwtPrincipal;
import com.fintech.orgwise.treasury.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisbursementSagaTest {

    @Mock
    private TransferService transferService;
    @Mock
    private ExpenseClient expenseClient;

    private DisbursementSaga saga;
    private TransferRecord pending;

    @BeforeEach
    void setUp() {
        saga = new DisbursementSaga(transferService, expenseClient);
        pending = Fixtures.pendingTransfer(10, "400.00");
    }

    private void stubReserve() {
        when(expenseClient.getExpense(10L)).thenReturn(Fixtures.expense(10, "400.00", "APPROVED"));
        when(transferService.reserveFunds(any(), eq(Fixtures.FINANCE))).thenReturn(pending);
    }

    private TransferRecord failedCopy() {
        TransferRecord failed = Fixtures.pendingTransfer(10, "400.00");
        failed.markFailed("x", false);
        return failed;
    }

    @Test
    @DisplayName("DS-01 happy path runs reserve -> markDisbursed -> complete in order and returns COMPLETED")
    void happyPath() {
        stubReserve();
        TransferRecord done = Fixtures.pendingTransfer(10, "400.00");
        done.markCompleted();
        when(transferService.completeTransfer(pending.getReference())).thenReturn(done);

        var result = saga.disburse(10L, Fixtures.FINANCE);

        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        InOrder order = inOrder(expenseClient, transferService);
        order.verify(expenseClient).getExpense(10L);
        order.verify(transferService).reserveFunds(any(), eq(Fixtures.FINANCE));
        order.verify(expenseClient).markDisbursed(eq(10L), any());
        order.verify(transferService).completeTransfer(pending.getReference());
        verify(transferService, never()).compensateTransfer(anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("DS-02 sends the transfer reference as idempotency key with the actor's identity")
    void sendsReference() {
        stubReserve();
        when(transferService.completeTransfer(any())).thenReturn(pending);

        saga.disburse(10L, Fixtures.FINANCE);

        ArgumentCaptor<DisbursementCommandDTO> cmd = ArgumentCaptor.forClass(DisbursementCommandDTO.class);
        verify(expenseClient).markDisbursed(eq(10L), cmd.capture());
        assertThat(cmd.getValue().transferReference()).isEqualTo(pending.getReference());
        assertThat(cmd.getValue().amount()).isEqualByComparingTo("400.00");
        assertThat(cmd.getValue().disbursedByUsername()).isEqualTo("finance");
    }

    @Test
    @DisplayName("DS-03 outage in step 2 compensates (revert confirmed) and throws 503")
    void outageCompensates() {
        stubReserve();
        when(expenseClient.markDisbursed(eq(10L), any())).thenThrow(new ServiceUnavailableException("down"));
        when(transferService.compensateTransfer(eq(pending.getReference()), anyString(), eq(false))).thenReturn(failedCopy());

        assertThatThrownBy(() -> saga.disburse(10L, Fixtures.FINANCE))
                .isInstanceOfSatisfying(DisbursementFailedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("balance was restored");
                });
        verify(expenseClient).revertDisbursement(eq(10L), any());
        verify(transferService, never()).completeTransfer(any());
    }

    @Test
    @DisplayName("DS-04 when the revert also fails, compensation records revertPending=true")
    void revertFailureFlagged() {
        stubReserve();
        when(expenseClient.markDisbursed(eq(10L), any())).thenThrow(new ServiceUnavailableException("timeout"));
        when(expenseClient.revertDisbursement(eq(10L), any())).thenThrow(new ServiceUnavailableException("down"));
        when(transferService.compensateTransfer(eq(pending.getReference()), anyString(), eq(true))).thenReturn(failedCopy());

        assertThatThrownBy(() -> saga.disburse(10L, Fixtures.FINANCE)).isInstanceOf(DisbursementFailedException.class);
        verify(transferService).compensateTransfer(eq(pending.getReference()), anyString(), eq(true));
    }

    @Test
    @DisplayName("DS-05 a business rejection in step 2 compensates and maps to 409")
    void rejectionIsConflict() {
        stubReserve();
        when(expenseClient.markDisbursed(eq(10L), any())).thenThrow(new BusinessRuleException("not APPROVED"));
        when(transferService.compensateTransfer(any(), any(), anyBoolean())).thenReturn(failedCopy());

        assertThatThrownBy(() -> saga.disburse(10L, Fixtures.FINANCE))
                .isInstanceOfSatisfying(DisbursementFailedException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("DS-06 expense-service down at step 0 fails closed: nothing reserved")
    void failsClosedBeforeReserve() {
        when(expenseClient.getExpense(10L)).thenThrow(new ServiceUnavailableException("down"));
        assertThatThrownBy(() -> saga.disburse(10L, Fixtures.FINANCE)).isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("DS-07 unknown or null expense -> 404, nothing reserved")
    void unknownExpense() {
        when(expenseClient.getExpense(10L)).thenThrow(new ResourceNotFoundException("nope"));
        assertThatThrownBy(() -> saga.disburse(10L, Fixtures.FINANCE)).isInstanceOf(ResourceNotFoundException.class);
        when(expenseClient.getExpense(11L)).thenReturn(null);
        assertThatThrownBy(() -> saga.disburse(11L, Fixtures.FINANCE)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("DS-08 insufficient funds in step 1 propagates and never calls step 2")
    void insufficientFunds() {
        when(expenseClient.getExpense(10L)).thenReturn(Fixtures.expense(10, "400.00", "APPROVED"));
        when(transferService.reserveFunds(any(), any()))
                .thenThrow(new InsufficientFundsException(BigDecimal.ONE, BigDecimal.TEN));
        assertThatThrownBy(() -> saga.disburse(10L, Fixtures.FINANCE)).isInstanceOf(InsufficientFundsException.class);
        verify(expenseClient, never()).markDisbursed(any(), any());
    }

    @Test
    @DisplayName("DS-09 only the finance head may disburse (service-layer role check)")
    void roleCheck() {
        assertThatThrownBy(() -> saga.disburse(10L, Fixtures.PRESIDENT)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> saga.disburse(10L, new JwtPrincipal(1L, "admin", "ADMIN")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> saga.disburse(10L, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(expenseClient, transferService);
    }

    @Test
    @DisplayName("DS-10 rejects a null or non-positive expense ID")
    void badId() {
        assertThatThrownBy(() -> saga.disburse(null, Fixtures.FINANCE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> saga.disburse(0L, Fixtures.FINANCE)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(expenseClient);
    }
}
