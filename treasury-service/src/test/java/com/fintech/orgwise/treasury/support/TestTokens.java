package com.fintech.orgwise.treasury.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/** Mints JWTs signed with the test secret from {@code src/test/resources/application.yml}. */
public final class TestTokens {

    /** Same value as {@code jwt.secret} in the test configuration. */
    public static final String SECRET = "dGVzdC1vbmx5LXNlY3JldC1mb3ItdHJlYXN1cnktc2VydmljZS10ZXN0cyEhIQ==";

    private static final SecretKey KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

    private TestTokens() {
    }

    /**
     * @param username  subject
     * @param role      role claim
     * @param officerId officer ID claim
     * @return {@code "Bearer <jwt>"}
     */
    public static String bearer(String username, String role, Long officerId) {
        return "Bearer " + token(username, role, officerId, KEY, Instant.now().plusSeconds(3600));
    }

    /** @return bearer token for finance head {@code finance} (officer 2) */
    public static String financeHead() {
        return bearer("finance", "FINANCE_HEAD", 2L);
    }

    /** @return bearer token for president {@code president} (officer 4) */
    public static String president() {
        return bearer("president", "PRESIDENT", 4L);
    }

    /** @return bearer token for associate {@code assoc} (officer 7) */
    public static String associate() {
        return bearer("assoc", "ASSOCIATE", 7L);
    }

    /** @return a token whose expiry is in the past */
    public static String expired() {
        return "Bearer " + token("finance", "FINANCE_HEAD", 2L, KEY, Instant.now().minusSeconds(60));
    }

    /** @return a token signed with a different key */
    public static String wrongKey() {
        SecretKey other = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
                "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1ub3QtdGhlLXRyZWFzdXJ5LW9uZSEhISE="));
        return "Bearer " + token("finance", "FINANCE_HEAD", 2L, other, Instant.now().plusSeconds(3600));
    }

    private static String token(String username, String role, Long officerId, SecretKey key, Instant expiry) {
        var builder = Jwts.builder().setSubject(username).claim("role", role)
                .setIssuedAt(Date.from(Instant.now().minusSeconds(120))).setExpiration(Date.from(expiry));
        if (officerId != null) {
            builder.claim("officerId", officerId);
        }
        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
