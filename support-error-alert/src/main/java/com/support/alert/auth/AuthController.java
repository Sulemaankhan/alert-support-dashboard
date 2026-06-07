package com.support.alert.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthProperties authProperties;
    private final AuthSessionStore sessionStore;
    private final EmailOtpService emailOtpService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final Environment environment;

    public AuthController(
            AuthProperties authProperties,
            AuthSessionStore sessionStore,
            EmailOtpService emailOtpService,
            GoogleTokenVerifier googleTokenVerifier,
            Environment environment) {
        this.authProperties = authProperties;
        this.sessionStore = sessionStore;
        this.emailOtpService = emailOtpService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.environment = environment;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", authProperties.isEnabled());
        String rawClientId = authProperties.getGoogleClientId();
        out.put("googleClientId", authProperties.isGoogleEnabled() ? rawClientId : "");
        out.put("googleConfigured", authProperties.isGoogleEnabled());
        if (!authProperties.isGoogleEnabled() && rawClientId != null && !rawClientId.isBlank()) {
            out.put(
                    "googleConfigError",
                    "Client ID is set but invalid. Use a Web client ID ending with .apps.googleusercontent.com");
        } else if (!authProperties.isGoogleEnabled()) {
            out.put(
                    "googleConfigError",
                    "Start with --support.auth.google-client-id=YOUR_ID.apps.googleusercontent.com or set GOOGLE_CLIENT_ID.");
        }
        out.put("googleSsoOnly", authProperties.isGoogleSsoOnly());
        boolean smtp = MailAuthSupport.isSmtpConfigured(environment);
        out.put("emailOtpConfigured", smtp);
        if (!smtp && !authProperties.getDevFixedOtp().isBlank()) {
            out.put("devSignInCode", authProperties.getDevFixedOtp().trim());
        }
        return out;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        return AuthContext.currentUser()
                .map(user -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("email", user.getEmail());
                    body.put("displayName", user.getDisplayName());
                    body.put("gmailInboxAccess", user.isGmailInboxAccess());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "Not signed in.")));
    }

    @PostMapping("/email/send-code")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody Map<String, String> body) {
        if (authProperties.isGoogleSsoOnly()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Sign-in uses Google SSO only. Use Sign in with Google on the login page."));
        }
        String email = body != null ? body.get("email") : null;
        try {
            SendCodeResult result = emailOtpService.sendSignInCode(email);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("message", result.message());
            out.put("emailSent", result.emailSent());
            if (result.devCode() != null) {
                out.put("devCode", result.devCode());
            }
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/email/verify")
    public ResponseEntity<?> verifyEmail(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        if (authProperties.isGoogleSsoOnly()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Sign-in uses Google SSO only. Use Sign in with Google on the login page."));
        }
        String email = body != null ? body.get("email") : null;
        String code = body != null ? body.get("code") : null;
        if (!emailOtpService.verifyCode(email, code)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired sign-in code."));
        }
        String normalized = email.trim().toLowerCase();
        String accessToken = body != null ? body.get("accessToken") : null;
        String scopeHint = body != null ? body.get("scope") : null;
        AuthSession session;
        if (accessToken != null
                && !accessToken.isBlank()
                && authProperties.isGoogleEnabled()) {
            try {
                GoogleTokenVerifier.GoogleAccessTokenInfo info =
                        googleTokenVerifier.verifyAccessToken(accessToken, scopeHint);
                if (!info.email().equalsIgnoreCase(normalized)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                    "error",
                                    "Google account email must match the address you verified ("
                                            + normalized
                                            + ")."));
                }
                log.info("Sign-in via email code + Google Gmail for {}", normalized);
                session = sessionStore.createWithGoogle(
                        normalized,
                        normalized,
                        authProperties.getSessionTtlHours(),
                        accessToken.trim(),
                        info.expiresAt());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
            }
        } else {
            log.info("Sign-in via email code for {}", normalized);
            session = sessionStore.create(normalized, normalized, authProperties.getSessionTtlHours());
        }
        SessionAuthFilter.writeSessionCookie(response, session.getSessionId(), authProperties.getSessionTtlHours());
        return ResponseEntity.ok(userPayload(session));
    }

    /**
     * Adds Gmail API access to the current session (after email-code sign-in).
     */
    @PostMapping("/gmail/connect")
    public ResponseEntity<?> connectGmail(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Optional<String> sessionId = SessionAuthFilter.readSessionCookie(request);
        if (sessionId.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not signed in."));
        }
        Optional<AuthSession> existing = sessionStore.find(sessionId.get());
        if (existing.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Session expired. Sign in again."));
        }
        String accessToken = body != null ? body.get("accessToken") : null;
        String scopeHint = body != null ? body.get("scope") : null;
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Google access token is required."));
        }
        try {
            GoogleTokenVerifier.GoogleAccessTokenInfo info =
                    googleTokenVerifier.verifyAccessToken(accessToken, scopeHint);
            if (!info.email().equalsIgnoreCase(existing.get().getEmail())) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error",
                                "Google account must be the same email you signed in with ("
                                        + existing.get().getEmail()
                                        + ")."));
            }
            if (!info.hasGmailScope()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error",
                                "Allow Gmail access in the Google prompt to search inbox without a password."));
            }
            return sessionStore
                    .upgradeGmailAccess(sessionId.get(), accessToken.trim(), info.expiresAt())
                    .map(session -> ResponseEntity.ok(userPayload(session)))
                    .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "Session expired.")));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> signInGoogle(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {
        String accessToken = body != null ? body.get("accessToken") : null;
        String scopeHint = body != null ? body.get("scope") : null;
        String idToken = body != null ? body.get("idToken") : null;
        try {
            AuthSession session;
            if (accessToken != null && !accessToken.isBlank()) {
                GoogleTokenVerifier.GoogleAccessTokenInfo info =
                        googleTokenVerifier.verifyAccessToken(accessToken, scopeHint);
                if (!info.hasGmailScope()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of(
                                    "error",
                                    "Google sign-in must include Gmail access. Sign out and use Sign in with Google again."));
                }
                log.info("Sign-in via Google for {}", info.email());
                session = sessionStore.createWithGoogle(
                        info.email(),
                        info.email(),
                        authProperties.getSessionTtlHours(),
                        accessToken.trim(),
                        info.expiresAt());
            } else {
                log.warn("Google sign-in without access token — Gmail inbox will require an app password");
                String email = googleTokenVerifier.verifyIdToken(idToken);
                session = sessionStore.create(email, email, authProperties.getSessionTtlHours());
            }
            SessionAuthFilter.writeSessionCookie(response, session.getSessionId(), authProperties.getSessionTtlHours());
            return ResponseEntity.ok(userPayload(session));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SessionAuthFilter.readSessionCookie(request).ifPresent(sessionStore::remove);
        SessionAuthFilter.clearSessionCookie(response);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> userPayload(AuthSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", session.getEmail());
        body.put("displayName", session.getDisplayName());
        body.put("gmailInboxAccess", session.hasGmailInboxAccess());
        return body;
    }
}
