package com.fintech.orgwise.treasury.controller;

import com.fintech.orgwise.treasury.dto.EventDisbursementDTO;
import com.fintech.orgwise.treasury.dto.TreasuryAggregateDTO;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.security.JwtTokenService;
import com.fintech.orgwise.treasury.service.TransferService;
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

@WebMvcTest(InternalTreasuryController.class)
@WebSliceTest
class InternalTreasuryControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenService tokenService;
    @MockitoBean
    private TransferService transferService;

    private static TreasuryAggregateDTO aggregate() {
        return new TreasuryAggregateDTO(new BigDecimal("7000.00"), "PHP", new BigDecimal("3000.00"), 2, 0, 1,
                List.of(new EventDisbursementDTO(3L, new BigDecimal("3000.00"), 2)), Instant.now());
    }

    @Test
    @DisplayName("IT-01 returns the raw aggregate (no envelope) for a forwarded user token")
    void aggregateForUser() throws Exception {
        when(transferService.getAggregate()).thenReturn(aggregate());
        mvc.perform(get("/api/v1/internal/treasury/aggregate").header("Authorization", TestTokens.financeHead()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(7000.00))
                .andExpect(jsonPath("$.totalDisbursed").value(3000.00))
                .andExpect(jsonPath("$.byEvent[0].eventId").value(3))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    @DisplayName("IT-02 accepts a SERVICE token")
    void aggregateForService() throws Exception {
        when(transferService.getAggregate()).thenReturn(aggregate());
        mvc.perform(get("/api/v1/internal/treasury/aggregate")
                        .header("Authorization", "Bearer " + tokenService.generateServiceToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("IT-03 rejects calls without a token and maps a missing account to 404")
    void errors() throws Exception {
        mvc.perform(get("/api/v1/internal/treasury/aggregate")).andExpect(status().isUnauthorized());
        when(transferService.getAggregate()).thenThrow(new ResourceNotFoundException("Treasury account has not been initialised"));
        mvc.perform(get("/api/v1/internal/treasury/aggregate").header("Authorization", TestTokens.associate()))
                .andExpect(status().isNotFound());
    }
}
