package com.nexploy.campuslink.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Change-password request for the signed-in user (POST /api/auth/change-password). The current
 * password is re-verified server-side before the new one is accepted; the new password must meet
 * a minimum length. Passwords are never logged and only the BCrypt hash is persisted.
 */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
