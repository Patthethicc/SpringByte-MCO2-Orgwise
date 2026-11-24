package com.fintech.orgwise.treasury.client;

import com.fintech.orgwise.treasury.security.JwtTokenService;
import com.fintech.orgwise.treasury.support.TestTokens;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FeignAuthForwardingInterceptorTest {

    private final JwtTokenService tokens = new JwtTokenService(TestTokens.SECRET, "treasury-service", Duration.ofMinutes(5));
    private final FeignAuthForwardingInterceptor interceptor = new FeignAuthForwardingInterceptor(tokens);

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("FI-01 forwards the caller's bearer token when serving a request")
    void forwardsUserToken() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer user.jwt.token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertThat(template.headers().get("Authorization")).containsExactly("Bearer user.jwt.token");
    }

    @Test
    @DisplayName("FI-02 uses a SERVICE token when there is no request (background job)")
    void serviceTokenWithoutRequest() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        String header = template.headers().get("Authorization").iterator().next();
        assertThat(header).startsWith("Bearer ");
        assertThat(tokens.parse(header.substring(7)).role()).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("FI-03 uses a SERVICE token when the request has no/non-bearer Authorization header")
    void serviceTokenForNonBearer() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertThat(template.headers().get("Authorization").iterator().next()).isNotEqualTo("Basic abc");
    }

    @Test
    @DisplayName("FI-04 leaves an explicitly set Authorization header alone")
    void keepsExplicitHeader() {
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer explicit");
        interceptor.apply(template);
        assertThat(template.headers().get("Authorization")).containsExactly("Bearer explicit");
    }
}
