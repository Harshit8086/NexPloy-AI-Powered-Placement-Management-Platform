package com.nexploy.campuslink.interview.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.interview.dto.RecordResultRequest;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewMode;
import com.nexploy.campuslink.interview.entity.InterviewResult;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * End-to-end HTTP integration test for the interview endpoints. Runs through real routing + the
 * security filter chain against in-memory H2 (the "test" profile). Covers recording a result
 * (which marks the interview COMPLETED), campus-scope enforcement on read, and not-found mapping.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class InterviewEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        interviewRepository.deleteAll();
        driveRepository.deleteAll();

        // recordResult resolves the interview's drive for the RBAC check, so it must exist.
        driveRepository.save(Drive.builder()
            .id("drive_1").campusId("campus_a").recruiterId("rec_1").companyName("TechNova").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(DriveStatus.ACTIVE).build());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Interview seed(String id, String campusId, InterviewStatus status) {
        return interviewRepository.save(Interview.builder()
            .id(id).campusId(campusId).studentId("stu_1").driveId("drive_1")
            .scheduleEventId("evt_1").mode(InterviewMode.ON_CAMPUS)
            .scheduledAt(OffsetDateTime.parse("2026-02-01T09:00:00Z"))
            .status(status).result(InterviewResult.PENDING)
            .createdAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void recordingResultMarksInterviewCompleted() throws Exception {
        seed("iv_1", "campus_a", InterviewStatus.SCHEDULED);
        RecordResultRequest request = new RecordResultRequest(InterviewResult.SELECTED, "Strong candidate");

        mockMvc.perform(post("/api/interviews/iv_1/result")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result").value("SELECTED"))
            .andExpect(jsonPath("$.feedback").value("Strong candidate"));
    }

    /** Officers are campus-scoped: they can read interviews only on their own campus. */
    @Test
    void officerCanReadInterviewInOwnCampus() throws Exception {
        seed("iv_1", "campus_a", InterviewStatus.SCHEDULED);

        mockMvc.perform(get("/api/interviews/iv_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.campusId").value("campus_a"));
    }

    @Test
    void officerCannotReadInterviewFromAnotherCampus() throws Exception {
        seed("iv_1", "campus_a", InterviewStatus.SCHEDULED);

        mockMvc.perform(get("/api/interviews/iv_1")
                .with(authentication(as(UserRole.OFFICER, "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recruiterCannotReadInterviewFromAnotherCampus() throws Exception {
        seed("iv_1", "campus_a", InterviewStatus.SCHEDULED);

        mockMvc.perform(get("/api/interviews/iv_1")
                .with(authentication(as(UserRole.RECRUITER, "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownInterviewReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/interviews/missing")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }
}
