package com.support.alert.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmailOtpStore {

    private final Map<String, PendingOtp> pending = new ConcurrentHashMap<>();

    public void put(String email, String code, Instant expiresAt) {
        pending.put(normalize(email), new PendingOtp(code, expiresAt));
    }

    public Optional<PendingOtp> get(String email) {
        PendingOtp otp = pending.get(normalize(email));
        if (otp == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(otp.expiresAt())) {
            pending.remove(normalize(email));
            return Optional.empty();
        }
        return Optional.of(otp);
    }

    public void remove(String email) {
        pending.remove(normalize(email));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    public record PendingOtp(String code, Instant expiresAt) {}
}
