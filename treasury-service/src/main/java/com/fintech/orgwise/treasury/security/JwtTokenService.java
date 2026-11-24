package com.fintech.orgwise.treasury.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

/**
 * Validates JWTs issued by identity-service and mints short-lived service tokens.
 *
 * <p>The signing key is the HS256 secret shared through the Config Server ({@code jwt.secret},
 * Base64-encoded, at least 256 bits). This is the treasury-local counterpart of
 * {@code orgwise-common}'s {@code JwtUtil}.</p>
 */
@Component
public class JwtTokenService {

    /** Claim holding the role name. */
    public static final String ROLE_CLAIM = "role";
    /** Claim holding the officer's numeric ID. */
    public static final String OFFICER_ID_CLAIM = "officerId";

    private final SecretKey key;
    private final String serviceName;
    private final Duration serviceTokenTtl;

    /**
     * @param secret          Base64-encoded HMAC secret shared by all services
     * @param serviceName     subject used for service tokens
     * @param serviceTokenTtl lifetime of service tokens
     */
    public JwtTokenService(@Value("${jwt.secret}") String secret,
                           @Value("${spring.application.name:treasury-service}") String serviceName,
                           @Value("${orgwise.security.service-token-ttl:PT5M}") Duration serviceTokenTtl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.serviceName = serviceName;
        this.serviceTokenTtl = serviceTokenTtl;
    }

    /**
     * Parses and verifies a token (signature and expiry).
     *
     * @param token compact JWT without the {@code Bearer } prefix
     * @return the caller
     * @throws JwtException             if the token is invalid, expired or tampered with
     * @throws IllegalArgumentException if the token is blank or has no role
     */
    public JwtPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is empty");
        }
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        String role = claims.get(ROLE_CLAIM, String.class);
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Token has no role claim");
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }
        return new JwtPrincipal(toLong(claims.get(OFFICER_ID_CLAIM)), claims.getSubject(),
                role.toUpperCase(Locale.ROOT));
    }

    /**
     * Mints a token for calls that have no user behind them (the reconciliation job).
     *
     * @return a compact JWT with subject = service name and role {@link Roles#SERVICE}
     */
    public String generateServiceToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(serviceName)
                .claim(ROLE_CLAIM, Roles.SERVICE)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(serviceTokenTtl)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
