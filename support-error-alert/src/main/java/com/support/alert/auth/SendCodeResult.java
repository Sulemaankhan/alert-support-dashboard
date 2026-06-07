package com.support.alert.auth;

/**
 * Result of requesting a sign-in OTP.
 *
 * @param message user-facing summary
 * @param emailSent true when SMTP delivered the message
 * @param devCode OTP shown in UI when email was not sent (local dev only)
 */
public record SendCodeResult(String message, boolean emailSent, String devCode) {}
