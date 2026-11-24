package com.fintech.orgwise.treasury.security;

/**
 * The authenticated caller, built from JWT claims ({@code sub}, {@code role}, {@code officerId}).
 * Mirrors {@code com.fintech.orgwise.common.security.JwtPrincipal}.
 *
 * @param officerId officer ID in identity-service ({@code null} for service tokens)
 * @param username  JWT subject
 * @param role      role name without the {@code ROLE_} prefix
 */
public record JwtPrincipal(Long officerId, String username, String role) {

    /**
     * @param candidates role names to accept
     * @return {@code true} if this principal has one of them
     */
    public boolean hasAnyRole(String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return username;
    }
}
