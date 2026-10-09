package com.nexploy.campuslink.auth.otp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registration OTP request (V60): the campus plus the directory email the code should go to. */
public record OtpSendRequest(
    @NotBlank String campusId,
    @NotBlank @Email @Size(max = 320) String email
) {
}
