package com.nexploy.campuslink.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Directory-gated student registration (V60). Identity and academic fields no longer come from
 * this form — the officer-managed campus directory supplies them when the student claims their
 * record. The registrant supplies only login credentials plus an optional contact phone, and
 * proves email ownership with the OTP issued by {@code POST /api/auth/otp/send}. The {@code otp}
 * may be omitted only when verification is disabled ({@code nexploy.auth.otp.enabled=false}).
 * The confirm-password check is performed client-side and never sent here.
 */
public record StudentRegisterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @Size(max = 32) String phone,
    @NotBlank String campusId,
    @Size(max = 16) String otp
) {
}
