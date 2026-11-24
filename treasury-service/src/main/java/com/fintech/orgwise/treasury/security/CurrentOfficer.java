package com.fintech.orgwise.treasury.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Static access to the authenticated {@link JwtPrincipal}. */
public final class CurrentOfficer {

    private CurrentOfficer() {
    }

    /**
     * @return the caller of the current request
     * @throws AuthenticationCredentialsNotFoundException if nobody is authenticated
     */
    public static JwtPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtPrincipal principal) {
            return principal;
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated officer");
    }
}
