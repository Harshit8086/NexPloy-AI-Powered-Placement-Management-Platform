package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for POST /api/students/me/what-if. Confirms the projection is anchored to
 * the signed-in student's OWN stored placement probability (resolved by email == username), is
 * deterministic and order-independent, reproduces the exact scenario table, caps improvement at the
 * best-case delta, and is rejected when unauthenticated. Runs against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WhatIfEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @BeforeEach
    void seed() {
        studentRepository.deleteAll();
        // Baseline 68 matches the reference the scenario table was authored against.
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(82).placementProbability(68)
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private org.springframework.test.web.servlet.ResultActions perform(String factorsJson, String email)
            throws Exception {
        return mockMvc.perform(post("/api/students/me/what-if")
            .with(authentication(asStudent(email)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"factors\":" + factorsJson + "}"));
    }

    @Test
    void emptySelectionReturnsBaseline() throws Exception {
        perform("[]", EMAIL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baselineProbability").value(68))
            .andExpect(jsonPath("$.probability").value(68))
            .andExpect(jsonPath("$.readinessImpact").value(0));
    }

    @Test
    void exactTableScenarioIsReproducedAndOrderIndependent() throws Exception {
        // aws,dsa,mockInterviews -> 81; sent in a different order to prove order-independence.
        perform("[\"mockInterviews\",\"aws\",\"dsa\"]", EMAIL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.probability").value(81))
            .andExpect(jsonPath("$.readinessImpact").value(8));
    }

    @Test
    void fullSelectionIsCappedAtBestCase() throws Exception {
        perform("[\"aws\",\"docker\",\"dsa\",\"mockInterviews\",\"newProject\",\"communication\"]", EMAIL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.probability").value(86))
            .andExpect(jsonPath("$.recommendedPath").value(
                "You've built the best-case roadmap. Apply it to lock in these gains."));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/students/me/what-if")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factors\":[]}"))
            .andExpect(status().is4xxClientError());
    }
}
