package com.nexploy.campuslink.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.otp.entity.OtpVerification;
import com.nexploy.campuslink.auth.otp.repository.OtpVerificationRepository;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
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
 * End-to-end HTTP integration test for self-service registration (student + recruiter). Since V60
 * student registration is directory-gated: the officer-seeded DIRECTORY row is CLAIMED (never
 * duplicated) only after the registrant proves email ownership with an OTP, and the slim form
 * carries credentials only — identity and academics come from the directory. Recruiter registration
 * still creates both rows in one transaction. Staff roles have no registration endpoint at all, so
 * the privilege-escalation guard is structural. Runs against in-memory H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RegistrationEndpointIntegrationTest {

    private static final String KNOWN_OTP = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        otpRepository.deleteAll();
        userAccountRepository.deleteAll();
        studentRepository.deleteAll();
        recruiterRepository.deleteAll();
        campusRepository.deleteAll();
        campusRepository.save(Campus.builder()
            .id("campus_a").name("Campus A").city("Bengaluru").state("KA").active(true).build());
        campusRepository.save(Campus.builder()
            .id("campus_inactive").name("Old Campus").city("Pune").state("MH").active(false).build());
        seedDirectoryRow("rahul@student.example");
    }

    /** An officer-managed DIRECTORY record with no account — what registration claims (V60). */
    private void seedDirectoryRow(String email) {
        studentRepository.save(Student.builder()
            .id("stu_" + email.replace('@', '_'))
            .campusId("campus_a").name("Rahul Sharma").email(email)
            .rollNumber("CS-2026-001").branch("Computer Science").graduationYear(2026)
            .cgpa(new BigDecimal("8.1")).backlogs(0)
            .source("DIRECTORY").active(true).readinessScore(0).placementStatus("UNPLACED").build());
    }

    /** Seeds the OTP challenge the way {@code POST /api/auth/otp/send} would, but with a known code. */
    private void seedOtp(String email) {
        OffsetDateTime now = OffsetDateTime.now();
        otpRepository.save(OtpVerification.builder()
            .id("otp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .email(email).campusId("campus_a")
            .codeHash(passwordEncoder.encode(KNOWN_OTP))
            .expiresAt(now.plusMinutes(5)).attempts(0).consumed(false).createdAt(now)
            .build());
    }

    /** The full V60 flow for a directory email: request a code, then register with it. */
    private void registerWithOtp(String email) throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"" + email + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.otpRequired").value(true));
        // The real send hashes a random code we cannot read (no mail sender in the test
        // profile), so seed a known challenge on top — verification picks the newest row.
        seedOtp(email);
        String body = studentRegistration(email, "s3cretpass", KNOWN_OTP);
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private static String studentRegistration(String email, String password, String otp) {
        return """
            { "email": "%s", "password": "%s", "phone": "+91 90000 00000",
              "campusId": "campus_a", "otp": "%s" }
            """.formatted(email, password, otp == null ? "" : otp);
    }

    private static final String RECRUITER_BODY = """
        {
          "fullName": "Asha Menon",
          "email": "asha@technova.example",
          "phone": "+91 90000 11111",
          "password": "s3cretpass",
          "campusId": "campus_a",
          "companyName": "TechNova",
          "companyWebsite": "https://technova.example",
          "companyIndustry": "Software",
          "companyLocation": "Bengaluru, India",
          "designation": "Talent Lead"
        }
        """;

    @Test
    void studentRegistrationClaimsDirectoryRowAndSignsIn() throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"rahul@student.example\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.otpRequired").value(true));
        seedOtp("rahul@student.example");

        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentRegistration("rahul@student.example", "s3cretpass", KNOWN_OTP)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"));

        UserAccount account = userAccountRepository.findById("rahul@student.example").orElseThrow();
        assertThat(account.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(account.getCampusId()).isEqualTo("campus_a");
        assertThat(account.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("s3cretpass", account.getPassword())).isTrue();

        // The DIRECTORY row is claimed, never duplicated: exactly one row, officer data intact.
        assertThat(studentRepository.findAll()).hasSize(1);
        Student student = studentRepository.findAll().get(0);
        assertThat(student.getSource()).isEqualTo("DIRECTORY");
        assertThat(student.getName()).isEqualTo("Rahul Sharma");
        assertThat(student.getRollNumber()).isEqualTo("CS-2026-001");
        assertThat(student.getBranch()).isEqualTo("Computer Science");
        assertThat(student.getGraduationYear()).isEqualTo(2026);
        assertThat(student.getCgpa()).isEqualByComparingTo("8.1");
        assertThat(student.getPhone()).isEqualTo("+91 90000 00000");
        assertThat(student.getCampusId()).isEqualTo("campus_a");
    }

    @Test
    void rollNumberDefaultsWhenDirectoryLeftItBlank() throws Exception {
        studentRepository.save(Student.builder()
            .id("stu_noroll").campusId("campus_a").name("No Roll")
            .email("noroll@student.example")
            .source("DIRECTORY").active(true).readinessScore(0).placementStatus("UNPLACED").build());

        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentRegistration("noroll@student.example", "s3cretpass", null)))
            .andExpect(status().isBadRequest());
        seedOtp("noroll@student.example");
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentRegistration("noroll@student.example", "s3cretpass", KNOWN_OTP)))
            .andExpect(status().isCreated());
        Student student = studentRepository.findAll().stream()
            .filter(s -> "noroll@student.example".equals(s.getEmail())).findFirst().orElseThrow();
        assertThat(student.getRollNumber()).isNotBlank();
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        registerWithOtp("rahul@student.example");
        // The account already exists; even a fresh code cannot create a second one — the
        // duplicate surfaces as a 409 from the account-uniqueness check. Seed the code
        // directly (bypassing the resend cooldown) and try to register again.
        seedOtp("rahul@student.example");
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentRegistration("rahul@student.example", "s3cretpass", KNOWN_OTP)))
            .andExpect(status().isConflict());
    }

    @Test
    void caseVariantDirectoryEmailIsRejectedAsDuplicate() throws Exception {
        // Once the lowercase record is claimed, the same address in different casing is an
        // already-registered account — the username uniqueness check is case-insensitive.
        registerWithOtp("rahul@student.example");
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"Rahul@Student.example\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void unknownCampusIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_missing\", \"email\": \"rahul@student.example\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void inactiveCampusIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_inactive\", \"email\": \"rahul@student.example\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredRecruiterFieldFailsValidation() throws Exception {
        // No companyIndustry -> bean validation rejects with 400.
        String missing = """
            { "fullName": "X", "email": "x@c.example", "phone": "1", "password": "s3cretpass",
              "campusId": "campus_a", "companyName": "C", "companyLocation": "L", "designation": "D" }
            """;
        mockMvc.perform(post("/api/auth/register/recruiter")
                .contentType(MediaType.APPLICATION_JSON).content(missing))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shortPasswordFailsValidation() throws Exception {
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentRegistration("rahul@student.example", "short", KNOWN_OTP)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailFailsValidation() throws Exception {
        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentRegistration("not-an-email", "s3cretpass", KNOWN_OTP)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void publicCampusListReturnsOnlyActiveCampusesWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/campuses/public"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("campus_a"));
    }

    @Test
    void recruiterRegistrationCreatesAccountAndCompanyProfile() throws Exception {
        mockMvc.perform(post("/api/auth/register/recruiter")
                .contentType(MediaType.APPLICATION_JSON).content(RECRUITER_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        UserAccount account = userAccountRepository.findById("asha@technova.example").orElseThrow();
        assertThat(account.getRole()).isEqualTo(UserRole.RECRUITER);

        Recruiter recruiter = recruiterRepository.findByCampusId("campus_a").stream()
            .filter(r -> "asha@technova.example".equals(r.getEmail())).findFirst().orElseThrow();
        assertThat(recruiter.getName()).isEqualTo("Asha Menon");
        assertThat(recruiter.getCompanyName()).isEqualTo("TechNova");
        assertThat(recruiter.getCompanyWebsite()).isEqualTo("https://technova.example");
        assertThat(recruiter.getCompanyIndustry()).isEqualTo("Software");
        assertThat(recruiter.getCompanyLocation()).isEqualTo("Bengaluru, India");
        assertThat(recruiter.getDesignation()).isEqualTo("Talent Lead");
        assertThat(recruiter.isActive()).isTrue();
    }

    @Test
    void caseVariantEmailClaimNormalizesCaseAndMeResolves() throws Exception {
        // Directory stores lowercase; the student types a cased variant. The claim must normalize
        // email to the typed case so email == username holds byte-for-byte for /me resolution.
        registerWithOtp("Rahul@student.example");

        String loginBody = objectMapper.writeValueAsString(
            new com.nexploy.campuslink.auth.dto.LoginRequest("Rahul@student.example", "s3cretpass"));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode accessToken = objectMapper.readTree(response).get("accessToken");
        assertThat(accessToken).isNotNull();

        mockMvc.perform(get("/api/students/me")
                .header("Authorization", "Bearer " + accessToken.asText()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("Rahul@student.example"))
            .andExpect(jsonPath("$.name").value("Rahul Sharma"))
            .andExpect(jsonPath("$.source").value("DIRECTORY"));
    }
}