package com.fintech.orgwise.treasury.dto;

import java.time.Instant;

/**
 * Success envelope for public endpoints, same shape as the MCO1 {@code ApiResponseDTO}.
 * Mirrors {@code com.fintech.orgwise.common.dto.ApiResponseDTO}; switch to the shared class once
 * {@code orgwise-common} is published.
 *
 * @param success   always {@code true} for this envelope (errors use {@code ErrorResponseDTO})
 * @param message   short human-readable message
 * @param data      payload
 * @param timestamp when the response was built
 * @param <T>       payload type
 */
public record ApiResponseDTO<T>(boolean success, String message, T data, Instant timestamp) {

    /**
     * @param message message to show
     * @param data    payload
     * @param <T>     payload type
     * @return a successful envelope
     */
    public static <T> ApiResponseDTO<T> ok(String message, T data) {
        return new ApiResponseDTO<>(true, message, data, Instant.now());
    }
}
