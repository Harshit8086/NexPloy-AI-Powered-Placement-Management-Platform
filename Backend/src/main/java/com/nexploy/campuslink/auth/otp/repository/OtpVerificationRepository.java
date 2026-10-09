package com.nexploy.campuslink.auth.otp.repository;

import com.nexploy.campuslink.auth.otp.entity.OtpVerification;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, String> {

    /** The latest unconsumed OTP for this email + campus (email compared case-insensitively). */
    Optional<OtpVerification> findTopByEmailIgnoreCaseAndCampusIdAndConsumedFalseOrderByCreatedAtDesc(
        String email, String campusId);

    Optional<OtpVerification> findTopByEmailIgnoreCaseAndCampusIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
        String email, String campusId, String purpose);

    Optional<OtpVerification> findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
        String email, String purpose);

    /** Any OTP (even consumed) issued for this email + campus within the resend-cooldown window. */
    Optional<OtpVerification> findTopByEmailIgnoreCaseAndCampusIdAndCreatedAtAfterOrderByCreatedAtDesc(
        String email, String campusId, OffsetDateTime since);

    Optional<OtpVerification> findTopByEmailIgnoreCaseAndCampusIdAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
        String email, String campusId, String purpose, OffsetDateTime since);

    Optional<OtpVerification> findTopByEmailIgnoreCaseAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
        String email, String purpose, OffsetDateTime since);

    void deleteByExpiresAtBefore(OffsetDateTime cutoff);
}