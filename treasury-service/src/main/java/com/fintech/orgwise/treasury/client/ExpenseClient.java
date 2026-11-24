package com.fintech.orgwise.treasury.client;

import com.fintech.orgwise.treasury.client.dto.DisbursementCommandDTO;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * OpenFeign client for expense-service's internal API (see {@code docs/internal-api-contracts.md}).
 *
 * <p>Resolved through Eureka ({@code lb://expense-service}) unless
 * {@code orgwise.clients.expense-service.url} is set (tests point it at WireMock). Every call
 * is wrapped in a Resilience4J circuit breaker; {@link ExpenseClientFallbackFactory} turns
 * failures into treasury exceptions so money-moving flows fail closed.</p>
 */
@FeignClient(name = "expense-service",
        url = "${orgwise.clients.expense-service.url:}",
        path = "/api/v1/internal/expenses",
        fallbackFactory = ExpenseClientFallbackFactory.class)
public interface ExpenseClient {

    /**
     * @param id expense ID
     * @return the expense snapshot
     */
    @GetMapping("/{id}")
    ExpenseSnapshotDTO getExpense(@PathVariable("id") Long id);

    /**
     * @param status expense status, e.g. {@code APPROVED}
     * @return expenses in that status
     */
    @GetMapping
    List<ExpenseSnapshotDTO> getExpensesByStatus(@RequestParam("status") String status);

    /**
     * Saga step 2: marks the expense {@code DISBURSED}. Idempotent on the transfer reference.
     *
     * @param id      expense ID
     * @param command transfer reference and amount
     * @return the updated expense
     */
    @PostMapping("/{id}/disburse")
    ExpenseSnapshotDTO markDisbursed(@PathVariable("id") Long id, @RequestBody DisbursementCommandDTO command);

    /**
     * Compensation: moves a {@code DISBURSED} expense back to {@code APPROVED} if (and only if) it
     * was disbursed by this reference. A no-op when the expense was never marked. Idempotent.
     *
     * @param id      expense ID
     * @param command transfer reference and amount
     * @return the expense after the revert
     */
    @PostMapping("/{id}/revert-disburse")
    ExpenseSnapshotDTO revertDisbursement(@PathVariable("id") Long id, @RequestBody DisbursementCommandDTO command);
}
