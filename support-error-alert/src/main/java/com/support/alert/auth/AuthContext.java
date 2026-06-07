package com.support.alert.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class AuthContext {

    private AuthContext() {}

    public static Optional<AuthUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Resolves the signed-in user from the security context or session cookie. */
    public static Optional<AuthUser> resolveUser(HttpServletRequest request, AuthSessionStore sessionStore) {
        Optional<AuthUser> fromContext = currentUser();
        if (fromContext.isPresent()) {
            return fromContext;
        }
        return SessionAuthFilter.readSessionCookie(request)
                .flatMap(sessionStore::find)
                .map(AuthUser::new);
    }
}
