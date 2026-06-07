package com.support.alert.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public GoogleTokenVerifier(AuthProperties authProperties, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    public record GoogleAccessTokenInfo(String email, Instant expiresAt, boolean hasGmailScope) {}

    public GoogleAccessTokenInfo verifyAccessToken(String accessToken) {
        return verifyAccessToken(accessToken, null);
    }

    /**
     * Verifies a Google OAuth access token. {@code scopeHint} may be the space-separated scope
     * string returned by the Google token client when tokeninfo omits it.
     */
    public GoogleAccessTokenInfo verifyAccessToken(String accessToken, String scopeHint) {
        if (!authProperties.isGoogleEnabled()) {
            throw new IllegalStateException("Google sign-in is not configured on the server.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Google access token is required.");
        }
        try {
            String body = restClient
                    .get()
                    .uri("https://www.googleapis.com/oauth2/v3/tokeninfo?access_token={token}", accessToken.trim())
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            String aud = text(json, "aud");
            if (!authProperties.getGoogleClientId().equals(aud)) {
                throw new IllegalArgumentException("Google token audience mismatch.");
            }
            String email = text(json, "email");
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Google account has no email.");
            }
            String scope = text(json, "scope");
            boolean gmailScope = hasGmailScope(scope) || hasGmailScope(scopeHint);
            if (!gmailScope) {
                gmailScope = probeGmailApi(accessToken.trim());
            }
            if (!gmailScope) {
                log.warn(
                        "Google access token for {} missing Gmail scope. tokeninfo: {}; client: {}",
                        mask(email),
                        scope != null ? scope : "(none)",
                        scopeHint != null ? scopeHint : "(none)");
            } else {
                log.info("Google access token verified for {} with Gmail access", mask(email));
            }
            long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong(60) : 3600L;
            Instant expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn));
            return new GoogleAccessTokenInfo(email.toLowerCase(), expiresAt, gmailScope);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Google access token verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("Invalid Google access token.");
        }
    }

    /**
     * @return verified email address
     */
    public String verifyIdToken(String idToken) {
        if (!authProperties.isGoogleEnabled()) {
            throw new IllegalStateException("Google sign-in is not configured on the server.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Google ID token is required.");
        }
        try {
            String body = restClient
                    .get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken.trim())
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            String aud = text(json, "aud");
            if (!authProperties.getGoogleClientId().equals(aud)) {
                throw new IllegalArgumentException("Google token audience mismatch.");
            }
            String email = text(json, "email");
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Google account has no email.");
            }
            String verified = text(json, "email_verified");
            if (!"true".equalsIgnoreCase(verified)) {
                throw new IllegalArgumentException("Google email is not verified.");
            }
            return email.toLowerCase();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Google token verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("Invalid Google sign-in token.");
        }
    }

    private boolean probeGmailApi(String accessToken) {
        try {
            restClient
                    .get()
                    .uri("https://gmail.googleapis.com/gmail/v1/users/me/profile")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (Exception ex) {
            log.debug("Gmail API probe failed: {}", ex.getMessage());
            return false;
        }
    }

    static boolean hasGmailScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return false;
        }
        String s = scope.toLowerCase();
        return s.contains("mail.google.com")
                || s.contains("gmail.readonly")
                || s.contains("gmail.modify")
                || s.contains("gmail.compose")
                || s.contains("gmail.metadata")
                || s.contains("gmail.labels")
                || s.contains("gmail.send");
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
