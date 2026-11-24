package com.fintech.orgwise.treasury.security;

import com.fintech.orgwise.treasury.support.TestTokens;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private final JwtTokenService service = new JwtTokenService(TestTokens.SECRET, "treasury-service", Duration.ofMinutes(5));

    private static String raw(String bearer) {
        return bearer.substring("Bearer ".length());
    }

    private static String tokenWithClaims(Object role, Object officerId) {
        var b = Jwts.builder().setSubject("u").setExpiration(new Date(System.currentTimeMillis() + 60_000));
        if (role != null) {
            b.claim("role", role);
        }
        if (officerId != null) {
            b.claim("officerId", officerId);
        }
        return b.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestTokens.SECRET)), SignatureAlgorithm.HS256).compact();
    }

    @Nested
    @DisplayName("parse()")
    class Parse {
        @Test
        @DisplayName("JT-01 reads subject, role and officerId from a valid token")
        void valid() {
            JwtPrincipal p = service.parse(raw(TestTokens.financeHead()));
            assertThat(p.username()).isEqualTo("finance");
            assertThat(p.role()).isEqualTo("FINANCE_HEAD");
            assertThat(p.officerId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("JT-02 strips a ROLE_ prefix, upper-cases the role and accepts a string officerId")
        void normalises() {
            JwtPrincipal p = service.parse(tokenWithClaims("ROLE_finance_head", "12"));
            assertThat(p.role()).isEqualTo("FINANCE_HEAD");
            assertThat(p.officerId()).isEqualTo(12L);
            assertThat(service.parse(tokenWithClaims("ASSOCIATE", "abc")).officerId()).isNull();
        }

        @Test
        @DisplayName("JT-03 rejects expired, wrongly signed and tampered tokens")
        void rejectsInvalid() {
            assertThatThrownBy(() -> service.parse(raw(TestTokens.expired()))).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> service.parse(raw(TestTokens.wrongKey()))).isInstanceOf(JwtException.class);
            String t = raw(TestTokens.associate());
            String tampered = t.substring(0, t.length() - 3) + (t.endsWith("a") ? "bbb" : "aaa");
            assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("JT-04 rejects blank tokens and tokens without a role")
        void rejectsIncomplete() {
            assertThatThrownBy(() -> service.parse(" ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.parse(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.parse(tokenWithClaims(null, 1))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("generateServiceToken()")
    class ServiceToken {
        @Test
        @DisplayName("JT-05 produces a token this service accepts with role SERVICE")
        void roundTrip() {
            JwtPrincipal p = service.parse(service.generateServiceToken());
            assertThat(p.role()).isEqualTo(Roles.SERVICE);
            assertThat(p.username()).isEqualTo("treasury-service");
            assertThat(p.officerId()).isNull();
        }

        @Test
        @DisplayName("JT-06 a service token signed with another secret is rejected")
        void otherSecretRejected() {
            JwtTokenService other = new JwtTokenService(
                    "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1ub3QtdGhlLXRyZWFzdXJ5LW9uZSEhISE=", "x", Duration.ofMinutes(1));
            assertThatThrownBy(() -> service.parse(other.generateServiceToken())).isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("JT-07 respects the configured lifetime")
        void expires() {
            JwtTokenService shortLived = new JwtTokenService(TestTokens.SECRET, "x", Duration.ofSeconds(-1));
            assertThatThrownBy(() -> service.parse(shortLived.generateServiceToken())).isInstanceOf(JwtException.class);
        }
    }

    @Test
    @DisplayName("JT-08 JwtPrincipal.hasAnyRole is case-insensitive and toString is the username")
    void principal() {
        JwtPrincipal p = new JwtPrincipal(1L, "pat", "ADMIN");
        assertThat(p.hasAnyRole("admin")).isTrue();
        assertThat(p.hasAnyRole("PRESIDENT", "FINANCE_HEAD")).isFalse();
        assertThat(p.hasAnyRole()).isFalse();
        assertThat(p).hasToString("pat");
    }
}
