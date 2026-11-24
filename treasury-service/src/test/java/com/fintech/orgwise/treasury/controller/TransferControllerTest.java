package com.fintech.orgwise.treasury.controller;

import com.fintech.orgwise.treasury.dto.PageResponseDTO;
import com.fintech.orgwise.treasury.dto.TransferRecordDTO;
import com.fintech.orgwise.treasury.entity.TransferRecord;
import com.fintech.orgwise.treasury.entity.TransferStatus;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.DisbursementFailedException;
import com.fintech.orgwise.treasury.exception.InsufficientFundsException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.security.JwtPrincipal;
import com.fintech.orgwise.treasury.service.DisbursementSaga;
import com.fintech.orgwise.treasury.service.TransferService;
import com.fintech.orgwise.treasury.support.Fixtures;
import com.fintech.orgwise.treasury.support.TestTokens;
import com.fintech.orgwise.treasury.support.WebSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@WebSliceTest
class TransferControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DisbursementSaga saga;
    @MockitoBean
    private TransferService transferService;

    private static TransferRecordDTO completed() {
        TransferRecord t = Fixtures.pendingTransfer(5, "300.00");
        t.markCompleted();
        return TransferRecordDTO.from(t);
    }

    private static PageResponseDTO<TransferRecordDTO> page(TransferRecordDTO... items) {
        return new PageResponseDTO<>(List.of(items), 0, 20, items.length, 1);
    }

    @Nested
    @DisplayName("POST /api/v1/transfers")
    class Disburse {
        private org.springframework.test.web.servlet.ResultActions call(String token, String body) throws Exception {
            var req = post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(body);
            return mvc.perform(token == null ? req : req.header("Authorization", token));
        }

        @Test
        @DisplayName("TF-01 finance head disburses: 201 with the COMPLETED transfer; the JWT identity is passed on")
        void created() throws Exception {
            when(saga.disburse(eq(5L), any())).thenReturn(completed());
            call(TestTokens.financeHead(), "{\"expenseId\":5}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Funds disbursed"))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
            ArgumentCaptor<JwtPrincipal> actor = ArgumentCaptor.forClass(JwtPrincipal.class);
            verify(saga).disburse(eq(5L), actor.capture());
            assertThat(actor.getValue().officerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("TF-02 validation: missing, negative or malformed expenseId is 400 and the saga is not called")
        void validation() throws Exception {
            call(TestTokens.financeHead(), "{}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.expenseId").value("expenseId is required"));
            call(TestTokens.financeHead(), "{\"expenseId\":-3}").andExpect(status().isBadRequest());
            call(TestTokens.financeHead(), "{not json").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
            verifyNoInteractions(saga);
        }

        @Test
        @DisplayName("TF-03 non finance heads get 403 and anonymous callers get 401")
        void security() throws Exception {
            call(TestTokens.president(), "{\"expenseId\":5}").andExpect(status().isForbidden());
            call(TestTokens.associate(), "{\"expenseId\":5}").andExpect(status().isForbidden());
            call(null, "{\"expenseId\":5}").andExpect(status().isUnauthorized());
            call(TestTokens.expired(), "{\"expenseId\":5}").andExpect(status().isUnauthorized());
            verifyNoInteractions(saga);
        }

        @Test
        @DisplayName("TF-04 saga errors map to 409 / 422 / 404 / 503 with the transfer reference when compensated")
        void sagaErrors() throws Exception {
            when(saga.disburse(eq(1L), any())).thenThrow(new BusinessRuleException("Expense 1 has already been disbursed"));
            when(saga.disburse(eq(2L), any())).thenThrow(new InsufficientFundsException(new BigDecimal("10.00"), new BigDecimal("99.00")));
            when(saga.disburse(eq(3L), any())).thenThrow(new ResourceNotFoundException("Expense 3 not found"));
            when(saga.disburse(eq(4L), any())).thenThrow(new DisbursementFailedException("restored", "ref-4",
                    HttpStatus.SERVICE_UNAVAILABLE, new RuntimeException()));

            call(TestTokens.financeHead(), "{\"expenseId\":1}").andExpect(status().isConflict());
            call(TestTokens.financeHead(), "{\"expenseId\":2}").andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.details.requested").value("99.00"));
            call(TestTokens.financeHead(), "{\"expenseId\":3}").andExpect(status().isNotFound());
            call(TestTokens.financeHead(), "{\"expenseId\":4}").andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.details.transferReference").value("ref-4"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transfers")
    class ListAll {
        @Test
        @DisplayName("TF-05 lists the ledger for the finance head")
        void lists() throws Exception {
            when(transferService.getTransfers(isNull(), any(Pageable.class))).thenReturn(page(completed()));
            mvc.perform(get("/api/v1/transfers").header("Authorization", TestTokens.financeHead()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("TF-06 passes the status filter and page request; invalid status is 400")
        void filters() throws Exception {
            when(transferService.getTransfers(eq(TransferStatus.FAILED), any(Pageable.class))).thenReturn(page());
            mvc.perform(get("/api/v1/transfers?status=FAILED&page=2&size=5").header("Authorization", TestTokens.president()))
                    .andExpect(status().isOk());
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(transferService).getTransfers(eq(TransferStatus.FAILED), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(5);

            mvc.perform(get("/api/v1/transfers?status=BOGUS").header("Authorization", TestTokens.financeHead()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid value for parameter 'status'"));
        }

        @Test
        @DisplayName("TF-07 associates cannot see the whole ledger")
        void forbidden() throws Exception {
            mvc.perform(get("/api/v1/transfers").header("Authorization", TestTokens.associate()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transfers/me")
    class Mine {
        @Test
        @DisplayName("TF-08 returns the caller's transfers using the officerId claim")
        void mine() throws Exception {
            when(transferService.getTransfersForRecipient(eq(7L), any(Pageable.class))).thenReturn(page(completed()));
            mvc.perform(get("/api/v1/transfers/me").header("Authorization", TestTokens.associate()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].recipientId").value(7));
        }

        @Test
        @DisplayName("TF-09 a token without officerId is 400")
        void noOfficerId() throws Exception {
            when(transferService.getTransfersForRecipient(isNull(), any(Pageable.class)))
                    .thenThrow(new IllegalArgumentException("The caller's token has no officerId claim"));
            mvc.perform(get("/api/v1/transfers/me").header("Authorization", TestTokens.bearer("x", "ASSOCIATE", null)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TF-10 anonymous is 401")
        void anonymous() throws Exception {
            mvc.perform(get("/api/v1/transfers/me")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transfers/{reference}")
    class One {
        @Test
        @DisplayName("TF-11 returns the transfer")
        void found() throws Exception {
            TransferRecordDTO dto = completed();
            when(transferService.getTransfer(eq(dto.reference()), any())).thenReturn(dto);
            mvc.perform(get("/api/v1/transfers/" + dto.reference()).header("Authorization", TestTokens.associate()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reference").value(dto.reference()));
        }

        @Test
        @DisplayName("TF-12 another officer's transfer is 403")
        void notMine() throws Exception {
            when(transferService.getTransfer(eq("r"), any())).thenThrow(new AccessDeniedException("You can only view transfers paid to you"));
            mvc.perform(get("/api/v1/transfers/r").header("Authorization", TestTokens.associate()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("You can only view transfers paid to you"));
        }

        @Test
        @DisplayName("TF-13 unknown reference is 404")
        void unknown() throws Exception {
            when(transferService.getTransfer(eq("nope"), any())).thenThrow(new ResourceNotFoundException("Transfer nope not found"));
            mvc.perform(get("/api/v1/transfers/nope").header("Authorization", TestTokens.financeHead()))
                    .andExpect(status().isNotFound());
        }
    }
}
