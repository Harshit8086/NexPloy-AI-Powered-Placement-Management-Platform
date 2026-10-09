package com.nexploy.campuslink.auth.otp.controller;

import com.nexploy.campuslink.auth.otp.dto.OtpSendRequest;
import com.nexploy.campuslink.auth.otp.dto.OtpSendResponse;
import com.nexploy.campuslink.auth.otp.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration OTP (V60): proves the registrant owns the directory email BEFORE the account is
 * created. Public (no JWT) — it necessarily runs before any authentication exists; the directory
 * pre-checks inside the service keep it from becoming an account-probing surface.
 */
@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    public OtpSendResponse send(@Valid @RequestBody OtpSendRequest request) {
        boolean sent = otpService.sendOtp(request.campusId(), request.email());
        return new OtpSendResponse(sent);
    }
}
