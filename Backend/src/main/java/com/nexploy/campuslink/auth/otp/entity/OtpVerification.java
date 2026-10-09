package com.nexploy.campuslink.auth.otp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One email OTP challenge. Only the hash of the 6-digit code is stored; the row is single-use
 * ({@code consumed}) and expires, and wrong guesses are counted against an attempt cap. Purpose
 * separates registration verification from account password recovery.
 */
@Entity
@Table(name = "otp_verifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerification {

    @Id
    private String id;

    private String email;
    private String campusId;
    @Default
    private String purpose = "REGISTRATION";

    /** Hash of the 6-digit code; the plaintext is never persisted. */
    private String codeHash;

    private OffsetDateTime expiresAt;

    /** Wrong guesses so far; the row becomes unusable once the cap is hit. */
    private Integer attempts;

    /** True once the code verified a registration; a consumed row can never be reused. */
    private Boolean consumed;

    private OffsetDateTime createdAt;
}
