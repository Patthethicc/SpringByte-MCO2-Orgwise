package com.fintech.orgwise.treasury.service;

import com.fintech.orgwise.treasury.config.TreasuryProperties;
import com.fintech.orgwise.treasury.entity.TreasuryAccount;
import com.fintech.orgwise.treasury.repository.TreasuryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the main treasury account on first start-up (MCO1's treasury bootstrap).
 * Existing accounts are never modified, so restarting does not reset the balance.
 */
@Configuration
public class TreasuryBootstrapConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TreasuryBootstrapConfiguration.class);

    /**
     * @param repository account persistence
     * @param properties name, opening balance and currency
     * @return runner that seeds the account if none exists
     */
    @Bean
    public ApplicationRunner treasuryBootstrap(TreasuryRepository repository, TreasuryProperties properties) {
        return args -> bootstrap(repository, properties);
    }

    /**
     * Seeds the account if the table is empty.
     *
     * @param repository account persistence
     * @param properties name, opening balance and currency
     * @return {@code true} if an account was created
     */
    static boolean bootstrap(TreasuryRepository repository, TreasuryProperties properties) {
        if (repository.count() > 0) {
            log.info("Treasury account already exists, bootstrap skipped");
            return false;
        }
        repository.save(new TreasuryAccount(properties.accountName(), properties.initialBalance(),
                properties.currency()));
        log.info("Bootstrapped treasury account '{}' with {} {}", properties.accountName(),
                properties.initialBalance(), properties.currency());
        return true;
    }
}
