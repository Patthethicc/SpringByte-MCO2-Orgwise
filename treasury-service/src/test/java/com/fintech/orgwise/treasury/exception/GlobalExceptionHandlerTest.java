package com.fintech.orgwise.treasury.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers");

    @Test
    @DisplayName("GE-01 ResourceNotFoundException -> 404 with message and path")
    void notFound() {
        var r = handler.handleNotFound(new ResourceNotFoundException("Transfer x not found"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().message()).isEqualTo("Transfer x not found");
        assertThat(r.getBody().path()).isEqualTo("/api/v1/transfers");
    }

    @Test
    @DisplayName("GE-02 BusinessRuleException -> 409")
    void conflict() {
        var r = handler.handleBusinessRule(new BusinessRuleException("already disbursed"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().error()).isEqualTo("Conflict");
    }

    @Test
    @DisplayName("GE-03 InsufficientFundsException -> 422 with available/requested details")
    void insufficient() {
        var r = handler.handleInsufficientFunds(new InsufficientFundsException(new BigDecimal("5.00"), new BigDecimal("7.50")), request);
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(r.getBody().details()).containsEntry("available", "5.00").containsEntry("requested", "7.50");
    }

    @Test
    @DisplayName("GE-04 ServiceUnavailableException -> 503")
    void unavailable() {
        var r = handler.handleUnavailable(new ServiceUnavailableException("down", new RuntimeException()), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getBody().message()).isEqualTo("down");
    }

    @Test
    @DisplayName("GE-05 DisbursementFailedException -> its own status plus the transfer reference")
    void disbursementFailed() {
        var r503 = handler.handleDisbursementFailed(new DisbursementFailedException("m", "ref-1",
                HttpStatus.SERVICE_UNAVAILABLE, null), request);
        var r409 = handler.handleDisbursementFailed(new DisbursementFailedException("m", "ref-2",
                HttpStatus.CONFLICT, null), request);
        assertThat(r503.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r503.getBody().details()).containsEntry("transferReference", "ref-1");
        assertThat(r409.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("GE-06 validation errors -> 400 with one message per field")
    void validation() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "disburseRequestDTO");
        binding.rejectValue(null, "x");
        binding.addError(new org.springframework.validation.FieldError("disburseRequestDTO", "expenseId", "expenseId is required"));
        binding.addError(new org.springframework.validation.FieldError("disburseRequestDTO", "expenseId", "second message"));
        MethodParameter param = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
        var r = handler.handleValidation(new MethodArgumentNotValidException(param, binding), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().details()).containsExactly(java.util.Map.entry("expenseId", "expenseId is required"));
    }

    @Test
    @DisplayName("GE-07 malformed body, bad parameter type and IllegalArgumentException -> 400")
    void badRequests() {
        HttpInputMessage msg = new MockHttpInputMessage(new byte[0]);
        assertThat(handler.handleBadRequest(new HttpMessageNotReadableException("x", msg), request).getBody().message())
                .isEqualTo("Malformed JSON request body");
        var mismatch = new MethodArgumentTypeMismatchException("BOGUS", null, "status", null, null);
        assertThat(handler.handleBadRequest(mismatch, request).getBody().message())
                .isEqualTo("Invalid value for parameter 'status'");
        assertThat(handler.handleBadRequest(new IllegalArgumentException("bad id"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GE-08 AccessDeniedException -> 403, missing credentials -> 401")
    void security() {
        assertThat(handler.handleAccessDenied(new AccessDeniedException("no"), request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleUnauthenticated(new AuthenticationCredentialsNotFoundException("no"), request)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GE-09 unexpected exceptions -> 500 without leaking internals")
    void unexpected() {
        var r = handler.handleUnexpected(new IllegalStateException("db password is hunter2"), request);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().message()).isEqualTo("An unexpected error occurred").doesNotContain("hunter2");
    }
}
