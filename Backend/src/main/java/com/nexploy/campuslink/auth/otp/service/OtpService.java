package com.nexploy.campuslink.auth.otp.service;

import com.nexploy.campuslink.auth.exception.UsernameAlreadyExistsException;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.otp.entity.OtpVerification;
import com.nexploy.campuslink.auth.otp.repository.OtpVerificationRepository;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.auth.service.RefreshTokenService;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotInDirectoryException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Email OTPs for registration and password recovery. Codes are 6 digits, expire in 5 minutes,
 * allow at most 3 wrong guesses, are hashed on store (never persisted in plaintext), single-use,
 * and rate-limited by a 60s resend cooldown.
 *
 * <p>Delivery goes straight through {@link JavaMailSender} — the notification pipeline is an
 * async/scheduled dispatcher and far too slow for a code the user is typing right now.
 *
 * <p>Prototype fallback: with {@code nexploy.auth.otp.enabled=false} no code is issued and
 * verification is a no-op, so a demo without SMTP credentials still registers.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int CODE_BOUND = 1_000_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long EXPIRY_MINUTES = 5;
    private static final long RESEND_COOLDOWN_SECONDS = 60;

    private final OtpVerificationRepository otpRepository;
    private final StudentRepository studentRepository;
    private final CampusRepository campusRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    /** Commits the wrong-attempt counter independently of the caller's rolling-back transaction. */
    private final PlatformTransactionManager transactionManager;
    /** Optional: only autoconfigured when spring.mail.host is set; absence never blocks startup. */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${nexploy.auth.otp.enabled:true}")
    private boolean otpEnabled;

    @Value("${spring.mail.username:}")
    private String from;

    /**
     * Issues a registration OTP after running the SAME pre-checks registration itself applies
     * (active campus, no account yet, an active DIRECTORY record), so the student gets a clear,
     * actionable error before ever typing a code. Returns {@code false} when OTP verification is
     * disabled — the client then skips the OTP step.
     */
    @Transactional
    public boolean sendOtp(String campusId, String email) {
        if (!otpEnabled) {
            return false;
        }
        requireRegistrationEligible(campusId, email);

        OffsetDateTime now = OffsetDateTime.now();
        otpRepository
            .findTopByEmailIgnoreCaseAndCampusIdAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
                email, campusId, "REGISTRATION", now.minusSeconds(RESEND_COOLDOWN_SECONDS))
            .ifPresent(recent -> {
                throw new IllegalArgumentException(
                    "A code was sent recently. Please wait a moment before requesting another.");
            });

        String code = String.format("%06d", secureRandom.nextInt(CODE_BOUND));
        otpRepository.save(OtpVerification.builder()
            .id("otp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .email(email)
            .campusId(campusId)
            .purpose("REGISTRATION")
            .codeHash(passwordEncoder.encode(code))
            .expiresAt(now.plusMinutes(EXPIRY_MINUTES))
            .attempts(0)
            .consumed(false)
            .createdAt(now)
            .build());
        deliver(email, code);
        // Opportunistic sweep keeps expired challenges from accumulating.
        otpRepository.deleteByExpiresAtBefore(now);
        return true;
    }

    /**
     * Verifies the presented code and marks the challenge consumed (single-use). A no-op when OTP
     * verification is disabled. A missing or expired challenge, a capped challenge, or a wrong
     * guess is a 400 asking for a fresh code; wrong guesses increment the attempt counter.
     */
    @Transactional
    public void verifyAndConsume(String email, String campusId, String code) {
        if (!otpEnabled) {
            return;
        }
        OtpVerification challenge = otpRepository
            .findTopByEmailIgnoreCaseAndCampusIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                email, campusId, "REGISTRATION")
            .orElseThrow(() -> new IllegalArgumentException(
                "Verification code required. Request a code first."));
        verifyAndConsume(challenge, code);
    }

    /**
     * Sends a password-reset code when the email belongs to an enabled account. Unknown addresses
     * receive the same successful response to avoid disclosing account existence.
     */
    @Transactional
    public void sendPasswordResetOtp(String email) {
        if (!otpEnabled) {
            throw new IllegalStateException("Email OTP is not enabled on this server.");
        }
        UserAccount account = userAccountRepository.findByUsernameIgnoreCase(email.trim()).orElse(null);
        if (account == null || !account.isEnabled()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (otpRepository
            .findTopByEmailIgnoreCaseAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
                account.getUsername(), "PASSWORD_RESET", now.minusSeconds(RESEND_COOLDOWN_SECONDS))
            .isPresent()) {
            return;
        }
        String code = String.format("%06d", secureRandom.nextInt(CODE_BOUND));
        otpRepository.save(OtpVerification.builder()
            .id("otp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .email(account.getUsername())
            .campusId(account.getCampusId())
            .purpose("PASSWORD_RESET")
            .codeHash(passwordEncoder.encode(code))
            .expiresAt(now.plusMinutes(EXPIRY_MINUTES))
            .attempts(0)
            .consumed(false)
            .createdAt(now)
            .build());
        deliverRequired(account.getUsername(), code, "Your CampusLink password reset code");
        otpRepository.deleteByExpiresAtBefore(now);
    }

    /** Verifies a reset OTP, changes the password, and revokes refresh tokens in one transaction. */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        if (!otpEnabled) {
            throw new IllegalStateException("Email OTP is not enabled on this server.");
        }
        UserAccount account = userAccountRepository.findByUsernameIgnoreCase(email.trim())
            .filter(UserAccount::isEnabled)
            .orElseThrow(() -> new IllegalArgumentException("The code is invalid or has expired."));
        OtpVerification challenge = otpRepository
            .findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                account.getUsername(), "PASSWORD_RESET")
            .orElseThrow(() -> new IllegalArgumentException(
                "Verification code required. Request a new code."));
        verifyAndConsume(challenge, code);
        account.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(account);
        refreshTokenService.revokeAll(account.getUsername());
    }

    private void verifyAndConsume(OtpVerification challenge, String code) {
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("The verification code has expired. Request a new one.");
        }
        if (challenge.getAttempts() != null && challenge.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Too many incorrect attempts. Request a new code.");
        }
        if (code == null || code.isBlank() || !passwordEncoder.matches(code.trim(), challenge.getCodeHash())) {
            challenge.setAttempts((challenge.getAttempts() == null ? 0 : challenge.getAttempts()) + 1);
            // Commit the counter in its OWN transaction: this method joins the registration's
            // transaction, and the throw below rolls it back. An increment that rolled back would
            // let an attacker guess forever and the attempt cap could never fire.
            TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(tx -> otpRepository.save(challenge));
            throw new IllegalArgumentException("Incorrect verification code.");
        }
        challenge.setConsumed(true);
        otpRepository.save(challenge);
    }

    /** True when OTP verification is active (drives the client's OTP-step visibility). */
    public boolean isEnabled() {
        return otpEnabled;
    }

    /**
     * The directory pre-checks shared with registration. Raising them here — before a code is
     * sent — means the student never receives an OTP they cannot use.
     */
    private void requireRegistrationEligible(String campusId, String email) {
        Campus campus = campusRepository.findById(campusId)
            .orElseThrow(() -> new IllegalArgumentException("Campus not found: " + campusId));
        if (!campus.isActive()) {
            throw new IllegalArgumentException("Campus is not accepting registrations: " + campusId);
        }
        if (userAccountRepository.existsByUsernameIgnoreCase(email)) {
            throw new UsernameAlreadyExistsException(email);
        }
        Student record = studentRepository.findByCampusIdAndEmailIgnoreCase(campusId, email)
            .orElseThrow(StudentNotInDirectoryException::new);
        if (!Boolean.TRUE.equals(record.getActive())) {
            throw new IllegalArgumentException(
                "Your directory record is deactivated. Contact your placement office.");
        }
    }

    /** Sends the code immediately over SMTP (the notification dispatcher is async — too slow for an OTP). */
    private void deliver(String to, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return;
        }
        deliver(mailSender, to, code, "Your CampusLink verification code");
    }

    private void deliverRequired(String to, String code, String subject) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("Email delivery is not configured on this server.");
        }
        deliver(mailSender, to, code, subject);
    }

    private void deliver(JavaMailSender mailSender, String to, String code, String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText("Your verification code is " + code
            + ". It expires in " + EXPIRY_MINUTES
            + " minutes. If you did not request it, ignore this email.");
        mailSender.send(message);
    }
}
