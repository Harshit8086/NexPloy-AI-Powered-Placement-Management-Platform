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
 * End-to-end HTTP test for GET /api/students/me/digital-twin. Confirms the read-only digital-twin
 * summary resolves the signed-in student by email == username, echoes the authoritative stored
 * readiness score as finalScore with a deterministic label, and is rejected when unauthenticated.
 * Runs against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DigitalTwinEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

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
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
    }

    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void returnsStoredScoreAndDerivedLabel() throws Exception {
        mockMvc.perform(get("/api/students/me/digital-twin").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finalScore").value(82))
            .andExpect(jsonPath("$.label").value("PLACEMENT DIGITAL TWIN READY — 82/100"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/digital-twin"))
            .andExpect(status().is4xxClientError());
    }
}
