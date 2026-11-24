package com.fintech.orgwise.treasury.client;

import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the Resilience4J breaker opens on outages but not on business (4xx) answers. */
@SpringBootTest(properties = {
        "resilience4j.circuitbreaker.configs.default.sliding-window-size=4",
        "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.configs.default.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=60s"
})
class ExpenseClientCircuitBreakerTest {

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

    @Test
    @DisplayName("CB-01 after repeated 500s the circuit opens and calls stop reaching expense-service")
    void opensOnOutage() {
        EXPENSE.stubFor(get(urlPathEqualTo("/api/v1/internal/expenses")).willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.getExpensesByStatus("APPROVED")).isInstanceOf(ServiceUnavailableException.class);
        }
        int before = EXPENSE.findAll(getRequestedFor(urlPathEqualTo("/api/v1/internal/expenses"))).size();
        assertThatThrownBy(() -> client.getExpensesByStatus("APPROVED"))
                .isInstanceOf(ServiceUnavailableException.class).hasCauseInstanceOf(CallNotPermittedException.class);
        int after = EXPENSE.findAll(getRequestedFor(urlPathEqualTo("/api/v1/internal/expenses"))).size();
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("CB-02 4xx business answers do not open the circuit")
    void ignoresBusinessErrors() {
        EXPENSE.stubFor(get(urlEqualTo("/api/v1/internal/expenses/77")).willReturn(aResponse().withStatus(409)));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.getExpense(77L)).isInstanceOf(BusinessRuleException.class);
        }
        org.assertj.core.api.Assertions.assertThat(
                EXPENSE.findAll(getRequestedFor(urlEqualTo("/api/v1/internal/expenses/77")))).hasSize(5);
    }
}
