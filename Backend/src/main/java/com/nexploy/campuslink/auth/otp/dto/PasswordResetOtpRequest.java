package com.nexploy.campuslink.auth.otp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public password-recovery request. */
public record PasswordResetOtpRequest(
    @NotBlank @Email @Size(max = 320) String email
) {
}
