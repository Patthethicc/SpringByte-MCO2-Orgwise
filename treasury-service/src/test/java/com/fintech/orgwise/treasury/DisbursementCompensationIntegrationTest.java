package com.fintech.orgwise.treasury;

import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import com.fintech.orgwise.treasury.repository.TransferRepository;
import com.fintech.orgwise.treasury.repository.TreasuryRepository;
import com.fintech.orgwise.treasury.support.TestTokens;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end saga tests (replaces MCO1's {@code TransferRollbackIntegrationTest}): the real
 * Spring context, H2, the real Feign client + circuit breaker, and WireMock as expense-service.
 * Checks that every failure path restores the balance and leaves a FAILED transfer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisbursementCompensationIntegrationTest {

    private static final WireMockServer EXPENSE = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        EXPENSE.start();
    }

    @DynamicPropertySource
    static void expenseUrl(DynamicPropertyRegistry registry) {
        registry.add("orgwise.clients.expense-service.url", EXPENSE::baseUrl);
    }

    @AfterAll
    static void stop() {
        EXPENSE.stop();
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TreasuryRepository treasuryRepository;
    @Autowired
    private TransferRepository transferRepository;

    private static final BigDecimal OPENING = new BigDecimal("10000.00");
    private final String financeToken = TestTokens.financeHead();

    @BeforeEach
    void reset() {
        EXPENSE.resetAll();
        transferRepository.deleteAll();
        var account = treasuryRepository.findFirstByOrderByIdAsc().orElseThrow();
        BigDecimal diff = OPENING.subtract(account.getBalance());
        if (diff.signum() > 0) {
            account.credit(diff);
        } else if (diff.signum() < 0) {
            account.debit(diff.negate());
        }
        treasuryRepository.save(account);
    }

    private static void stubApprovedExpense(long id, String amount) {
        EXPENSE.stubFor(get(urlEqualTo("/api/v1/internal/expenses/" + id)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": %d, "title": "Venue deposit", "amount": %s, "status": "APPROVED",
                         "requesterId": 7, "requesterName": "Assoc Member", "eventId": 3}
                        """.formatted(id, amount))));
    }

    private static void stubRevertOk(long id) {
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/" + id + "/revert-disburse"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": " + id + ", \"status\": \"APPROVED\"}")));
    }

    private BigDecimal balance() {
        return treasuryRepository.findFirstByOrderByIdAsc().orElseThrow().getBalance();
    }

    private org.springframework.test.web.servlet.ResultActions disburse(long expenseId) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/transfers")
                .header("Authorization", financeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expenseId\": " + expenseId + "}"));
    }

    @Test
    @DisplayName("SG-01 Happy path: balance drops, transfer COMPLETED, expense-service told with the reference and the user's token")
    void happyPathCompletesTransfer() throws Exception {
        stubApprovedExpense(11, "2500.00");
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/11/disburse")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": 11, \"status\": \"DISBURSED\"}")));

        disburse(11).andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.recipientName").value("Assoc Member"));

        assertThat(balance()).isEqualByComparingTo("7500.00");
        List<TransferRecord> all = transferRepository.findAll();
        assertThat(all).singleElement().extracting(TransferRecord::getStatus).isEqualTo(TransferStatus.COMPLETED);
        EXPENSE.verify(postRequestedFor(urlEqualTo("/api/v1/internal/expenses/11/disburse"))
                .withHeader("Authorization", equalTo(financeToken))
                .withRequestBody(matchingJsonPath("$.transferReference", equalTo(all.get(0).getReference()))));
    }

    @Test
    @DisplayName("SG-02 Disburse step returns 500: balance restored, transfer FAILED, 503 returned, revert attempted")
    void serverErrorOnDisburseIsCompensated() throws Exception {
        stubApprovedExpense(12, "4000.00");
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/12/disburse"))
                .willReturn(aResponse().withStatus(500)));
        stubRevertOk(12);

        disburse(12).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.details.transferReference").isNotEmpty());

        assertThat(balance()).isEqualByComparingTo(OPENING);
        TransferRecord t = transferRepository.findAll().get(0);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(t.isRevertPending()).isFalse();
        EXPENSE.verify(postRequestedFor(urlEqualTo("/api/v1/internal/expenses/12/revert-disburse")));
    }

    @Test
    @DisplayName("SG-03 Disburse step times out and revert fails: balance restored, FAILED with revertPending")
    void timeoutAndRevertFailureLeavesRevertPending() throws Exception {
        stubApprovedExpense(13, "1000.00");
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/13/disburse"))
                .willReturn(aResponse().withFixedDelay(3000).withStatus(200)));
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/13/revert-disburse"))
                .willReturn(aResponse().withStatus(503)));

        disburse(13).andExpect(status().isServiceUnavailable());

        assertThat(balance()).isEqualByComparingTo(OPENING);
        TransferRecord t = transferRepository.findAll().get(0);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(t.isRevertPending()).isTrue();
    }

    @Test
    @DisplayName("SG-04 Expense-service rejects the disburse step with 409: compensated and 409 returned")
    void conflictOnDisburseIsCompensated() throws Exception {
        stubApprovedExpense(14, "500.00");
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/14/disburse")).willReturn(aResponse()
                .withStatus(409).withHeader("Content-Type", "application/json")
                .withBody("{\"status\":409,\"message\":\"Expense is no longer APPROVED\"}")));
        stubRevertOk(14);

        disburse(14).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no longer APPROVED")));

        assertThat(balance()).isEqualByComparingTo(OPENING);
        assertThat(transferRepository.findAll().get(0).getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    @DisplayName("SG-05 Expense-service down before any money moved: 503, no transfer row, balance unchanged")
    void expenseDownFailsClosed() throws Exception {
        EXPENSE.stubFor(get(urlEqualTo("/api/v1/internal/expenses/15")).willReturn(aResponse()
                .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        disburse(15).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No money was moved")));

        assertThat(balance()).isEqualByComparingTo(OPENING);
        assertThat(transferRepository.count()).isZero();
    }

    @Test
    @DisplayName("SG-06 Second disbursement of the same expense is rejected with 409 and does not debit again")
    void doubleDisbursementRejected() throws Exception {
        stubApprovedExpense(16, "1500.00");
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/16/disburse")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"id\":16,\"status\":\"DISBURSED\"}")));

        disburse(16).andExpect(status().isCreated());
        disburse(16).andExpect(status().isConflict());

        assertThat(balance()).isEqualByComparingTo("8500.00");
        assertThat(transferRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SG-07 Amount above the balance: 422, no transfer row, balance unchanged")
    void insufficientFunds() throws Exception {
        stubApprovedExpense(17, "999999.00");

        disburse(17).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details.available").value("10000.00"));

        assertThat(balance()).isEqualByComparingTo(OPENING);
        assertThat(transferRepository.count()).isZero();
    }
}
