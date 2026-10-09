package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentGoalRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for the self-service applied-roadmap endpoint (Phase 3b). Exercises real
 * routing, validation, the security chain, and the email==username self-resolution: a student can
 * only ever read or apply their own roadmap, and re-applying upserts in place. Runs against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StudentGoalEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentGoalRepository studentGoalRepository;

    @BeforeEach
    void seed() {
        studentGoalRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
    }

    /** Auth principal whose username equals the student's email, as STUDENT accounts are. */
    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void getBeforeApplyReturnsNoContent() throws Exception {
        mockMvc.perform(get("/api/students/me/goals").with(authentication(asStudent())))
            .andExpect(status().isNoContent());
    }

    @Test
    void appliesThenGetsRoadmap() throws Exception {
        mockMvc.perform(put("/api/students/me/goals")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factors\":[\"aws\",\"dsa\"],\"projectedProbability\":76,"
                    + "\"baselineProbability\":68,\"readinessImpact\":5,"
                    + "\"recommendedPath\":\"Add DSA next.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.factors.length()").value(2))
            .andExpect(jsonPath("$.projectedProbability").value(76))
            .andExpect(jsonPath("$.targetRole").value("Backend Developer"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/students/me/goals").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.factors.length()").value(2))
            .andExpect(jsonPath("$.projectedProbability").value(76));
    }

    @Test
    void reApplyingReplacesInPlace() throws Exception {
        mockMvc.perform(put("/api/students/me/goals")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factors\":[\"aws\"],\"projectedProbability\":72}"))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/students/me/goals")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factors\":[\"aws\",\"dsa\",\"mockInterviews\"],\"projectedProbability\":81}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.factors.length()").value(3))
            .andExpect(jsonPath("$.projectedProbability").value(81));

        mockMvc.perform(get("/api/students/me/goals").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.factors.length()").value(3));
    }

    @Test
    void emptyFactorsAreRejected() throws Exception {
        mockMvc.perform(put("/api/students/me/goals")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factors\":[],\"projectedProbability\":68}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/goals"))
            .andExpect(status().is4xxClientError());
    }
}
