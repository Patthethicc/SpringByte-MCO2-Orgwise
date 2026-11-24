package com.fintech.orgwise.treasury.repository;

import com.fintech.orgwise.treasury.dto.EventDisbursementDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import com.fintech.orgwise.treasury.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class TransferRepositoryTest {

    private static final EnumSet<TransferStatus> ACTIVE = EnumSet.of(TransferStatus.PENDING, TransferStatus.COMPLETED);

    @Autowired
    private TransferRepository repository;

    private TransferRecord pending;
    private TransferRecord completed;
    private TransferRecord failed;

    @BeforeEach
    void seed() {
        pending = repository.save(Fixtures.pendingTransfer(1, "100.00"));
        completed = Fixtures.pendingTransfer(2, "200.00");
        completed.markCompleted();
        completed = repository.save(completed);
        failed = Fixtures.pendingTransfer(3, "300.00");
        failed.markFailed("down", true);
        failed = repository.save(failed);
        repository.flush();
    }

    @Test
    @DisplayName("TFR-01 findByReference / findByReferenceForUpdate find by reference, empty when unknown")
    void findByReference() {
        assertThat(repository.findByReference(pending.getReference())).contains(pending);
        assertThat(repository.findByReferenceForUpdate(completed.getReference())).contains(completed);
        assertThat(repository.findByReference("nope")).isEmpty();
    }

    @Test
    @DisplayName("TFR-02 existsByExpenseIdAndStatusIn sees PENDING and COMPLETED but not FAILED")
    void existsActive() {
        assertThat(repository.existsByExpenseIdAndStatusIn(1L, ACTIVE)).isTrue();
        assertThat(repository.existsByExpenseIdAndStatusIn(2L, ACTIVE)).isTrue();
        assertThat(repository.existsByExpenseIdAndStatusIn(3L, ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("TFR-03 findByStatus and findByRecipientId page results")
    void paging() {
        assertThat(repository.findByStatus(TransferStatus.FAILED, PageRequest.of(0, 10)).getContent())
                .containsExactly(failed);
        assertThat(repository.findByRecipientId(7L, PageRequest.of(0, 2)).getTotalElements()).isEqualTo(3);
        assertThat(repository.findByRecipientId(99L, PageRequest.of(0, 2)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("TFR-04 stale PENDING query respects the cut-off")
    void stalePending() {
        assertThat(repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TransferStatus.PENDING,
                Instant.now().plusSeconds(60))).containsExactly(pending);
        assertThat(repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TransferStatus.PENDING,
                Instant.now().minusSeconds(3600))).isEmpty();
    }

    @Test
    @DisplayName("TFR-05 findByStatusAndRevertPendingTrue returns only FAILED rows awaiting revert")
    void awaitingRevert() {
        assertThat(repository.findByStatusAndRevertPendingTrue(TransferStatus.FAILED)).containsExactly(failed);
        assertThat(repository.findByStatusAndRevertPendingTrue(TransferStatus.PENDING)).isEmpty();
    }

    @Test
    @DisplayName("TFR-06 findExpenseIdsWithStatusIn returns only busy expense IDs")
    void busyIds() {
        assertThat(repository.findExpenseIdsWithStatusIn(List.of(1L, 2L, 3L, 4L), ACTIVE))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("TFR-07 sums, counts and per-event totals only include the requested status")
    void aggregates() {
        TransferRecord another = Fixtures.pendingTransfer(5, "50.00");
        another.markCompleted();
        repository.saveAndFlush(another);

        assertThat(repository.sumAmountByStatus(TransferStatus.COMPLETED)).isEqualByComparingTo("250.00");
        assertThat(repository.sumAmountByStatus(TransferStatus.PENDING)).isEqualByComparingTo("100.00");
        assertThat(repository.countByStatus(TransferStatus.FAILED)).isEqualTo(1);
        List<EventDisbursementDTO> byEvent = repository.sumCompletedByEvent();
        assertThat(byEvent).singleElement().satisfies(e -> {
            assertThat(e.eventId()).isEqualTo(3L);
            assertThat(e.totalDisbursed()).isEqualByComparingTo("250.00");
            assertThat(e.transferCount()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("TFR-08 sumAmountByStatus returns zero when nothing matches")
    void sumZero() {
        repository.deleteAll();
        assertThat(repository.sumAmountByStatus(TransferStatus.COMPLETED)).isEqualByComparingTo("0");
        assertThat(repository.sumCompletedByEvent()).isEmpty();
    }

    @Test
    @DisplayName("TFR-09 the reference column is unique")
    void uniqueReference() {
        repository.deleteAll();
        repository.flush();
        TransferRecord a = Fixtures.pendingTransfer(9, "1.00");
        repository.saveAndFlush(a);
        TransferRecord clone = Fixtures.pendingTransfer(9, "1.00");
        org.springframework.test.util.ReflectionTestUtils.setField(clone, "reference", a.getReference());
        assertThatThrownBy(() -> repository.saveAndFlush(clone)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
