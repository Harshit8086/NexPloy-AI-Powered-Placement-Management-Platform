package com.nexploy.campuslink.risk.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.risk.client.MlRiskClient;
import com.nexploy.campuslink.risk.client.MlRiskClient.Factor;
import com.nexploy.campuslink.risk.client.MlRiskClient.RiskPrediction;
import com.nexploy.campuslink.risk.dto.RiskPredictRequestBody;
import com.nexploy.campuslink.risk.repository.RiskEntryRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the risk prediction slice. The ML client is replaced by a
 * stub so prediction runs without the Python service; the assertions focus on what Spring owns —
 * the ML result returned verbatim (snake_case contract), every prediction appended as immutable
 * history newest-first, the campus roll-up picking the latest entry per student, and the
 * validation / campus-access boundaries around both endpoints.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RiskEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RiskEntryRepository riskEntryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MlRiskClient mlRiskClient;

    @BeforeEach
    void reset() {
        riskEntryRepository.deleteAll();
        studentRepository.deleteAll();
        Mockito.when(mlRiskClient.predict(Mockito.any()))
            .thenReturn(prediction("stu_1", "NEEDS_SUPPORT", "2026-09-20T12:00:00Z"));
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void seedStudent(String id, String campusId) {
        studentRepository.save(Student.builder()
            .id(id).campusId(campusId).name("Demo").email(id + "@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("6.1")).backlogs(2).readinessScore(48).placementStatus("ACTIVE")
            .build());
    }

    private RiskPredictRequestBody signals() {
        return new RiskPredictRequestBody(2, 1, 0, 3, 1);
    }

    private RiskPrediction prediction(String studentId, String band, String generatedAt) {
        return new RiskPrediction(studentId, 0.82, band,
            List.of(new Factor("low_readiness", 0.4, "increases-risk")),
            List.of("Schedule Mock Interview", "Update Roadmap"),
            0.9, List.of(), "risk-baseline-2026-01", "student-features-1",
            OffsetDateTime.parse(generatedAt));
    }

    @Test
    void predictReturnsMlResultAndAppendsHistory() throws Exception {
        seedStudent("stu_1", "campus_a");
        Mockito.when(mlRiskClient.predict(Mockito.any()))
            .thenReturn(prediction("stu_1", "MONITOR", "2026-09-20T12:00:00Z"))
            .thenReturn(prediction("stu_1", "NEEDS_SUPPORT", "2026-09-21T12:00:00Z"));

        mockMvc.perform(post("/api/students/stu_1/risk")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(signals())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.student_id").value("stu_1"))
            .andExpect(jsonPath("$.support_probability").value(0.82))
            .andExpect(jsonPath("$.band").value("MONITOR"))
            .andExpect(jsonPath("$.recommended_interventions.length()").value(2));

        mockMvc.perform(post("/api/students/stu_1/risk")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(signals())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.band").value("NEEDS_SUPPORT"));

        // Both predictions are retained as append-only history, newest first.
        mockMvc.perform(get("/api/students/stu_1/risk/entries")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].level").value("NEEDS_SUPPORT"))
            .andExpect(jsonPath("$[0].primaryReason").value("low_readiness"))
            .andExpect(jsonPath("$[1].level").value("MONITOR"));

        org.assertj.core.api.Assertions.assertThat(riskEntryRepository.count()).isEqualTo(2);
    }

    @Test
    void predictForUnknownStudentIsNotFound() throws Exception {
        mockMvc.perform(post("/api/students/missing/risk")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(signals())))
            .andExpect(status().isNotFound());
    }

    @Test
    void negativeActivitySignalIsBadRequest() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/risk")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"applicationsSubmitted\":-1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void recruiterCannotPredictForStudentInAnotherCampus() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/risk")
                .with(authentication(as(UserRole.RECRUITER, "campus_other")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(signals())))
            .andExpect(status().isForbidden());
    }

    @Test
    void entriesForUnknownStudentIsNotFound() throws Exception {
        mockMvc.perform(get("/api/students/missing/risk/entries")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void campusRollupReturnsLatestEntryPerStudent() throws Exception {
        seedStudent("stu_1", "campus_a");
        seedStudent("stu_2", "campus_a");
        Mockito.when(mlRiskClient.predict(Mockito.any()))
            .thenReturn(prediction("stu_1", "MONITOR", "2026-09-20T09:00:00Z"))
            .thenReturn(prediction("stu_2", "LOW", "2026-09-20T10:00:00Z"))
            .thenReturn(prediction("stu_1", "NEEDS_SUPPORT", "2026-09-20T11:00:00Z"));

        for (int i = 0; i < 3; i++) {
            String studentId = i == 1 ? "stu_2" : "stu_1";
            mockMvc.perform(post("/api/students/" + studentId + "/risk")
                    .with(authentication(as(UserRole.OFFICER, "campus_a")))
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(signals())))
                .andExpect(status().isOk());
        }

        // Roll-up: one entry per student, and the latest one wins (stu_1 is NEEDS_SUPPORT, not
        // its earlier MONITOR).
        mockMvc.perform(get("/api/campuses/campus_a/risk-entries")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].studentId").value("stu_1"))
            .andExpect(jsonPath("$[0].level").value("NEEDS_SUPPORT"))
            .andExpect(jsonPath("$[1].studentId").value("stu_2"))
            .andExpect(jsonPath("$[1].level").value("LOW"));
    }
}