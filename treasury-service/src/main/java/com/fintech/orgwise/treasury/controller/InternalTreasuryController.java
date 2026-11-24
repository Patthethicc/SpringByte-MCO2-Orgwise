package com.fintech.orgwise.treasury.controller;

import com.fintech.orgwise.treasury.dto.TreasuryAggregateDTO;
import com.fintech.orgwise.treasury.service.TransferService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. Blocked at the gateway; reachable only inside the Docker network
 * with a valid JWT. Returns raw DTOs (no envelope) so Feign clients can decode them directly.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/treasury")
public class InternalTreasuryController {

    private final TransferService transferService;

    /**
     * @param transferService ledger queries
     */
    public InternalTreasuryController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * {@code GET /api/v1/internal/treasury/aggregate}: totals for reporting-service.
     *
     * @return the treasury aggregate
     */
    @GetMapping("/aggregate")
    public TreasuryAggregateDTO getAggregate() {
        return transferService.getAggregate();
    }
}
