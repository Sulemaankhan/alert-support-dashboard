package com.support.alert.auth;

import java.time.Instant;

public final class AuthSession {

    private final String sessionId;
    private final String email;
    private final String displayName;
    private final Instant expiresAt;
    private final String googleAccessToken;
    private final Instant googleTokenExpiresAt;

    public AuthSession(
            String sessionId,
            String email,
            String displayName,
            Instant expiresAt,
            String googleAccessToken,
            Instant googleTokenExpiresAt) {
        this.sessionId = sessionId;
        this.email = email;
        this.displayName = displayName;
        this.expiresAt = expiresAt;
        this.googleAccessToken = googleAccessToken;
        this.googleTokenExpiresAt = googleTokenExpiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getGoogleAccessToken() {
        return googleAccessToken;
    }

    public Instant getGoogleTokenExpiresAt() {
        return googleTokenExpiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean hasGmailInboxAccess() {
        return googleAccessToken != null
                && !googleAccessToken.isBlank()
                && googleTokenExpiresAt != null
                && Instant.now().isBefore(googleTokenExpiresAt);
    }
}
