package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.client.ExpenseClient;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.dto.EventDisbursementDTO;
import com.fintech.orgwise.treasury.dto.TreasuryAggregateDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import com.fintech.orgwise.treasury.entity.TreasuryAccount;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.InsufficientFundsException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import com.fintech.orgwise.treasury.repository.TransferRepository;
import com.fintech.orgwise.treasury.repository.TreasuryRepository;
import com.fintech.orgwise.treasury.security.JwtPrincipal;
import com.fintech.orgwise.treasury.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Mock
    private TreasuryRepository treasuryRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private ExpenseClient expenseClient;

    private TransferService service;
    private TreasuryAccount account;

    @BeforeEach
    void setUp() {
        service = new TransferService(treasuryRepository, transferRepository, expenseClient,
                Clock.fixed(NOW, ZoneOffset.UTC));
        account = new TreasuryAccount("Main", new BigDecimal("1000.00"), "PHP");
        ReflectionTestUtils.setField(account, "id", 1L);
        when(treasuryRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(account));
        when(treasuryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transferRepository.save(any(TransferRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("reserveFunds()")
    class ReserveFunds {
        @Test
        @DisplayName("TS-01 debits the locked balance and saves a PENDING transfer with snapshot data")
        void reserves() {
            TransferRecord t = service.reserveFunds(Fixtures.expense(10, "400.00", "APPROVED"), Fixtures.FINANCE);

            assertThat(account.getBalance()).isEqualByComparingTo("600.00");
            assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDING);
            assertThat(t.getExpenseId()).isEqualTo(10L);
            assertThat(t.getRecipientId()).isEqualTo(7L);
            assertThat(t.getDisbursedByUsername()).isEqualTo("finance");
            verify(treasuryRepository).findByIdForUpdate(1L);
        }

        @Test
        @DisplayName("TS-02 rejects an expense that is not APPROVED without touching the balance")
        void rejectsNotApproved() {
            assertThatThrownBy(() -> service.reserveFunds(Fixtures.expense(10, "400.00", "PENDING"), Fixtures.FINANCE))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("expected APPROVED");
            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
            verify(transferRepository, never()).save(any());
        }

        @Test
        @DisplayName("TS-03 rejects an expense that already has an active transfer (no double disbursement)")
        void rejectsDouble() {
            when(transferRepository.existsByExpenseIdAndStatusIn(eq(10L), anyCollection())).thenReturn(true);
            assertThatThrownBy(() -> service.reserveFunds(Fixtures.expense(10, "400.00", "APPROVED"), Fixtures.FINANCE))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already been disbursed");
            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("TS-04 propagates InsufficientFundsException and saves nothing")
        void insufficient() {
            assertThatThrownBy(() -> service.reserveFunds(Fixtures.expense(10, "5000.00", "APPROVED"), Fixtures.FINANCE))
                    .isInstanceOf(InsufficientFundsException.class);
            verify(transferRepository, never()).save(any());
        }

        @Test
        @DisplayName("TS-05 rejects a missing or zero amount")
        void rejectsNoAmount() {
            assertThatThrownBy(() -> service.reserveFunds(Fixtures.expense(10, null, "APPROVED"), Fixtures.FINANCE))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.reserveFunds(Fixtures.expense(10, "0", "APPROVED"), Fixtures.FINANCE))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("TS-06 fails with 404 when the treasury was never bootstrapped")
        void noAccount() {
            when(treasuryRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.reserveFunds(Fixtures.expense(10, "1.00", "APPROVED"), Fixtures.FINANCE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("completeTransfer()")
    class Complete {
        @Test
        @DisplayName("TS-07 marks a PENDING transfer COMPLETED")
        void completes() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10.00");
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));
            assertThat(service.completeTransfer(t.getReference()).getStatus()).isEqualTo(TransferStatus.COMPLETED);
        }

        @Test
        @DisplayName("TS-08 is idempotent for an already COMPLETED transfer")
        void idempotent() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10.00");
            t.markCompleted();
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));
            assertThat(service.completeTransfer(t.getReference()).getStatus()).isEqualTo(TransferStatus.COMPLETED);
        }

        @Test
        @DisplayName("TS-09 refuses a FAILED transfer and 404s on an unknown one")
        void refuses() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10.00");
            t.markFailed("x", false);
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.completeTransfer(t.getReference()))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> service.completeTransfer("missing")).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("compensateTransfer()")
    class Compensate {
        @Test
        @DisplayName("TS-10 refunds the amount and marks the transfer FAILED")
        void refunds() {
            TransferRecord t = Fixtures.pendingTransfer(1, "250.00");
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));

            TransferRecord result = service.compensateTransfer(t.getReference(), "down", true);

            assertThat(account.getBalance()).isEqualByComparingTo("1250.00");
            assertThat(result.getStatus()).isEqualTo(TransferStatus.FAILED);
            assertThat(result.isRevertPending()).isTrue();
        }

        @Test
        @DisplayName("TS-11 does not refund twice when the transfer is already FAILED")
        void noDoubleRefund() {
            TransferRecord t = Fixtures.pendingTransfer(1, "250.00");
            t.markFailed("first", false);
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));

            service.compensateTransfer(t.getReference(), "again", false);

            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
            verify(treasuryRepository, never()).findByIdForUpdate(anyLong());
        }

        @Test
        @DisplayName("TS-12 refuses to compensate a COMPLETED transfer")
        void refusesCompleted() {
            TransferRecord t = Fixtures.pendingTransfer(1, "250.00");
            t.markCompleted();
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.compensateTransfer(t.getReference(), "x", false))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        }
    }

    @Nested
    @DisplayName("confirmRevert()")
    class ConfirmRevert {
        @Test
        @DisplayName("TS-13 clears revertPending on a FAILED transfer")
        void clears() {
            TransferRecord t = Fixtures.pendingTransfer(1, "1.00");
            t.markFailed("x", true);
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));
            assertThat(service.confirmRevert(t.getReference()).isRevertPending()).isFalse();
        }

        @Test
        @DisplayName("TS-14 404s on an unknown transfer")
        void unknown() {
            assertThatThrownBy(() -> service.confirmRevert("missing")).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("TS-15 refuses a PENDING transfer")
        void pending() {
            TransferRecord t = Fixtures.pendingTransfer(1, "1.00");
            when(transferRepository.findByReferenceForUpdate(t.getReference())).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.confirmRevert(t.getReference())).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("getTreasuryAccount()")
    class GetAccount {
        @Test
        @DisplayName("TS-16 maps the main account to a DTO")
        void maps() {
            var dto = service.getTreasuryAccount();
            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.balance()).isEqualByComparingTo("1000.00");
            assertThat(dto.currency()).isEqualTo("PHP");
        }

        @Test
        @DisplayName("TS-17 reflects the balance after a reservation")
        void reflectsDebit() {
            service.reserveFunds(Fixtures.expense(1, "100.00", "APPROVED"), Fixtures.FINANCE);
            assertThat(service.getTreasuryAccount().balance()).isEqualByComparingTo("900.00");
        }

        @Test
        @DisplayName("TS-18 404s when no account exists")
        void missing() {
            when(treasuryRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getTreasuryAccount()).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getTransfers() / getTransfersForRecipient()")
    class Listing {
        @Test
        @DisplayName("TS-19 without a status filter lists the whole ledger")
        void all() {
            var page = PageRequest.of(0, 20);
            when(transferRepository.findAll(page)).thenReturn(new PageImpl<>(List.of(Fixtures.pendingTransfer(1, "1")), page, 1));
            var result = service.getTransfers(null, page);
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("TS-20 with a status filter uses findByStatus")
        void filtered() {
            var page = PageRequest.of(1, 5);
            when(transferRepository.findByStatus(TransferStatus.FAILED, page)).thenReturn(new PageImpl<>(List.of(), page, 5));
            var result = service.getTransfers(TransferStatus.FAILED, page);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.content()).isEmpty();
            verify(transferRepository, never()).findAll(page);
        }

        @Test
        @DisplayName("TS-21 recipient listing uses the officer ID; a token without officerId is rejected")
        void recipient() {
            var page = PageRequest.of(0, 20);
            when(transferRepository.findByRecipientId(7L, page))
                    .thenReturn(new PageImpl<>(List.of(Fixtures.pendingTransfer(1, "1"), Fixtures.pendingTransfer(2, "2")), page, 2));
            assertThat(service.getTransfersForRecipient(7L, page).content()).hasSize(2);
            assertThatThrownBy(() -> service.getTransfersForRecipient(null, page))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getTransfer()")
    class GetOne {
        @Test
        @DisplayName("TS-22 finance head and president can view any transfer")
        void privileged() {
            TransferRecord t = Fixtures.pendingTransfer(1, "1");
            when(transferRepository.findByReference(t.getReference())).thenReturn(Optional.of(t));
            assertThat(service.getTransfer(t.getReference(), Fixtures.FINANCE).reference()).isEqualTo(t.getReference());
            assertThat(service.getTransfer(t.getReference(), Fixtures.PRESIDENT).reference()).isEqualTo(t.getReference());
        }

        @Test
        @DisplayName("TS-23 the recipient can view their own transfer, another associate cannot")
        void recipientOnly() {
            TransferRecord t = Fixtures.pendingTransfer(1, "1");
            when(transferRepository.findByReference(t.getReference())).thenReturn(Optional.of(t));
            assertThat(service.getTransfer(t.getReference(), Fixtures.ASSOCIATE)).isNotNull();
            assertThatThrownBy(() -> service.getTransfer(t.getReference(), new JwtPrincipal(99L, "other", "ASSOCIATE")))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("TS-24 404s on an unknown reference")
        void unknown() {
            assertThatThrownBy(() -> service.getTransfer("missing", Fixtures.FINANCE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getDisbursableExpenses()")
    class Disbursable {
        @Test
        @DisplayName("TS-25 removes approved expenses that already have an active transfer")
        void filtersBusy() {
            when(expenseClient.getExpensesByStatus("APPROVED")).thenReturn(List.of(
                    Fixtures.expense(1, "10", "APPROVED"), Fixtures.expense(2, "20", "APPROVED")));
            when(transferRepository.findExpenseIdsWithStatusIn(eq(List.of(1L, 2L)), anyCollection())).thenReturn(List.of(2L));
            assertThat(service.getDisbursableExpenses()).extracting(ExpenseSnapshotDTO::id).containsExactly(1L);
        }

        @Test
        @DisplayName("TS-26 returns an empty list without querying the ledger when nothing is approved")
        void empty() {
            when(expenseClient.getExpensesByStatus("APPROVED")).thenReturn(List.of());
            assertThat(service.getDisbursableExpenses()).isEmpty();
            when(expenseClient.getExpensesByStatus("APPROVED")).thenReturn(null);
            assertThat(service.getDisbursableExpenses()).isEmpty();
            verify(transferRepository, never()).findExpenseIdsWithStatusIn(any(), any());
        }

        @Test
        @DisplayName("TS-27 propagates 503 when expense-service is down (fail closed)")
        void failsClosed() {
            when(expenseClient.getExpensesByStatus("APPROVED")).thenThrow(new ServiceUnavailableException("down"));
            assertThatThrownBy(() -> service.getDisbursableExpenses()).isInstanceOf(ServiceUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("getAggregate()")
    class Aggregate {
        @Test
        @DisplayName("TS-28 combines balance, sums, counts and per-event totals")
        void combines() {
            when(transferRepository.sumAmountByStatus(TransferStatus.COMPLETED)).thenReturn(new BigDecimal("300.00"));
            when(transferRepository.countByStatus(TransferStatus.COMPLETED)).thenReturn(2L);
            when(transferRepository.countByStatus(TransferStatus.PENDING)).thenReturn(1L);
            when(transferRepository.countByStatus(TransferStatus.FAILED)).thenReturn(4L);
            when(transferRepository.sumCompletedByEvent())
                    .thenReturn(List.of(new EventDisbursementDTO(3L, new BigDecimal("300.00"), 2)));

            TreasuryAggregateDTO a = service.getAggregate();

            assertThat(a.balance()).isEqualByComparingTo("1000.00");
            assertThat(a.totalDisbursed()).isEqualByComparingTo("300.00");
            assertThat(a.completedTransfers()).isEqualTo(2);
            assertThat(a.pendingTransfers()).isEqualTo(1);
            assertThat(a.failedTransfers()).isEqualTo(4);
            assertThat(a.byEvent()).hasSize(1);
            assertThat(a.generatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("TS-29 treats a null sum as zero")
        void nullSum() {
            when(transferRepository.sumAmountByStatus(TransferStatus.COMPLETED)).thenReturn(null);
            assertThat(service.getAggregate().totalDisbursed()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("TS-30 404s when no account exists")
        void missing() {
            when(treasuryRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getAggregate()).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findStalePendingTransfers() / findTransfersAwaitingRevert()")
    class Reconciliation {
        @Test
        @DisplayName("TS-31 uses now minus the timeout as cut-off")
        void cutoff() {
            service.findStalePendingTransfers(Duration.ofMinutes(2));
            ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
            verify(transferRepository).findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(TransferStatus.PENDING), cutoff.capture());
            assertThat(cutoff.getValue()).isEqualTo(NOW.minusSeconds(120));
        }

        @Test
        @DisplayName("TS-32 returns what the repository finds")
        void returnsRows() {
            TransferRecord t = Fixtures.pendingTransfer(1, "1");
            when(transferRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(TransferStatus.PENDING), any()))
                    .thenReturn(List.of(t));
            assertThat(service.findStalePendingTransfers(Duration.ZERO)).containsExactly(t);
        }

        @Test
        @DisplayName("TS-33 awaiting-revert queries FAILED rows with revertPending")
        void awaitingRevert() {
            TransferRecord t = Fixtures.pendingTransfer(1, "1");
            t.markFailed("x", true);
            when(transferRepository.findByStatusAndRevertPendingTrue(TransferStatus.FAILED)).thenReturn(List.of(t));
            assertThat(service.findTransfersAwaitingRevert()).containsExactly(t);
        }
    }
}
