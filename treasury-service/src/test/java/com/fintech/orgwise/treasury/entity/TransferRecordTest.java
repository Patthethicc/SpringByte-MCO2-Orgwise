package com.fintech.orgwise.treasury.entity;

import com.fintech.orgwise.treasury.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferRecordTest {

    @Nested
    @DisplayName("builder()")
    class Build {
        @Test
        @DisplayName("TR-01 builds a PENDING transfer with a UUID reference and snapshot fields")
        void buildsPending() {
            TransferRecord t = Fixtures.pendingTransfer(5, "250.00");
            assertThat(t.getStatus()).isEqualTo(TransferStatus.PENDING);
            assertThat(t.getReference()).hasSize(36);
            assertThat(t.getRecipientName()).isEqualTo("Assoc Member");
            assertThat(t.getCreatedAt()).isNotNull();
            assertThat(t.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("TR-02 every transfer gets a different reference")
        void uniqueReferences() {
            assertThat(Fixtures.pendingTransfer(5, "1").getReference())
                    .isNotEqualTo(Fixtures.pendingTransfer(5, "1").getReference());
        }

        @Test
        @DisplayName("TR-03 rejects a missing expenseId")
        void rejectsMissingExpense() {
            assertThatThrownBy(() -> TransferRecord.builder().amount(BigDecimal.ONE).disbursedByUsername("f").build())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expenseId");
        }

        @Test
        @DisplayName("TR-04 rejects a zero or negative amount")
        void rejectsNonPositiveAmount() {
            assertThatThrownBy(() -> TransferRecord.builder().expenseId(1L).amount(BigDecimal.ZERO)
                    .disbursedByUsername("f").build()).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TransferRecord.builder().expenseId(1L).amount(new BigDecimal("-1"))
                    .disbursedByUsername("f").build()).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TR-05 rejects a blank disbursing username")
        void rejectsBlankActor() {
            assertThatThrownBy(() -> TransferRecord.builder().expenseId(1L).amount(BigDecimal.ONE)
                    .disbursedByUsername(" ").build()).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("markCompleted()")
    class Complete {
        @Test
        @DisplayName("TR-06 moves PENDING to COMPLETED and stamps completedAt")
        void completes() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10");
            t.markCompleted();
            assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(t.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("TR-07 is idempotent on a COMPLETED transfer")
        void idempotent() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10");
            t.markCompleted();
            var firstStamp = t.getCompletedAt();
            t.markCompleted();
            assertThat(t.getCompletedAt()).isEqualTo(firstStamp);
        }

        @Test
        @DisplayName("TR-08 refuses to complete a FAILED transfer")
        void refusesFailed() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10");
            t.markFailed("x", false);
            assertThatThrownBy(t::markCompleted).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("markFailed()")
    class Fail {
        @Test
        @DisplayName("TR-09 moves PENDING to FAILED with reason and revert flag")
        void fails() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10");
            t.markFailed("expense down", true);
            assertThat(t.getStatus()).isEqualTo(TransferStatus.FAILED);
            assertThat(t.getFailureReason()).isEqualTo("expense down");
            assertThat(t.isRevertPending()).isTrue();
        }

        @Test
        @DisplayName("TR-10 truncates long reasons and defaults blank ones")
        void normalisesReason() {
            TransferRecord a = Fixtures.pendingTransfer(1, "10");
            a.markFailed("x".repeat(900), false);
            assertThat(a.getFailureReason()).hasSize(TransferRecord.MAX_REASON_LENGTH);
            TransferRecord b = Fixtures.pendingTransfer(1, "10");
            b.markFailed("  ", false);
            assertThat(b.getFailureReason()).isEqualTo("Unknown failure");
        }

        @Test
        @DisplayName("TR-11 is idempotent on FAILED and refuses COMPLETED")
        void idempotentAndGuarded() {
            TransferRecord failed = Fixtures.pendingTransfer(1, "10");
            failed.markFailed("first", false);
            failed.markFailed("second", true);
            assertThat(failed.getFailureReason()).isEqualTo("first");
            assertThat(failed.isRevertPending()).isFalse();

            TransferRecord done = Fixtures.pendingTransfer(1, "10");
            done.markCompleted();
            assertThatThrownBy(() -> done.markFailed("late", false)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("confirmRevert()")
    class ConfirmRevert {
        @Test
        @DisplayName("TR-12 clears revertPending on a FAILED transfer")
        void clears() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10");
            t.markFailed("x", true);
            t.confirmRevert();
            assertThat(t.isRevertPending()).isFalse();
        }

        @Test
        @DisplayName("TR-13 is harmless when nothing was pending")
        void harmless() {
            TransferRecord t = Fixtures.pendingTransfer(1, "10");
            t.markFailed("x", false);
            t.confirmRevert();
            assertThat(t.isRevertPending()).isFalse();
        }

        @Test
        @DisplayName("TR-14 refuses PENDING and COMPLETED transfers")
        void refusesNonFailed() {
            TransferRecord pending = Fixtures.pendingTransfer(1, "10");
            assertThatThrownBy(pending::confirmRevert).isInstanceOf(IllegalStateException.class);
            pending.markCompleted();
            assertThatThrownBy(pending::confirmRevert).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("equals()/hashCode() and TransferStatus.isActive()")
    class Identity {
        @Test
        @DisplayName("TR-15 equality is by reference")
        void equalityByReference() {
            TransferRecord a = Fixtures.pendingTransfer(1, "10");
            TransferRecord b = Fixtures.pendingTransfer(1, "10");
            assertThat(a).isEqualTo(a).isNotEqualTo(b).isNotEqualTo("other");
            assertThat(a.hashCode()).isEqualTo(a.getReference().hashCode());
        }

        @Test
        @DisplayName("TR-16 PENDING and COMPLETED are active, FAILED is not")
        void activeStatuses() {
            assertThat(TransferStatus.PENDING.isActive()).isTrue();
            assertThat(TransferStatus.COMPLETED.isActive()).isTrue();
            assertThat(TransferStatus.FAILED.isActive()).isFalse();
        }

        @Test
        @DisplayName("TR-17 touch() refreshes updatedAt")
        void touchUpdates() throws InterruptedException {
            TransferRecord t = Fixtures.pendingTransfer(1, "10");
            var before = t.getUpdatedAt();
            Thread.sleep(2);
            t.touch();
            assertThat(t.getUpdatedAt()).isAfter(before);
        }
    }
}
