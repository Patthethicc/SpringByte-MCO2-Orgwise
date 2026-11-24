package com.fintech.orgwise.treasury.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Error body returned by every treasury endpoint. Same shape as the MCO1
 * {@code ErrorResponseDTO} so the frontend can handle errors from all services the same way.
 *
 * @param timestamp when the error happened
 * @param status    HTTP status code
 * @param error     HTTP reason phrase
 * @param message   human-readable explanation
 * @param path      request path
 * @param details   optional extra data, e.g. field validation errors or the transfer reference
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponseDTO(Instant timestamp, int status, String error, String message, String path,
                               Map<String, String> details) {
}
