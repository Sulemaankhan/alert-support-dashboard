package com.support.alert.auth;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AuthStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(AuthStartupLogger.class);

    private final AuthProperties authProperties;
    private final Environment environment;

    public AuthStartupLogger(AuthProperties authProperties, Environment environment) {
        this.authProperties = authProperties;
        this.environment = environment;
    }

    @PostConstruct
    void logAuthSetup() {
        if (!authProperties.isEnabled()) {
            return;
        }
        if (authProperties.isGoogleSsoOnly()) {
            if (!authProperties.isGoogleEnabled()) {
                log.warn(
                        "Gmail SSO is enabled but Google client ID is missing. "
                                + "Start the service with --support.auth.google-client-id=YOUR_ID.apps.googleusercontent.com "
                                + "or set GOOGLE_CLIENT_ID (see google-oauth.properties.example).");
            } else {
                log.info(
                        "Gmail SSO ready (client ID {})",
                        maskClientId(authProperties.getGoogleClientId()));
            }
            return;
        }
        if (!authProperties.isGoogleEnabled()) {
            log.warn(
                    "Google OAuth client ID is not configured — set support.auth.google-client-id "
                            + "or GOOGLE_CLIENT_ID.");
        } else {
            log.info("Google OAuth enabled for client ID {}", maskClientId(authProperties.getGoogleClientId()));
        }
    }

    private static String maskClientId(String clientId) {
        if (clientId == null || clientId.length() < 12) {
            return "(configured)";
        }
        return clientId.substring(0, 8) + "…" + clientId.substring(clientId.length() - 20);
    }
}
