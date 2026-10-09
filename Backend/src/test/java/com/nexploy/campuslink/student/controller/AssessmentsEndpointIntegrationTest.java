package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for GET /api/students/me/assessments. Confirms the read-only assessment
 * baseline resolves the signed-in student by email == username, exposes the authoritative stored
 * scores, defaults to all-zero when none are recorded, and is rejected when unauthenticated.
 * Runs against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AssessmentsEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";
    private static final String EMAIL_NO_SCORES = "blank@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @BeforeEach
    void seed() {
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(82)
            .assessmentsJson("{\"aptitude\":78,\"coding\":85,\"technical\":80,"
                + "\"communication\":72,\"mockInterview\":66}")
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
        studentRepository.save(Student.builder()
            .id("stu_2").campusId("campus_a").name("Blank").email(EMAIL_NO_SCORES)
            .rollNumber("R-2").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("7.0")).backlogs(0).readinessScore(50)
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void returnsStoredAssessmentScores() throws Exception {
        mockMvc.perform(get("/api/students/me/assessments").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aptitude").value(78))
            .andExpect(jsonPath("$.coding").value(85))
            .andExpect(jsonPath("$.technical").value(80))
            .andExpect(jsonPath("$.communication").value(72))
            .andExpect(jsonPath("$.mockInterview").value(66));
    }

    @Test
    void defaultsToZeroWhenNoScoresRecorded() throws Exception {
        mockMvc.perform(get("/api/students/me/assessments").with(authentication(asStudent(EMAIL_NO_SCORES))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aptitude").value(0))
            .andExpect(jsonPath("$.mockInterview").value(0));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/assessments"))
            .andExpect(status().is4xxClientError());
    }
}
