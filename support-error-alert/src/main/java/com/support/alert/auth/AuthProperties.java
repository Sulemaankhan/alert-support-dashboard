package com.support.alert.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "support.auth")
public class AuthProperties {

    private boolean enabled = true;

    /** Session lifetime after sign-in. */
    private int sessionTtlHours = 12;

    /** How long a one-time email code remains valid. */
    private int otpTtlMinutes = 10;

    /** Google OAuth client ID (Web) for Sign in with Google / Gmail SSO. */
    private String googleClientId = "";

    /** When true, only Google SSO is offered (no email OTP sign-in). */
    private boolean googleSsoOnly = true;

    /** When true, log OTP to server logs if email cannot be sent (local dev). */
    private boolean devLogOtp = true;

    /** Optional fixed OTP for any email when SMTP is not configured (dev only). */
    private String devFixedOtp = "";

    /** From address for sign-in emails. Falls back to spring.mail.username. */
    private String mailFrom = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSessionTtlHours() {
        return sessionTtlHours;
    }

    public void setSessionTtlHours(int sessionTtlHours) {
        this.sessionTtlHours = sessionTtlHours;
    }

    public int getOtpTtlMinutes() {
        return otpTtlMinutes;
    }

    public void setOtpTtlMinutes(int otpTtlMinutes) {
        this.otpTtlMinutes = otpTtlMinutes;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public void setGoogleClientId(String googleClientId) {
        this.googleClientId = googleClientId;
    }

    public boolean isGoogleSsoOnly() {
        return googleSsoOnly;
    }

    public void setGoogleSsoOnly(boolean googleSsoOnly) {
        this.googleSsoOnly = googleSsoOnly;
    }

    public boolean isDevLogOtp() {
        return devLogOtp;
    }

    public void setDevLogOtp(boolean devLogOtp) {
        this.devLogOtp = devLogOtp;
    }

    public String getDevFixedOtp() {
        return devFixedOtp;
    }

    public void setDevFixedOtp(String devFixedOtp) {
        this.devFixedOtp = devFixedOtp;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public boolean isGoogleEnabled() {
        return isConfiguredClientId(googleClientId);
    }

    static boolean isConfiguredClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        String normalized = clientId.trim().toLowerCase();
        return !normalized.startsWith("your-")
                && !normalized.contains("your-id")
                && !normalized.contains("your-client-id")
                && normalized.endsWith(".apps.googleusercontent.com");
    }
}
