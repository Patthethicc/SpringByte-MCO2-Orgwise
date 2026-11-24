package com.fintech.orgwise.treasury.controller;

import com.fintech.orgwise.treasury.dto.ApiResponseDTO;
import com.fintech.orgwise.treasury.dto.DisburseRequestDTO;
import com.fintech.orgwise.treasury.dto.PageResponseDTO;
import com.fintech.orgwise.treasury.dto.TransferRecordDTO;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import com.fintech.orgwise.treasury.security.CurrentOfficer;
import com.fintech.orgwise.treasury.service.DisbursementSaga;
import com.fintech.orgwise.treasury.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Disbursements and the transfer ledger. */
@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Disburse approved expenses and browse the ledger")
public class TransferController {

    private final DisbursementSaga disbursementSaga;
    private final TransferService transferService;

    /**
     * @param disbursementSaga saga orchestrator
     * @param transferService  ledger queries
     */
    public TransferController(DisbursementSaga disbursementSaga, TransferService transferService) {
        this.disbursementSaga = disbursementSaga;
        this.transferService = transferService;
    }

    /**
     * {@code POST /api/v1/transfers}: disburse an approved expense. Finance head only.
     *
     * @param request the expense to pay
     * @return 201 with the COMPLETED transfer
     */
    @PostMapping
    @PreAuthorize("hasRole('FINANCE_HEAD')")
    @Operation(summary = "Disburse an approved expense (runs the disbursement saga)")
    public ResponseEntity<ApiResponseDTO<TransferRecordDTO>> disburse(@Valid @RequestBody DisburseRequestDTO request) {
        TransferRecordDTO transfer = disbursementSaga.disburse(request.expenseId(), CurrentOfficer.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.ok("Funds disbursed", transfer));
    }

    /**
     * {@code GET /api/v1/transfers}: the whole ledger, newest first. Finance head, president, admin.
     *
     * @param status   optional status filter
     * @param pageable page and size ({@code ?page=0&size=20})
     * @return a page of transfers
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE_HEAD','PRESIDENT','ADMIN')")
    @Operation(summary = "List all transfers")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<TransferRecordDTO>>> getTransfers(
            @RequestParam(required = false) TransferStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponseDTO.ok("Transfers retrieved",
                transferService.getTransfers(status, pageable)));
    }

    /**
     * {@code GET /api/v1/transfers/me}: transfers paid to the caller. Any authenticated officer.
     *
     * @param pageable page and size
     * @return a page of the caller's transfers
     */
    @GetMapping("/me")
    @Operation(summary = "List transfers paid to me")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<TransferRecordDTO>>> getMyTransfers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponseDTO.ok("Transfers retrieved",
                transferService.getTransfersForRecipient(CurrentOfficer.get().officerId(), pageable)));
    }

    /**
     * {@code GET /api/v1/transfers/{reference}}: one transfer (privileged roles or its recipient).
     *
     * @param reference transfer reference
     * @return the transfer
     */
    @GetMapping("/{reference}")
    @Operation(summary = "Get one transfer by reference")
    public ResponseEntity<ApiResponseDTO<TransferRecordDTO>> getTransfer(@PathVariable String reference) {
        return ResponseEntity.ok(ApiResponseDTO.ok("Transfer retrieved",
                transferService.getTransfer(reference, CurrentOfficer.get())));
    }
}
