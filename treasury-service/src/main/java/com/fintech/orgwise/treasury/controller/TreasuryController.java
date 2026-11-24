package com.fintech.orgwise.treasury.controller;

import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.dto.ApiResponseDTO;
import com.fintech.orgwise.treasury.dto.TreasuryAccountDTO;
import com.fintech.orgwise.treasury.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Treasury balance and the list of expenses waiting to be paid. */
@RestController
@RequestMapping("/api/v1/treasury")
@Tag(name = "Treasury", description = "Treasury balance and disbursement queue")
public class TreasuryController {

    private final TransferService transferService;

    /**
     * @param transferService ledger queries
     */
    public TreasuryController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * {@code GET /api/v1/treasury}: current balance. Finance head, president, admin.
     *
     * @return the main treasury account
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE_HEAD','PRESIDENT','ADMIN')")
    @Operation(summary = "Get the treasury balance")
    public ResponseEntity<ApiResponseDTO<TreasuryAccountDTO>> getTreasury() {
        return ResponseEntity.ok(ApiResponseDTO.ok("Treasury retrieved", transferService.getTreasuryAccount()));
    }

    /**
     * {@code GET /api/v1/treasury/disbursable}: approved expenses without an active transfer.
     * Finance head only. Returns 503 when expense-service is down.
     *
     * @return expenses that can be disbursed now
     */
    @GetMapping("/disbursable")
    @PreAuthorize("hasRole('FINANCE_HEAD')")
    @Operation(summary = "List approved expenses waiting for disbursement")
    public ResponseEntity<ApiResponseDTO<List<ExpenseSnapshotDTO>>> getDisbursable() {
        return ResponseEntity.ok(ApiResponseDTO.ok("Disbursable expenses retrieved",
                transferService.getDisbursableExpenses()));
    }
}
