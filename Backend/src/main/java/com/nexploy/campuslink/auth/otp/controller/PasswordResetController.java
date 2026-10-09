package com.nexploy.campuslink.auth.otp.controller;

import com.nexploy.campuslink.auth.otp.dto.PasswordResetOtpRequest;
import com.nexploy.campuslink.auth.otp.dto.PasswordResetOtpResponse;
import com.nexploy.campuslink.auth.otp.dto.PasswordResetRequest;
import com.nexploy.campuslink.auth.otp.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public email-OTP password recovery endpoints. */
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private static final String GENERIC_MESSAGE =
        "If an account exists for this email, a verification code has been sent.";

    private final OtpService otpService;

    @PostMapping("/otp")
    public PasswordResetOtpResponse sendOtp(@Valid @RequestBody PasswordResetOtpRequest request) {
        otpService.sendPasswordResetOtp(request.email());
        return new PasswordResetOtpResponse(GENERIC_MESSAGE);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        otpService.resetPassword(request.email(), request.otp(), request.newPassword());
    }
}
