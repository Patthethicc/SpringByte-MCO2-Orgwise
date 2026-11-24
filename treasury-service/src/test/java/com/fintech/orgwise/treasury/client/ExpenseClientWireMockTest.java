package com.fintech.orgwise.treasury.client;

import com.fintech.orgwise.treasury.client.dto.DisbursementCommandDTO;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contract test of {@link ExpenseClient} against a WireMock stand-in for expense-service. */
@SpringBootTest
class ExpenseClientWireMockTest {

    private static final WireMockServer EXPENSE = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        EXPENSE.start();
    }

    @DynamicPropertySource
    static void url(DynamicPropertyRegistry registry) {
        registry.add("orgwise.clients.expense-service.url", EXPENSE::baseUrl);
    }

    @AfterAll
    static void stop() {
        EXPENSE.stop();
    }

    @Autowired
    private ExpenseClient client;

    @BeforeEach
    void reset() {
        EXPENSE.resetAll();
    }

    private static final String JSON = "application/json";

    @Test
    @DisplayName("EC-01 getExpense decodes the snapshot and ignores unknown fields")
    void getExpense() {
        EXPENSE.stubFor(get(urlEqualTo("/api/v1/internal/expenses/5")).willReturn(aResponse().withHeader("Content-Type", JSON)
                .withBody("{\"id\":5,\"title\":\"Food\",\"amount\":120.50,\"status\":\"APPROVED\",\"requesterId\":7,"
                        + "\"requesterName\":\"A\",\"eventId\":3,\"level\":\"MINOR\",\"extra\":{\"x\":1}}")));
        ExpenseSnapshotDTO e = client.getExpense(5L);
        assertThat(e.amount()).isEqualByComparingTo("120.50");
        assertThat(e.isApproved()).isTrue();
        assertThat(e.eventId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("EC-02 getExpense maps 404 and 500 through the fallback")
    void getExpenseErrors() {
        EXPENSE.stubFor(get(urlEqualTo("/api/v1/internal/expenses/6")).willReturn(aResponse().withStatus(404)));
        EXPENSE.stubFor(get(urlEqualTo("/api/v1/internal/expenses/7")).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> client.getExpense(6L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> client.getExpense(7L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("EC-03 with no HTTP request in scope, calls carry a SERVICE token")
    void serviceToken() {
        EXPENSE.stubFor(get(urlEqualTo("/api/v1/internal/expenses/8")).willReturn(aResponse().withHeader("Content-Type", JSON)
                .withBody("{\"id\":8,\"status\":\"APPROVED\"}")));
        client.getExpense(8L);
        EXPENSE.verify(getRequestedFor(urlEqualTo("/api/v1/internal/expenses/8"))
                .withHeader("Authorization", matching("Bearer [\\w-]+\\.[\\w-]+\\.[\\w-]+")));
    }

    @Test
    @DisplayName("EC-04 getExpensesByStatus sends the status query parameter and decodes a list")
    void byStatus() {
        EXPENSE.stubFor(get(urlPathEqualTo("/api/v1/internal/expenses")).withQueryParam("status", equalTo("APPROVED"))
                .willReturn(aResponse().withHeader("Content-Type", JSON)
                        .withBody("[{\"id\":1,\"status\":\"APPROVED\"},{\"id\":2,\"status\":\"APPROVED\"}]")));
        List<ExpenseSnapshotDTO> list = client.getExpensesByStatus("APPROVED");
        assertThat(list).extracting(ExpenseSnapshotDTO::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("EC-05 getExpensesByStatus returns an empty list and maps connection faults to 503")
    void byStatusEdge() {
        EXPENSE.stubFor(get(urlPathEqualTo("/api/v1/internal/expenses")).withQueryParam("status", equalTo("NONE"))
                .willReturn(aResponse().withHeader("Content-Type", JSON).withBody("[]")));
        assertThat(client.getExpensesByStatus("NONE")).isEmpty();
        EXPENSE.stubFor(get(urlPathEqualTo("/api/v1/internal/expenses")).withQueryParam("status", equalTo("BROKEN"))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.EMPTY_RESPONSE)));
        assertThatThrownBy(() -> client.getExpensesByStatus("BROKEN")).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("EC-06 markDisbursed POSTs the command JSON")
    void markDisbursed() {
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/9/disburse")).willReturn(aResponse()
                .withHeader("Content-Type", JSON).withBody("{\"id\":9,\"status\":\"DISBURSED\",\"transferReference\":\"r-1\"}")));
        ExpenseSnapshotDTO e = client.markDisbursed(9L, new DisbursementCommandDTO("r-1", new BigDecimal("10.00"), 2L, "finance"));
        assertThat(e.isDisbursedBy("r-1")).isTrue();
        EXPENSE.verify(postRequestedFor(urlEqualTo("/api/v1/internal/expenses/9/disburse")).withRequestBody(equalToJson(
                "{\"transferReference\":\"r-1\",\"amount\":10.00,\"disbursedById\":2,\"disbursedByUsername\":\"finance\"}")));
    }

    @Test
    @DisplayName("EC-07 markDisbursed maps 409 to BusinessRuleException and a slow response to 503")
    void markDisbursedErrors() {
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/10/disburse")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", JSON).withBody("{\"message\":\"Expense already DISBURSED\"}")));
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/11/disburse"))
                .willReturn(aResponse().withFixedDelay(2500)));
        var cmd = new DisbursementCommandDTO("r", BigDecimal.ONE, 2L, "finance");
        assertThatThrownBy(() -> client.markDisbursed(10L, cmd)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("Expense already DISBURSED");
        assertThatThrownBy(() -> client.markDisbursed(11L, cmd)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("EC-08 revertDisbursement POSTs to revert-disburse")
    void revert() {
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/12/revert-disburse")).willReturn(aResponse()
                .withHeader("Content-Type", JSON).withBody("{\"id\":12,\"status\":\"APPROVED\"}")));
        assertThat(client.revertDisbursement(12L, new DisbursementCommandDTO("r", BigDecimal.ONE, null, "treasury-service"))
                .status()).isEqualTo("APPROVED");
        EXPENSE.verify(postRequestedFor(urlEqualTo("/api/v1/internal/expenses/12/revert-disburse")));
    }

    @Test
    @DisplayName("EC-09 revertDisbursement maps 404 and 503")
    void revertErrors() {
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/13/revert-disburse")).willReturn(aResponse().withStatus(404)));
        EXPENSE.stubFor(post(urlEqualTo("/api/v1/internal/expenses/14/revert-disburse")).willReturn(aResponse().withStatus(503)));
        var cmd = new DisbursementCommandDTO("r", BigDecimal.ONE, null, "treasury-service");
        assertThatThrownBy(() -> client.revertDisbursement(13L, cmd)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> client.revertDisbursement(14L, cmd)).isInstanceOf(ServiceUnavailableException.class);
    }
}
