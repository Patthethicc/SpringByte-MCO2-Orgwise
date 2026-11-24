package com.fintech.orgwise.treasury.repository;

import com.fintech.orgwise.treasury.entity.TreasuryAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TreasuryRepositoryTest {

    @Autowired
    private TreasuryRepository repository;
    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("TRR-01 findFirstByOrderByIdAsc returns the first account created")
    void findFirstReturnsOldest() {
        TreasuryAccount first = repository.save(new TreasuryAccount("Main", new BigDecimal("1.00"), "PHP"));
        repository.save(new TreasuryAccount("Reserve", new BigDecimal("2.00"), "PHP"));
        assertThat(repository.findFirstByOrderByIdAsc()).get().extracting(TreasuryAccount::getId)
                .isEqualTo(first.getId());
    }

    @Test
    @DisplayName("TRR-02 findFirstByOrderByIdAsc is empty before bootstrap")
    void findFirstEmpty() {
        repository.deleteAll();
        assertThat(repository.findFirstByOrderByIdAsc()).isEmpty();
    }

    @Test
    @DisplayName("TRR-03 findByIdForUpdate loads the account under a row lock")
    void findForUpdate() {
        TreasuryAccount saved = repository.save(new TreasuryAccount("Main", new BigDecimal("50.00"), "PHP"));
        em.flush();
        em.clear();
        assertThat(repository.findByIdForUpdate(saved.getId())).get()
                .satisfies(a -> assertThat(a.getBalance()).isEqualByComparingTo("50.00"));
    }

    @Test
    @DisplayName("TRR-04 findByIdForUpdate is empty for an unknown ID")
    void findForUpdateUnknown() {
        assertThat(repository.findByIdForUpdate(987654L)).isEmpty();
    }

    @Test
    @DisplayName("TRR-05 balance changes are persisted with 2-decimal precision")
    void persistsBalance() {
        TreasuryAccount saved = repository.save(new TreasuryAccount("Main", new BigDecimal("100.00"), "PHP"));
        saved.debit(new BigDecimal("33.33"));
        repository.saveAndFlush(saved);
        em.clear();
        assertThat(repository.findById(saved.getId()).orElseThrow().getBalance()).isEqualByComparingTo("66.67");
    }
}
