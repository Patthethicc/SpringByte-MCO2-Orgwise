package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.config.TreasuryProperties;
import com.fintech.orgwise.treasury.entity.TreasuryAccount;
import com.fintech.orgwise.treasury.repository.TreasuryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreasuryBootstrapConfigurationTest {

    @Mock
    private TreasuryRepository repository;

    @Test
    @DisplayName("TB-01 creates the account with the configured name, balance and currency when none exists")
    void createsAccount() {
        when(repository.count()).thenReturn(0L);
        var props = new TreasuryProperties("Org Treasury", new BigDecimal("2500.00"), "PHP", null);

        assertThat(TreasuryBootstrapConfiguration.bootstrap(repository, props)).isTrue();

        ArgumentCaptor<TreasuryAccount> saved = ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Org Treasury");
        assertThat(saved.getValue().getBalance()).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("TB-02 does not touch an existing account (restart keeps the balance)")
    void skipsExisting() {
        when(repository.count()).thenReturn(1L);
        assertThat(TreasuryBootstrapConfiguration.bootstrap(repository,
                new TreasuryProperties(null, null, null, null))).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("TB-03 falls back to defaults (Main Treasury, 100000.00, PHP) when nothing is configured")
    void defaults() {
        when(repository.count()).thenReturn(0L);
        TreasuryBootstrapConfiguration.bootstrap(repository, new TreasuryProperties(" ", null, "", null));
        ArgumentCaptor<TreasuryAccount> saved = ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Main Treasury");
        assertThat(saved.getValue().getBalance()).isEqualByComparingTo("100000.00");
        assertThat(saved.getValue().getCurrency()).isEqualTo("PHP");
    }

    @Test
    @DisplayName("TB-04 the ApplicationRunner bean runs the bootstrap")
    void runnerBean() throws Exception {
        when(repository.count()).thenReturn(0L);
        new TreasuryBootstrapConfiguration()
                .treasuryBootstrap(repository, new TreasuryProperties(null, BigDecimal.ONE, null, null))
                .run(null);
        verify(repository).save(any(TreasuryAccount.class));
    }
}
