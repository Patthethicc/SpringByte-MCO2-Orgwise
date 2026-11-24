package com.fintech.orgwise.treasury.client;

import com.fintech.orgwise.treasury.security.JwtTokenService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Adds an {@code Authorization} header to every outgoing Feign request.
 *
 * <p>If the call happens while serving an HTTP request, the caller's own bearer token is
 * forwarded so expense-service applies the same identity (RFC-001 section 6). Background work
 * with no request (the reconciler) gets a short-lived service token instead. This mirrors
 * {@code orgwise-common}'s {@code FeignAuthInterceptor}.</p>
 *
 * <p>Circuit breakers run on the calling thread
 * ({@code spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true}), so the request
 * context is visible here.</p>
 */
@Component
public class FeignAuthForwardingInterceptor implements RequestInterceptor {

    private final JwtTokenService tokenService;

    /**
     * @param tokenService used to mint service tokens when no user token is available
     */
    public FeignAuthForwardingInterceptor(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            return;
        }
        String incoming = currentAuthorizationHeader();
        if (incoming != null && incoming.startsWith("Bearer ")) {
            template.header(HttpHeaders.AUTHORIZATION, incoming);
        } else {
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.generateServiceToken());
        }
    }

    private static String currentAuthorizationHeader() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            return request.getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
