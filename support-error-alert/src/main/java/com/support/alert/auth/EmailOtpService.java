package com.support.alert.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
public class EmailOtpService {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthProperties authProperties;
    private final EmailOtpStore otpStore;
    private final JavaMailSender mailSender;
    private final Environment environment;

    public EmailOtpService(
            AuthProperties authProperties,
            EmailOtpStore otpStore,
            @Autowired(required = false) JavaMailSender mailSender,
            Environment environment) {
        this.authProperties = authProperties;
        this.otpStore = otpStore;
        this.mailSender = mailSender;
        this.environment = environment;
    }

    public SendCodeResult sendSignInCode(String email) {
        String normalized = normalizeEmail(email);
        boolean smtpReady = MailAuthSupport.isMailSenderReady(mailSender, environment);

        String code = generateCode();
        if (!smtpReady && !authProperties.getDevFixedOtp().isBlank()) {
            code = authProperties.getDevFixedOtp().trim();
        }

        Instant expires = Instant.now().plusSeconds(authProperties.getOtpTtlMinutes() * 60L);
        otpStore.put(normalized, code, expires);

        if (smtpReady) {
            sendEmailOrThrow(normalized, code);
            return new SendCodeResult("Sign-in code sent to " + normalized + ". Check your inbox.", true, null);
        }

        boolean sent = trySendEmail(normalized, code);
        if (sent) {
            return new SendCodeResult("Sign-in code sent to your email.", true, null);
        }

        if (authProperties.isDevLogOtp()) {
            log.warn(
                    "DEV sign-in code for {}: {} (expires in {} min). Configure spring.mail.* to email this code.",
                    mask(normalized),
                    code,
                    authProperties.getOtpTtlMinutes());
        }

        if (!authProperties.isDevLogOtp() && authProperties.getDevFixedOtp().isBlank()) {
            throw new IllegalStateException(
                    "Could not send sign-in email. Set spring.mail.host, spring.mail.username, and "
                            + " in application.properties.");
        }

        String devCode = shouldExposeDevCode() ? code : null;
        if (!authProperties.getDevFixedOtp().isBlank()) {
            return new SendCodeResult(
                    "SMTP is not configured. Use sign-in code: " + code + ".", false, devCode);
        }
        return new SendCodeResult(
                "SMTP is not configured. Use the sign-in code shown below.", false, devCode);
    }

    public boolean verifyCode(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String normalized;
        try {
            normalized = normalizeEmail(email);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        Optional<EmailOtpStore.PendingOtp> pending = otpStore.get(normalized);
        if (pending.isEmpty()) {
            return false;
        }
        boolean ok = pending.get().code().equals(code.trim());
        if (ok) {
            otpStore.remove(normalized);
        }
        return ok;
    }

    private void sendEmailOrThrow(String email, String code) {
        String from = resolveFromAddress();
        if (from.isBlank()) {
            throw new IllegalStateException(
                    "Set support.auth.mail.from or spring.mail.username in application.properties.");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("Your sign-in code — Support Error Alert Dashboard");
            message.setText(
                    "Your one-time sign-in code is: " + code + "\n\n"
                            + "It expires in " + authProperties.getOtpTtlMinutes() + " minutes.\n\n"
                            + "If you did not request this, you can ignore this email.");
            mailSender.send(message);
            log.info("Sign-in code emailed to {}", mask(email));
        } catch (Exception ex) {
            log.error("Failed to send sign-in email to {}: {}", mask(email), ex.getMessage());
            throw new IllegalStateException(
                    "Could not send sign-in email: " + ex.getMessage()
                            + ". Check spring.mail.* and use a Gmail App Password if using Gmail.",
                    ex);
        }
    }

    private boolean shouldExposeDevCode() {
        return authProperties.isDevLogOtp() || !authProperties.getDevFixedOtp().isBlank();
    }

    private boolean trySendEmail(String email, String code) {
        if (!MailAuthSupport.isMailSenderReady(mailSender, environment)) {
            return false;
        }
        try {
            sendEmailOrThrow(email, code);
            return true;
        } catch (IllegalStateException ex) {
            log.warn("{}", ex.getMessage());
            return false;
        }
    }

    private String resolveFromAddress() {
        String from = authProperties.getMailFrom();
        if (from != null && !from.isBlank()) {
            return from.trim();
        }
        String username = environment.getProperty("spring.mail.username");
        return username != null ? username.trim() : "";
    }

    private static String generateCode() {
        int n = RANDOM.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required.");
        }
        String t = email.trim().toLowerCase();
        if (!t.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email address.");
        }
        return t;
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
