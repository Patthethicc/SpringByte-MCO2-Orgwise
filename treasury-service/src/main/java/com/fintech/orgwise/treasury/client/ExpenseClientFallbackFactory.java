package com.fintech.orgwise.treasury.client;

import com.fintech.orgwise.treasury.client.dto.DisbursementCommandDTO;
import com.fintech.orgwise.treasury.client.dto.ExpenseSnapshotDTO;
import com.fintech.orgwise.treasury.exception.BusinessRuleException;
import com.fintech.orgwise.treasury.exception.ResourceNotFoundException;
import com.fintech.orgwise.treasury.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Circuit-breaker fallback for {@link ExpenseClient}. Every call to expense-service either feeds
 * a balance check or moves money, so the policy is <b>fail closed</b>: no fallback invents data.
 *
 * <ul>
 *   <li>404 becomes {@link ResourceNotFoundException}</li>
 *   <li>400/409/422 become {@link BusinessRuleException}, keeping expense-service's message</li>
 *   <li>401/403 become {@link AccessDeniedException}</li>
 *   <li>everything else (connection refused, timeout, 5xx, open circuit) becomes
 *       {@link ServiceUnavailableException} (HTTP 503)</li>
 * </ul>
 */
@Component
public class ExpenseClientFallbackFactory implements FallbackFactory<ExpenseClient> {

    private static final Logger log = LoggerFactory.getLogger(ExpenseClientFallbackFactory.class);
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Message used whenever expense-service cannot be reached. */
    public static final String UNAVAILABLE_MESSAGE =
            "Expense service is unavailable. No money was moved; please try again later.";

    @Override
    public ExpenseClient create(Throwable cause) {
        return new ExpenseClient() {
            @Override
            public ExpenseSnapshotDTO getExpense(Long id) {
                throw translate("getExpense(" + id + ")", cause);
            }

            @Override
            public List<ExpenseSnapshotDTO> getExpensesByStatus(String status) {
                throw translate("getExpensesByStatus(" + status + ")", cause);
            }

            @Override
            public ExpenseSnapshotDTO markDisbursed(Long id, DisbursementCommandDTO command) {
                throw translate("markDisbursed(" + id + ")", cause);
            }

            @Override
            public ExpenseSnapshotDTO revertDisbursement(Long id, DisbursementCommandDTO command) {
                throw translate("revertDisbursement(" + id + ")", cause);
            }
        };
    }

    /**
     * Maps a Feign / circuit-breaker failure to the exception treasury should throw.
     *
     * @param operation description of the call, for logs
     * @param cause     failure reported by the circuit breaker
     * @return the exception to throw
     */
    static RuntimeException translate(String operation, Throwable cause) {
        if (cause instanceof FeignException fe && fe.status() > 0) {
            int status = fe.status();
            String remoteMessage = extractMessage(fe);
            if (status == 404) {
                return new ResourceNotFoundException(remoteMessage != null ? remoteMessage : "Expense not found");
            }
            if (status == 400 || status == 409 || status == 422) {
                return new BusinessRuleException(remoteMessage != null ? remoteMessage
                        : "Expense service rejected the request (HTTP " + status + ")");
            }
            if (status == 401 || status == 403) {
                return new AccessDeniedException("Expense service refused the caller's credentials");
            }
        }
        if (cause instanceof CallNotPermittedException) {
            log.warn("Circuit open for expense-service, {} short-circuited", operation);
        } else {
            log.warn("expense-service call {} failed: {}", operation, cause == null ? "unknown" : cause.toString());
        }
        return new ServiceUnavailableException(UNAVAILABLE_MESSAGE, cause);
    }

    private static String extractMessage(FeignException fe) {
        String body = fe.contentUTF8();
        if (body == null || body.isBlank()) {
            return null;
        }
        Matcher m = MESSAGE.matcher(body);
        return m.find() ? m.group(1).replace("\\\"", "\"") : null;
    }
}
