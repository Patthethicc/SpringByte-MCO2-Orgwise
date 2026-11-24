package com.fintech.orgwise.treasury.entity;

import com.fintech.orgwise.treasury.exception.InsufficientFundsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreasuryAccountTest {

    private static TreasuryAccount account(String balance) {
        return new TreasuryAccount("Main", new BigDecimal(balance), "PHP");
    }

    @Nested
    @DisplayName("constructor")
    class Create {
        @Test
        @DisplayName("TA-01 creates an account with the opening balance")
        void creates() {
            TreasuryAccount a = account("500.00");
            assertThat(a.getBalance()).isEqualByComparingTo("500.00");
            assertThat(a.getName()).isEqualTo("Main");
            assertThat(a.getCurrency()).isEqualTo("PHP");
            assertThat(a.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("TA-02 allows a zero opening balance")
        void zeroAllowed() {
            assertThat(account("0").getBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("TA-03 rejects negative balance, blank name or blank currency")
        void rejectsInvalid() {
            assertThatThrownBy(() -> account("-1")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TreasuryAccount(" ", BigDecimal.ONE, "PHP"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TreasuryAccount("Main", BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TreasuryAccount("Main", null, "PHP"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("debit()")
    class Debit {
        @Test
        @DisplayName("TA-04 subtracts the amount")
        void subtracts() {
            TreasuryAccount a = account("1000.00");
            a.debit(new BigDecimal("250.50"));
            assertThat(a.getBalance()).isEqualByComparingTo("749.50");
        }

        @Test
        @DisplayName("TA-05 can debit the exact balance down to zero")
        void exactBalance() {
            TreasuryAccount a = account("100.00");
            a.debit(new BigDecimal("100.00"));
            assertThat(a.getBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("TA-06 throws InsufficientFundsException and leaves the balance unchanged")
        void insufficient() {
            TreasuryAccount a = account("100.00");
            assertThatThrownBy(() -> a.debit(new BigDecimal("100.01")))
                    .isInstanceOfSatisfying(InsufficientFundsException.class, e -> {
                        assertThat(e.getAvailable()).isEqualByComparingTo("100.00");
                        assertThat(e.getRequested()).isEqualByComparingTo("100.01");
                    });
            assertThat(a.getBalance()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("TA-07 rejects zero, negative and null amounts")
        void rejectsNonPositive() {
            TreasuryAccount a = account("100.00");
            assertThatThrownBy(() -> a.debit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> a.debit(new BigDecimal("-5"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> a.debit(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("credit()")
    class Credit {
        @Test
        @DisplayName("TA-08 adds the amount")
        void adds() {
            TreasuryAccount a = account("10.00");
            a.credit(new BigDecimal("5.25"));
            assertThat(a.getBalance()).isEqualByComparingTo("15.25");
        }

        @Test
        @DisplayName("TA-09 debit followed by credit of the same amount restores the balance")
        void roundTrip() {
            TreasuryAccount a = account("900.00");
            a.debit(new BigDecimal("300.00"));
            a.credit(new BigDecimal("300.00"));
            assertThat(a.getBalance()).isEqualByComparingTo("900.00");
        }

        @Test
        @DisplayName("TA-10 rejects zero, negative and null amounts")
        void rejectsNonPositive() {
            TreasuryAccount a = account("10.00");
            assertThatThrownBy(() -> a.credit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> a.credit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> a.credit(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("TA-11 unsaved accounts are only equal to themselves")
    void equality() {
        TreasuryAccount a = account("1");
        assertThat(a).isEqualTo(a).isNotEqualTo(account("1")).isNotEqualTo(null);
        assertThat(a.hashCode()).isEqualTo(TreasuryAccount.class.hashCode());
    }
}
