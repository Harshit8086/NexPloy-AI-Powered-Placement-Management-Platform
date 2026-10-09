package com.nexploy.campuslink.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.otp.entity.OtpVerification;
import com.nexploy.campuslink.auth.otp.repository.OtpVerificationRepository;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP integration tests for the V60 registration security layer: directory gating (not in the
 * directory, deactivated record, cross-campus scope) and email OTP verification (wrong-attempt cap,
 * expiry, single-use, resend cooldown). OTP rows are seeded directly with a known code hash — the
 * test profile has no JavaMailSender, so the plaintext is never captured from a real send.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DirectoryRegistrationOtpIntegrationTest {

    private static final String KNOWN_OTP = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        otpRepository.deleteAll();
        userAccountRepository.deleteAll();
        studentRepository.deleteAll();
        campusRepository.deleteAll();
        campusRepository.save(Campus.builder()
            .id("campus_a").name("Campus A").city("Bengaluru").state("KA").active(true).build());
        campusRepository.save(Campus.builder()
            .id("campus_b").name("Campus B").city("Mysuru").state("KA").active(true).build());
        seedDirectoryRow("campus_a", "priya@student.example");
    }

    private void seedDirectoryRow(String campusId, String email) {
        studentRepository.save(Student.builder()
            .id("stu_" + campusId + "_" + email.replace('@', '_'))
            .campusId(campusId).name("Priya Verma").email(email)
            .rollNumber("EC-2026-007").branch("ECE").graduationYear(2026)
            .cgpa(new BigDecimal("8.4")).backlogs(0)
            .source("DIRECTORY").active(true).readinessScore(0).placementStatus("UNPLACED").build());
    }

    /** Seeds an OTP challenge with a known code, fresh expiry, and zero wrong attempts. */
    private void seedOtp(String campusId, String email) {
        OffsetDateTime now = OffsetDateTime.now();
        otpRepository.save(OtpVerification.builder()
            .id("otp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .email(email).campusId(campusId)
            .codeHash(passwordEncoder.encode(KNOWN_OTP))
            .expiresAt(now.plusMinutes(5)).attempts(0).consumed(false).createdAt(now)
            .build());
    }

    private void expectSend(String email, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"" + email + "\"}"))
            .andExpect(status().is(expectedStatus));
    }

    private void expectRegister(String email, String otp, int expectedStatus) throws Exception {
        String body = """
            { "email": "%s", "password": "s3cretpass", "phone": "+91 90000 22222",
              "campusId": "campus_a", "otp": "%s" }
            """.formatted(email, otp == null ? "" : otp);
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expectedStatus));
    }

    // ---- Directory gating ----

    @Test
    void notInDirectoryIsBlockedWithActionableMessage() throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"ghost@student.example\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("not in your campus directory")));
        // Registration is gated the same way even if someone skips the OTP step.
        expectRegister("ghost@student.example", KNOWN_OTP, 400);
    }

    @Test
    void deactivatedDirectoryRecordBlocksRegistration() throws Exception {
        Student record = studentRepository.findAll().get(0);
        record.setActive(false);
        studentRepository.save(record);

        expectSend("priya@student.example", 400);
        seedOtp("campus_a", "priya@student.example");
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "priya@student.example", "password": "s3cretpass",
                      "campusId": "campus_a", "otp": "%s" }
                    """.formatted(KNOWN_OTP)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("deactivated")));
    }

    @Test
    void sameEmailInDifferentCampusIsBlocked() throws Exception {
        // The (campus, email) identity is per campus: priya is in campus_a's directory only.
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_b\", \"email\": \"priya@student.example\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void emailWithExistingAccountIsRejectedAtOtpSend() throws Exception {
        // Simulate an already-claimed record: an account exists for the directory email.
        userAccountRepository.save(com.nexploy.campuslink.auth.entity.UserAccount.builder()
            .username("priya@student.example").password(passwordEncoder.encode("s3cretpass"))
            .campusId("campus_a")
            .role(com.nexploy.campuslink.auth.entity.UserRole.STUDENT).enabled(true).build());

        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"priya@student.example\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void preloadedLegacyStudentCanClaimExistingRecordAndSetPassword() throws Exception {
        Student existing = studentRepository.findAll().get(0);
        existing.setSource("SELF_REGISTERED");
        studentRepository.save(existing);

        expectSend("priya@student.example", 200);
        seedOtp("campus_a", "priya@student.example");

        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "priya@student.example", "password": "first-password",
                      "phone": "+91 90000 22222", "campusId": "campus_a", "otp": "%s" }
                    """.formatted(KNOWN_OTP)))
            .andExpect(status().isCreated());

        assertThat(studentRepository.findAll()).hasSize(1);
        Student claimed = studentRepository.findAll().get(0);
        assertThat(claimed.getId()).isEqualTo(existing.getId());
        assertThat(claimed.getSource()).isEqualTo("SELF_REGISTERED");
        assertThat(userAccountRepository.findByUsernameIgnoreCase("priya@student.example"))
            .isPresent()
            .get()
            .extracting(account -> passwordEncoder.matches("first-password", account.getPassword()))
            .isEqualTo(true);
    }

    // ---- OTP rules ----

    @Test
    void wrongCodeIsRejectedAndCountsAttempts() throws Exception {
        seedOtp("campus_a", "priya@student.example");
        expectRegister("priya@student.example", "000000", 400);
        OtpVerification challenge = otpRepository.findAll().get(0);
        assertThat(challenge.getAttempts()).isEqualTo(1);
        assertThat(challenge.getConsumed()).isFalse();
        assertThat(userAccountRepository.existsByUsernameIgnoreCase("priya@student.example")).isFalse();
    }

    @Test
    void threeWrongAttemptsCapTheChallenge() throws Exception {
        seedOtp("campus_a", "priya@student.example");
        expectRegister("priya@student.example", "111111", 400);
        expectRegister("priya@student.example", "222222", 400);
        expectRegister("priya@student.example", "333333", 400);
        // Even the correct code is refused once the attempt cap is hit.
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "priya@student.example", "password": "s3cretpass",
                      "campusId": "campus_a", "otp": "%s" }
                    """.formatted(KNOWN_OTP)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("Too many incorrect attempts")));
        assertThat(userAccountRepository.existsByUsernameIgnoreCase("priya@student.example")).isFalse();
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        otpRepository.save(OtpVerification.builder()
            .id("otp_expired").email("priya@student.example").campusId("campus_a")
            .codeHash(passwordEncoder.encode(KNOWN_OTP))
            .expiresAt(now.minusMinutes(1)).attempts(0).consumed(false).createdAt(now.minusMinutes(6))
            .build());

        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "priya@student.example", "password": "s3cretpass",
                      "campusId": "campus_a", "otp": "%s" }
                    """.formatted(KNOWN_OTP)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void missingCodeIsRejectedWithGuidance() throws Exception {
        expectRegister("priya@student.example", null, 400);
    }

    @Test
    void consumedCodeCannotBeReused() throws Exception {
        seedOtp("campus_a", "priya@student.example");
        expectRegister("priya@student.example", KNOWN_OTP, 201);
        // A consumed challenge is invisible to verification: asking again says "request a code first".
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "priya@student.example", "password": "s3cretpass",
                      "campusId": "campus_a", "otp": "%s" }
                    """.formatted(KNOWN_OTP)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void resendWithinCooldownIsRejected() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        otpRepository.save(OtpVerification.builder()
            .id("otp_recent").email("priya@student.example").campusId("campus_a")
            .codeHash(passwordEncoder.encode(KNOWN_OTP))
            .expiresAt(now.plusMinutes(5)).attempts(0).consumed(false)
            .createdAt(now.minusSeconds(10))
            .build());

        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"priya@student.example\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("wait")));
    }

    }