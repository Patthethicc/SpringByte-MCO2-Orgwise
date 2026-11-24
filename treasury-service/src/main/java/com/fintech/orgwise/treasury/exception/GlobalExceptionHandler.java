package com.fintech.orgwise.treasury.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps every exception to an {@link ErrorResponseDTO} with the right HTTP status. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * @param ex      missing resource
     * @param request current request
     * @return 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    /**
     * @param ex      broken business rule
     * @param request current request
     * @return 409
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    /**
     * @param ex      balance too low
     * @param request current request
     * @return 422 with available and requested amounts
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientFunds(InsufficientFundsException ex,
                                                                    HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("available", ex.getAvailable().toPlainString());
        details.put("requested", ex.getRequested().toPlainString());
        return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), request, details);
    }

    /**
     * @param ex      downstream outage before any money moved
     * @param request current request
     * @return 503
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnavailable(ServiceUnavailableException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, null);
    }

    /**
     * @param ex      compensated saga
     * @param request current request
     * @return 503 or 409 with the FAILED transfer reference
     */
    @ExceptionHandler(DisbursementFailedException.class)
    public ResponseEntity<ErrorResponseDTO> handleDisbursementFailed(DisbursementFailedException ex,
                                                                     HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request, Map.of("transferReference", ex.getReference()));
    }

    /**
     * @param ex      bean-validation failure
     * @param request current request
     * @return 400 with one entry per invalid field
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fields.putIfAbsent(err.getField(), err.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fields);
    }

    /**
     * @param ex      unreadable body, bad parameter type or bad argument
     * @param request current request
     * @return 400
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponseDTO> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = ex instanceof HttpMessageNotReadableException
                ? "Malformed JSON request body"
                : ex instanceof MethodArgumentTypeMismatchException mm
                ? "Invalid value for parameter '" + mm.getName() + "'"
                : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    /**
     * @param ex      authorisation failure from {@code @PreAuthorize} or the service layer
     * @param request current request
     * @return 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    /**
     * @param ex      no authenticated principal
     * @param request current request
     * @return 401
     */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnauthenticated(AuthenticationCredentialsNotFoundException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    /**
     * @param ex      anything unexpected
     * @param request current request
     * @return 500 without internal details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    private static ResponseEntity<ErrorResponseDTO> build(HttpStatus status, String message,
                                                          HttpServletRequest request, Map<String, String> details) {
        return ResponseEntity.status(status).body(new ErrorResponseDTO(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), details));
    }
}
