package com.fintech.orgwise.treasury.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** Answers authenticated-but-forbidden requests with a JSON 403 (MCO1's {@code CustomAccessDeniedHandler}). */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper JSON writer
     */
    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        RestAuthenticationEntryPoint.write(objectMapper, response, request, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action");
    }
}
