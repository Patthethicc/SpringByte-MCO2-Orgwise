package com.fintech.orgwise.treasury.security;

import com.fintech.orgwise.treasury.exception.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

/** Answers unauthenticated requests with a JSON 401 (MCO1's {@code CustomAuthenticationEntryPoint}). */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper JSON writer
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(objectMapper, response, request, HttpStatus.UNAUTHORIZED,
                "Authentication required: provide a valid Bearer token");
    }

    static void write(ObjectMapper mapper, HttpServletResponse response, HttpServletRequest request,
                      HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponseDTO body = new ErrorResponseDTO(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), null);
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
