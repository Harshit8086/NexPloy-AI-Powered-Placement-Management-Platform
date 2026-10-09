package com.nexploy.campuslink.auth.otp.dto;

/**
 * OTP send outcome (V60). {@code otpRequired=false} means verification is disabled
 * ({@code nexploy.auth.otp.enabled=false} — the no-SMTP prototype fallback), and the client
 * should skip the OTP step and go straight to registration.
 */
public record OtpSendResponse(boolean otpRequired) {
}
