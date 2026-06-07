package com.support.alert.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_COOKIE = "SUPPORT_SESSION";

    private final AuthProperties authProperties;
    private final AuthSessionStore sessionStore;

    public SessionAuthFilter(AuthProperties authProperties, AuthSessionStore sessionStore) {
        this.authProperties = authProperties;
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!authProperties.isEnabled()) {
            SecurityContextHolder.getContext().setAuthentication(new AuthUser("anonymous@local", "Local user"));
            filterChain.doFilter(request, response);
            return;
        }
        Optional<String> sessionId = readSessionCookie(request);
        if (sessionId.isPresent()) {
            sessionStore
                    .find(sessionId.get())
                    .ifPresent(session -> SecurityContextHolder.getContext().setAuthentication(new AuthUser(session)));
        }
        filterChain.doFilter(request, response);
    }

    public static Optional<String> readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> SESSION_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    static void writeSessionCookie(HttpServletResponse response, String sessionId, int ttlHours) {
        Cookie cookie = new Cookie(SESSION_COOKIE, sessionId);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(Math.max(1, ttlHours) * 3600);
        cookie.setSecure(false);
        response.addCookie(cookie);
    }

    static void clearSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
