package com.fintech.orgwise.treasury.controller;

import com.fintech.orgwise.treasury.dto.TreasuryAccountDTO;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import com.fintech.orgwise.treasury.service.TransferService;
import com.fintech.orgwise.treasury.support.Fixtures;
import com.fintech.orgwise.treasury.support.TestTokens;
import com.fintech.orgwise.treasury.support.WebSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TreasuryController.class)
@WebSliceTest
class TreasuryControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TransferService transferService;

    private static TreasuryAccountDTO account() {
        return new TreasuryAccountDTO(1L, "Main Treasury", new BigDecimal("9500.00"), "PHP", Instant.now());
    }

    @Test
    @DisplayName("TC-01 GET /treasury returns the balance to the finance head")
    void balanceForFinance() throws Exception {
        when(transferService.getTreasuryAccount()).thenReturn(account());
        mvc.perform(get("/api/v1/treasury").header("Authorization", TestTokens.financeHead()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(9500.00))
                .andExpect(jsonPath("$.data.currency").value("PHP"));
    }

    @Test
    @DisplayName("TC-02 GET /treasury is allowed for the president, forbidden for an associate")
    void balanceRoles() throws Exception {
        when(transferService.getTreasuryAccount()).thenReturn(account());
        mvc.perform(get("/api/v1/treasury").header("Authorization", TestTokens.president())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/treasury").header("Authorization", TestTokens.associate()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("TC-03 GET /treasury without a token is 401; missing account is 404")
    void balanceErrors() throws Exception {
        mvc.perform(get("/api/v1/treasury")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Bearer")));
        when(transferService.getTreasuryAccount()).thenThrow(new ResourceNotFoundException("Treasury account has not been initialised"));
        mvc.perform(get("/api/v1/treasury").header("Authorization", TestTokens.financeHead()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-04 GET /treasury/disbursable lists approved expenses for the finance head")
    void disbursable() throws Exception {
        when(transferService.getDisbursableExpenses()).thenReturn(List.of(Fixtures.expense(5, "300.00", "APPROVED")));
        mvc.perform(get("/api/v1/treasury/disbursable").header("Authorization", TestTokens.financeHead()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].requesterName").value("Assoc Member"));
    }

    @Test
    @DisplayName("TC-05 GET /treasury/disbursable is finance-head only (president gets 403)")
    void disbursableRole() throws Exception {
        mvc.perform(get("/api/v1/treasury/disbursable").header("Authorization", TestTokens.president()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-06 GET /treasury/disbursable returns 503 when expense-service is down")
    void disbursableUnavailable() throws Exception {
        when(transferService.getDisbursableExpenses()).thenThrow(new ServiceUnavailableException("Expense service is unavailable"));
        mvc.perform(get("/api/v1/treasury/disbursable").header("Authorization", TestTokens.financeHead()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Expense service is unavailable"));
    }
}
