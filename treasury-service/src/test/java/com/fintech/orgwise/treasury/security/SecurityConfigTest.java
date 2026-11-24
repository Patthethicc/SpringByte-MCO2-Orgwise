package com.fintech.orgwise.treasury.security;

import com.fintech.orgwise.treasury.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security rules against the full application context. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("SC-01 health and OpenAPI docs are public")
    void publicEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Orgwise Treasury Service API"));
    }

    @Test
    @DisplayName("SC-02 business and internal endpoints require a JWT and answer 401 as JSON")
    void protectedEndpoints() throws Exception {
        mvc.perform(get("/api/v1/treasury")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andExpect(jsonPath("$.path").value("/api/v1/treasury"));
        mvc.perform(get("/api/v1/internal/treasury/aggregate")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/transfers/me").header("Authorization", TestTokens.wrongKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SC-03 a valid token reaches the controller; wrong role gets a JSON 403")
    void roles() throws Exception {
        mvc.perform(get("/api/v1/treasury").header("Authorization", TestTokens.financeHead()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Treasury"));
        mvc.perform(get("/api/v1/transfers").header("Authorization", TestTokens.associate()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("SC-04 the session is stateless and CORS pre-flight is not blocked by auth")
    void statelessAndPreflight() throws Exception {
        mvc.perform(get("/api/v1/treasury").header("Authorization", TestTokens.financeHead()))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(options("/api/v1/treasury")).andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("SC-05 there is no username/password login: the user store is empty")
    void noUsers() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("admin"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
