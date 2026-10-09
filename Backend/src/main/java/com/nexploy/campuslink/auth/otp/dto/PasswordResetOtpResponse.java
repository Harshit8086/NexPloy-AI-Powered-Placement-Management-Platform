package com.nexploy.campuslink.auth.otp.dto;

/** Deliberately identical regardless of whether an account exists for the submitted email. */
public record PasswordResetOtpResponse(String message) {
}
