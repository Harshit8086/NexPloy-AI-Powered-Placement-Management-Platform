package com.nexploy.campuslink.student.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.nexploy.campuslink.student.dto.SetStudentActiveRequest;
import com.nexploy.campuslink.student.dto.StudentDirectoryCreateRequest;
import com.nexploy.campuslink.student.dto.StudentDirectoryUpdateRequest;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP integration tests for the officer-managed student directory (V60): POST /api/students,
 * POST /api/students/import (CSV), PUT /api/students/{id}/directory, and PATCH /{id}/active.
 * Directory management is officer/admin-only; other roles are forbidden. Deactivation is soft and
 * disables the linked account (login -> 401). Academic fields are directory-owned, so a student's
 * own PUT /api/students/me cannot forge them even if an old client still sends them.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DirectoryManagementEndpointIntegrationTest {

    private static final String KNOWN_OTP = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        seedDirectoryRow("stu_seeded", "campus_a", "seeded@student.example");
    }

    private void seedDirectoryRow(String id, String campusId, String email) {
        studentRepository.save(Student.builder()
            .id(id).campusId(campusId).name("Seeded Student").email(email)
            .rollNumber("R-100").branch("CSE").cgpa(new BigDecimal("7.9")).backlogs(0)
            .source("DIRECTORY").active(true).readinessScore(0).placementStatus("UNPLACED").build());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private StudentDirectoryCreateRequest createRequest(String email) {
        return new StudentDirectoryCreateRequest(
            "Anita Rao", email, "CS-2026-042", "campus_a", "Computer Science", "CS",
            "CSE", "2022-26", 6, 2026, "3rd Year",
            new BigDecimal("8.5"), new BigDecimal("91.2"), new BigDecimal("88.4"),
            0, "+91 90000 33333");
    }

    /** Registers a directory student through the full V60 flow and returns the access token. */
    private String registerDirectoryStudent(String studentId, String email) throws Exception {
        Student seeded = studentRepository.findById(studentId).orElseThrow();
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType("application/json")
                .content("{\"campusId\": \"" + seeded.getCampusId() + "\", \"email\": \"" + email + "\"}"))
            .andExpect(status().isOk());
        OffsetDateTime now = OffsetDateTime.now();
        otpRepository.save(OtpVerification.builder()
            .id("otp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .email(email).campusId(seeded.getCampusId())
            .codeHash(passwordEncoder.encode(KNOWN_OTP))
            .expiresAt(now.plusMinutes(5)).attempts(0).consumed(false).createdAt(now)
            .build());
        String response = mockMvc.perform(post("/api/auth/register/student")
                .contentType("application/json")
                .content("""
                    { "email": "%s", "password": "s3cretpass",
                      "campusId": "%s", "otp": "%s" }
                    """.formatted(email, seeded.getCampusId(), KNOWN_OTP)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode accessToken = objectMapper.readTree(response).get("accessToken");
        assertThat(accessToken).isNotNull();
        return accessToken.asText();
    }

    // ---- Create ----

    @Test
    void officerCreatesDirectoryRecord() throws Exception {
        mockMvc.perform(post("/api/students")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(createRequest("anita@student.example"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("anita@student.example"))
            .andExpect(jsonPath("$.source").value("DIRECTORY"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.cgpa").value(8.5));
        // No account is created — the record waits to be claimed.
        assertThat(userAccountRepository.existsByUsernameIgnoreCase("anita@student.example")).isFalse();
    }

    @Test
    void duplicateEmailCreateIsConflict() throws Exception {
        mockMvc.perform(post("/api/students")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(createRequest("seeded@student.example"))))
            .andExpect(status().isConflict());
    }

    @Test
    void studentRoleCannotCreateDirectoryRecord() throws Exception {
        mockMvc.perform(post("/api/students")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(createRequest("sneak@student.example"))))
            .andExpect(status().isForbidden());
    }

    // ---- Directory edit ----

    @Test
    void officerEditsDirectoryFields() throws Exception {
        mockMvc.perform(put("/api/students/stu_seeded/directory")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new StudentDirectoryUpdateRequest(
                    null, null, null, null, null, null, null, null, null, null,
                    new BigDecimal("9.0"), null, null, 1, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cgpa").value(9.0))
            .andExpect(jsonPath("$.backlogs").value(1));
    }

    @Test
    void claimedRecordEmailChangeIsConflict() throws Exception {
        registerDirectoryStudent("stu_seeded", "seeded@student.example");
        mockMvc.perform(put("/api/students/stu_seeded/directory")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new StudentDirectoryUpdateRequest(
                    null, "renamed@student.example", null, null, null, null, null, null, null,
                    null, null, null, null, null, null))))
            .andExpect(status().isConflict());
    }

    @Test
    void unclaimedRecordEmailMayChange() throws Exception {
        mockMvc.perform(put("/api/students/stu_seeded/directory")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new StudentDirectoryUpdateRequest(
                    null, "fresh@student.example", null, null, null, null, null, null, null,
                    null, null, null, null, null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("fresh@student.example"));
    }

    // ---- Soft-active toggle + login gating ----

    @Test
    void deactivateDisablesLinkedAccountAndBlocksLogin() throws Exception {
        String token = registerDirectoryStudent("stu_seeded", "seeded@student.example");
        // While active, the directory profile resolves.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/students/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/students/stu_seeded/active")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SetStudentActiveRequest(false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
        assertThat(userAccountRepository.findById("seeded@student.example").orElseThrow().isEnabled())
            .isFalse();

        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\": \"seeded@student.example\", \"password\": \"s3cretpass\"}"))
            .andExpect(status().isUnauthorized());

        // Reactivation restores both the record and the login.
        mockMvc.perform(patch("/api/students/stu_seeded/active")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SetStudentActiveRequest(true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\": \"seeded@student.example\", \"password\": \"s3cretpass\"}"))
            .andExpect(status().isOk());
    }

    // ---- CSV import ----

    @Test
    void csvImportCountsImportedAndSkippedRows() throws Exception {
        String csv = """
            rollNumber,email,name,branch,cgpa,backlogs
            CS-1,one@student.example,One Student,CSE,8.0,0
            CS-2,seeded@student.example,Dup Student,CSE,7.0,0
            CS-3,,Missing Email,CSE,7.5,0
            CS-4,four@student.example,Bad Cgpa,CSE,12.5,0
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/students/import")
                .file(file)
                .param("campusId", "campus_a")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(1))
            .andExpect(jsonPath("$.skipped").value(3))
            .andExpect(jsonPath("$.errors.length()").value(3));

        assertThat(studentRepository.findByCampusIdAndEmailIgnoreCase("campus_a", "one@student.example"))
            .isPresent();
    }

    @Test
    void csvImportStripsUtf8ByteOrderMarkFromHeader() throws Exception {
        // Excel-saved CSVs start with EF BB BF; without stripping it the first header reads as
        // "\uFEFFrollNumber" and every row fails as missing rollNumber.
        String csv = "\uFEFFrollNumber,email,name\nBOM-1,bom@student.example,Bom Student\n";
        MockMultipartFile file = new MockMultipartFile(
            "file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/students/import")
                .file(file)
                .param("campusId", "campus_a")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imported").value(1))
            .andExpect(jsonPath("$.skipped").value(0));

        assertThat(studentRepository.findByCampusIdAndEmailIgnoreCase("campus_a", "bom@student.example"))
            .isPresent();
    }

    @Test
    void studentRoleCannotImportCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "students.csv", "text/csv",
            "rollNumber,email,name\nCS-9,x@student.example,X\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/students/import")
                .file(file)
                .param("campusId", "campus_a")
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isForbidden());
    }

    // ---- Field ownership ----

    @Test
    void studentSelfEditCannotForgeDirectoryOwnedAcademics() throws Exception {
        String token = registerDirectoryStudent("stu_seeded", "seeded@student.example");

        // An old client still sends cgpa/branch/name; Jackson ignores the unknown properties and
        // the directory-owned values stay untouched. Self-editable fields do apply.
        mockMvc.perform(put("/api/students/me")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                    { "phone": "+91 99999 00000", "targetRole": "SDE",
                      "cgpa": 10, "branch": "Hacked", "backlogs": 0, "name": "Fake Name" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("+91 99999 00000"))
            .andExpect(jsonPath("$.targetRole").value("SDE"))
            .andExpect(jsonPath("$.cgpa").value(7.9))
            .andExpect(jsonPath("$.branch").value("CSE"))
            .andExpect(jsonPath("$.name").value("Seeded Student"));
    }
}