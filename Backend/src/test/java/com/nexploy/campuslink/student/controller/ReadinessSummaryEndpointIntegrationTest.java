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
 * End-to-end HTTP test for GET /api/students/me/readiness. Confirms the read-only readiness summary
 * resolves the signed-in student by email == username, exposes the authoritative stored score +
 * parsed breakdown, and is rejected when unauthenticated. Runs against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReadinessSummaryEndpointIntegrationTest {

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
            // Legacy 7-key breakdown row: the reduced 5-factor DTO must read it, silently dropping
            // the retired technical/coding keys (no DB migration needed for stored rows).
            .breakdownJson("{\"academic\":80,\"technical\":75,\"coding\":88,\"aptitude\":70,"
                + "\"communication\":65,\"projects\":90,\"interview\":60}")
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
    }

    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void returnsStoredScoreAndBreakdown() throws Exception {
        mockMvc.perform(get("/api/students/me/readiness").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(82))
            .andExpect(jsonPath("$.breakdown.academic").value(80))
            .andExpect(jsonPath("$.breakdown.projects").value(90))
            .andExpect(jsonPath("$.breakdown.interview").value(60))
            // The retired dimensions are dropped by the 5-factor DTO and must not leak into the response.
            .andExpect(jsonPath("$.breakdown.technical").doesNotExist())
            .andExpect(jsonPath("$.breakdown.coding").doesNotExist());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/readiness"))
            .andExpect(status().is4xxClientError());
    }
}
