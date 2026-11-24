package com.fintech.orgwise.treasury.security;

import com.fintech.orgwise.treasury.support.TestTokens;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new JwtTokenService(TestTokens.SECRET, "treasury-service", Duration.ofMinutes(5)));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private AtomicReference<Object> run(String authorization) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/treasury");
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        AtomicReference<Object> seen = new AtomicReference<>();
        FilterChain chain = (rq, rs) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            seen.set(auth == null ? null : auth);
        };
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return seen;
    }

    @Test
    @DisplayName("JF-01 a valid token authenticates a JwtPrincipal with ROLE_<role>")
    void authenticates() throws Exception {
        var auth = (org.springframework.security.core.Authentication) run(TestTokens.financeHead()).get();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(JwtPrincipal.class);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_FINANCE_HEAD");
    }

    @Test
    @DisplayName("JF-02 missing or non-bearer headers leave the request anonymous")
    void anonymous() throws Exception {
        assertThat(run(null).get()).isNull();
        assertThat(run("Basic dXNlcjpwYXNz").get()).isNull();
    }

    @Test
    @DisplayName("JF-03 invalid or expired tokens leave the request anonymous (Spring then answers 401)")
    void invalid() throws Exception {
        assertThat(run(TestTokens.expired()).get()).isNull();
        assertThat(run(TestTokens.wrongKey()).get()).isNull();
        assertThat(run("Bearer garbage").get()).isNull();
    }

    @Test
    @DisplayName("JF-04 an already authenticated context is not overwritten")
    void keepsExisting() throws Exception {
        var existing = new TestingAuthenticationToken("someone", null);
        SecurityContextHolder.getContext().setAuthentication(existing);
        assertThat(run(TestTokens.financeHead()).get()).isSameAs(existing);
    }

    @Test
    @DisplayName("JF-05 CurrentOfficer returns the principal and throws when anonymous")
    void currentOfficer() {
        assertThatThrownBy(CurrentOfficer::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("plain-string", null));
        assertThatThrownBy(CurrentOfficer::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        JwtPrincipal p = new JwtPrincipal(1L, "u", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(p, null));
        assertThat(CurrentOfficer.get()).isSameAs(p);
    }
}
