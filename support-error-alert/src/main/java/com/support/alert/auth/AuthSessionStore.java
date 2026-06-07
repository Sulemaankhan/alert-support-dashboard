package com.support.alert.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionStore.class);

    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

    public AuthSession create(String email, String displayName, int ttlHours) {
        return create(email, displayName, ttlHours, null, null);
    }

    public AuthSession createWithGoogle(
            String email,
            String displayName,
            int ttlHours,
            String googleAccessToken,
            Instant googleTokenExpiresAt) {
        return create(email, displayName, ttlHours, googleAccessToken, googleTokenExpiresAt);
    }

    private AuthSession create(
            String email,
            String displayName,
            int ttlHours,
            String googleAccessToken,
            Instant googleTokenExpiresAt) {
        String id = UUID.randomUUID().toString();
        Instant expires = Instant.now().plusSeconds(Math.max(1, ttlHours) * 3600L);
        AuthSession session =
                new AuthSession(id, email, displayName, expires, googleAccessToken, googleTokenExpiresAt);
        sessions.put(id, session);
        log.info(
                "Auth session created for {} (gmail inbox access: {}{})",
                maskEmail(email),
                session.hasGmailInboxAccess(),
                session.hasGmailInboxAccess() ? "" : " — use Connect Gmail or an app password for inbox search");
        return session;
    }

    public Optional<AuthSession> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        AuthSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired()) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<AuthSession> upgradeGmailAccess(String sessionId, String googleAccessToken, Instant googleTokenExpiresAt) {
        AuthSession existing = sessions.get(sessionId);
        if (existing == null || existing.isExpired()) {
            return Optional.empty();
        }
        AuthSession upgraded = new AuthSession(
                existing.getSessionId(),
                existing.getEmail(),
                existing.getDisplayName(),
                existing.getExpiresAt(),
                googleAccessToken,
                googleTokenExpiresAt);
        sessions.put(sessionId, upgraded);
        log.info("Gmail inbox access enabled for {}", maskEmail(existing.getEmail()));
        return Optional.of(upgraded);
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
