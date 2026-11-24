package com.fintech.orgwise.treasury;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Treasury &amp; Ledger bounded context.
 *
 * <p>The treasury service owns the organisation's {@code TreasuryAccount} (the balance)
 * and the append-only ledger of {@code TransferRecord}s. It is the only service allowed
 * to move money, and does so through the disbursement saga
 * ({@link com.fintech.orgwise.treasury.service.DisbursementSaga}).</p>
 *
 * <p>Feign clients and scheduling are enabled in
 * {@link com.fintech.orgwise.treasury.config.ClientConfiguration} rather than here so that
 * test slices ({@code @WebMvcTest}, {@code @DataJpaTest}) do not have to build them.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TreasuryServiceApplication {

    /**
     * Starts the treasury service.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(TreasuryServiceApplication.class, args);
    }
}
