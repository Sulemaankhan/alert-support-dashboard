package com.support.alert.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public final class AuthUser implements Authentication {

    private final String email;
    private final String displayName;
    private final boolean gmailInboxAccess;
    private final String googleAccessToken;
    private boolean authenticated = true;

    public AuthUser(AuthSession session) {
        this.email = session.getEmail();
        this.displayName = session.getDisplayName() != null && !session.getDisplayName().isBlank()
                ? session.getDisplayName()
                : session.getEmail();
        this.gmailInboxAccess = session.hasGmailInboxAccess();
        this.googleAccessToken = session.hasGmailInboxAccess() ? session.getGoogleAccessToken() : null;
    }

    /** Dev / anonymous fallback. */
    public AuthUser(String email, String displayName) {
        this.email = email;
        this.displayName = displayName != null && !displayName.isBlank() ? displayName : email;
        this.gmailInboxAccess = false;
        this.googleAccessToken = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return email;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isGmailInboxAccess() {
        return gmailInboxAccess;
    }

    public String getGoogleAccessToken() {
        return googleAccessToken;
    }
}
