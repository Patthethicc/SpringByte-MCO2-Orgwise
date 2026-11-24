package com.fintech.orgwise.treasury.client;

import com.fintech.orgwise.treasury.client.dto.DisbursementCommandDTO;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseClientFallbackFactoryTest {

    private final ExpenseClientFallbackFactory factory = new ExpenseClientFallbackFactory();
    private static final DisbursementCommandDTO CMD = new DisbursementCommandDTO("ref", BigDecimal.ONE, 2L, "finance");

    private static FeignException http(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://expense/x", new HashMap<>(), null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("ExpenseClient#getExpense(Long)", feign.Response.builder()
                .status(status).reason("r").request(request).headers(new HashMap<>())
                .body(body == null ? null : body.getBytes(StandardCharsets.UTF_8)).build());
    }

    @Test
    @DisplayName("FB-01 404 becomes ResourceNotFoundException with the remote message")
    void notFound() {
        ExpenseClient client = factory.create(http(404, "{\"message\":\"Expense 9 not found\"}"));
        assertThatThrownBy(() -> client.getExpense(9L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Expense 9 not found");
    }

    @Test
    @DisplayName("FB-02 404 without a body gets a default message")
    void notFoundNoBody() {
        assertThatThrownBy(() -> factory.create(http(404, null)).getExpense(9L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Expense not found");
    }

    @Test
    @DisplayName("FB-03 400/409/422 become BusinessRuleException")
    void businessErrors() {
        assertThatThrownBy(() -> factory.create(http(409, "{\"message\":\"Already disbursed\"}")).markDisbursed(1L, CMD))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Already disbursed");
        assertThatThrownBy(() -> factory.create(http(422, "")).markDisbursed(1L, CMD))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("HTTP 422");
        assertThatThrownBy(() -> factory.create(http(400, "not json")).getExpensesByStatus("X"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("FB-04 401/403 become AccessDeniedException")
    void authErrors() {
        assertThatThrownBy(() -> factory.create(http(403, null)).getExpense(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> factory.create(http(401, null)).getExpense(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("FB-05 5xx becomes ServiceUnavailableException (fail closed)")
    void serverErrors() {
        assertThatThrownBy(() -> factory.create(http(500, null)).revertDisbursement(1L, CMD))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(ExpenseClientFallbackFactory.UNAVAILABLE_MESSAGE);
        assertThatThrownBy(() -> factory.create(http(503, null)).getExpense(1L))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("FB-06 connection errors, open circuit and unknown causes become ServiceUnavailableException")
    void outages() {
        Request request = Request.create(Request.HttpMethod.GET, "http://expense/x", new HashMap<>(), null,
                StandardCharsets.UTF_8, null);
        RetryableException refused = new RetryableException(-1, "Connection refused", Request.HttpMethod.GET,
                (Long) null, request);
        assertThatThrownBy(() -> factory.create(refused).getExpense(1L)).isInstanceOf(ServiceUnavailableException.class);

        CallNotPermittedException open = CallNotPermittedException
                .createCallNotPermittedException(CircuitBreaker.ofDefaults("expense"));
        assertThatThrownBy(() -> factory.create(open).getExpensesByStatus("APPROVED"))
                .isInstanceOf(ServiceUnavailableException.class).hasCause(open);

        assertThatThrownBy(() -> factory.create(null).getExpense(1L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("FB-07 the fallback never returns data: every method throws")
    void neverReturnsData() {
        ExpenseClient client = factory.create(new RuntimeException("boom"));
        assertThatThrownBy(() -> client.getExpense(1L)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> client.getExpensesByStatus("APPROVED")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> client.markDisbursed(1L, CMD)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> client.revertDisbursement(1L, CMD)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("FB-08 translate() keeps escaped quotes in remote messages")
    void escapedQuotes() {
        RuntimeException e = ExpenseClientFallbackFactory.translate("op",
                http(409, "{\"message\":\"Status is \\\"PENDING\\\"\"}"));
        assertThat(e).isInstanceOf(BusinessRuleException.class).hasMessage("Status is \"PENDING\"");
    }
}
