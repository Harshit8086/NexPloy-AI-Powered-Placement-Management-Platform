package com.nexploy.campuslink.auth.otp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Completes password recovery using a one-time code sent to the account email. */
public record PasswordResetRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Pattern(regexp = "\\d{6}") String otp,
    @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
