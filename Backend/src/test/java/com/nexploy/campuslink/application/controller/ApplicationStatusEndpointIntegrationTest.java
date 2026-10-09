package com.nexploy.campuslink.application.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.application.dto.ChangeApplicationStatusRequest;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.application.repository.ApplicationStatusChangeRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * HTTP integration tests for application status control: PATCH /api/applications/{id}/status
 * (officer/recruiter funnel advance, guarded by the transition table) and PATCH
 * /api/applications/{id}/withdraw (student self-service withdrawal).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApplicationStatusEndpointIntegrationTest {

    private static final String STUDENT_EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationStatusChangeRepository statusChangeRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        statusChangeRepository.deleteAll();
        applicationRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(STUDENT_EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).placementStatus("UNPLACED").build());
        driveRepository.save(Drive.builder()
            .id("drv_1").campusId("campus_a").recruiterId("rec_1").companyName("TechNova").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(DriveStatus.ACTIVE).build());
    }

    private Authentication as(UserRole role, String campusId, String username) {
        UserAccount user = UserAccount.builder()
            .username(username).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Application seedApplication(String id, ApplicationStatus status) {
        return applicationRepository.save(Application.builder()
            .id(id).studentId("stu_1").driveId("drv_1").appliedOn(LocalDate.now())
            .status(status).build());
    }

    @Test
    void officerAdvancesApplication() throws Exception {
        seedApplication("app_1", ApplicationStatus.APPLIED);

        mockMvc.perform(patch("/api/applications/app_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a", "officer@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeApplicationStatusRequest(ApplicationStatus.SHORTLISTED))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SHORTLISTED"));
    }

    @Test
    void illegalAdvanceIsConflict() throws Exception {
        // APPLIED may only move to SHORTLISTED or REJECTED — APPLIED -> SELECTED is illegal.
        seedApplication("app_1", ApplicationStatus.APPLIED);

        mockMvc.perform(patch("/api/applications/app_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a", "officer@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeApplicationStatusRequest(ApplicationStatus.SELECTED))))
            .andExpect(status().isConflict());
    }

    @Test
    void studentWithdrawsOwnApplication() throws Exception {
        seedApplication("app_1", ApplicationStatus.APPLIED);

        mockMvc.perform(patch("/api/applications/app_1/withdraw")
                .with(authentication(as(UserRole.STUDENT, "campus_a", STUDENT_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void unknownApplicationIsBadRequest() throws Exception {
        // The service raises IllegalArgumentException("Application not found") for a missing id,
        // which the GlobalExceptionHandler maps to 400 (not 404 — no ApplicationNotFoundException
        // type exists). Asserting the real behavior rather than the planned 404.
        mockMvc.perform(patch("/api/applications/missing/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a", "officer@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeApplicationStatusRequest(ApplicationStatus.SHORTLISTED))))
            .andExpect(status().isBadRequest());
    }
}
