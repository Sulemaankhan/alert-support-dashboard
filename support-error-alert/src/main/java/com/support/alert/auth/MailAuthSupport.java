package com.support.alert.auth;

import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Shared helpers for sign-in OTP email (SMTP).
 */
public final class MailAuthSupport {

    private MailAuthSupport() {}

    public static boolean isSmtpConfigured(Environment environment) {
        if (environment == null) {
            return false;
        }
        String host = environment.getProperty("spring.mail.host");
        if (host == null || host.isBlank()) {
            return false;
        }
        String username = environment.getProperty("spring.mail.username");
        String password = resolveMailPassword(environment);
        return username != null
                && !username.isBlank()
                && password != null
                && !password.isBlank();
    }

    /** Gmail app passwords are often pasted with spaces — strip them. */
    public static String resolveMailPassword(Environment environment) {
        if (environment == null) {
            return "";
        }
        String password = environment.getProperty("spring.mail.password");
        if (password == null) {
            return "";
        }
        return password.replaceAll("\\s+", "");
    }

    public static boolean isMailSenderReady(JavaMailSender mailSender, Environment environment) {
        return mailSender != null && isSmtpConfigured(environment);
    }
}
