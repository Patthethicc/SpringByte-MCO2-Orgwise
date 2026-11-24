package com.fintech.orgwise.treasury.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityHandlersTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("SH-01 entry point writes a JSON 401 with the request path")
    void entryPoint() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        new RestAuthenticationEntryPoint(mapper).commence(new MockHttpServletRequest("GET", "/api/v1/treasury"), res,
                new BadCredentialsException("bad"));
        JsonNode body = mapper.readTree(res.getContentAsString());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentType()).isEqualTo("application/json");
        assertThat(body.get("path").asString()).isEqualTo("/api/v1/treasury");
        assertThat(body.get("message").asString()).contains("Bearer token");
    }

    @Test
    @DisplayName("SH-02 access-denied handler writes a JSON 403")
    void accessDenied() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        new RestAccessDeniedHandler(mapper).handle(new MockHttpServletRequest("POST", "/api/v1/transfers"), res,
                new AccessDeniedException("no"));
        JsonNode body = mapper.readTree(res.getContentAsString());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asString()).isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("SH-03 neither handler leaks the exception message or omits the timestamp")
    void noLeak() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        new RestAccessDeniedHandler(mapper).handle(new MockHttpServletRequest("GET", "/x"), res,
                new AccessDeniedException("internal rule ROLE_X"));
        assertThat(res.getContentAsString()).doesNotContain("internal rule").contains("timestamp");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        new RestAuthenticationEntryPoint(mapper).commence(new MockHttpServletRequest("GET", "/x"), res2,
                new BadCredentialsException("secret detail"));
        assertThat(res2.getContentAsString()).doesNotContain("secret detail");
    }
}
