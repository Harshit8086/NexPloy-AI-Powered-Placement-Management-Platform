package com.nexploy.campuslink.recruiter.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.recruiter.dto.RecruiterUpdateRequest;
import com.nexploy.campuslink.recruiter.dto.SetRecruiterActiveRequest;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.student.dto.ChangePlacementStatusRequest;
import com.nexploy.campuslink.student.dto.StudentUpdateRequest;
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
 * HTTP integration tests for officer/admin management of recruiter and student records:
 * PUT /api/recruiters/{id} + PATCH /api/recruiters/{id}/active, and PUT /api/students/{id} +
 * PATCH /api/students/{id}/placement-status. All four are officer/admin-only; other roles are
 * forbidden. Placement status is validated against the shared vocabulary (unknown -> 400).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RecruiterStudentAdminEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        recruiterRepository.deleteAll();
        studentRepository.deleteAll();

        recruiterRepository.save(Recruiter.builder()
            .id("rec_1").campusId("campus_a").name("Ann Recruiter").companyName("TechNova")
            .email("rec@a.example").active(true).build());
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("student@campus.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).placementStatus("UNPLACED").build());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private StudentUpdateRequest studentPatch(String targetRole) {
        return new StudentUpdateRequest(
            null, targetRole, null, null, null, null, null, null, null, null);
    }

    private RecruiterUpdateRequest recruiterPatch(String companyName) {
        return new RecruiterUpdateRequest(
            null, null, null, null, companyName, null, null, null, null, null, null, null, null);
    }

    @Test
    void officerUpdatesRecruiter() throws Exception {
        mockMvc.perform(put("/api/recruiters/rec_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(recruiterPatch("TechNova Labs"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.companyName").value("TechNova Labs"));
    }

    @Test
    void officerDeactivatesRecruiter() throws Exception {
        mockMvc.perform(patch("/api/recruiters/rec_1/active")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SetRecruiterActiveRequest(false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void studentCannotUpdateRecruiter() throws Exception {
        mockMvc.perform(put("/api/recruiters/rec_1")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(recruiterPatch("TechNova Labs"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerUpdatesStudent() throws Exception {
        // Student self-editable fields only (V60): academics/branch are directory-owned now.
        mockMvc.perform(put("/api/students/stu_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(studentPatch("SDE"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetRole").value("SDE"));
    }

    @Test
    void officerChangesPlacementStatus() throws Exception {
        mockMvc.perform(patch("/api/students/stu_1/placement-status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangePlacementStatusRequest("PLACED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.placementStatus").value("PLACED"));
    }

    @Test
    void invalidPlacementStatusIsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/students/stu_1/placement-status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangePlacementStatusRequest("GRADUATED"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotChangePlacementStatus() throws Exception {
        mockMvc.perform(patch("/api/students/stu_1/placement-status")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangePlacementStatusRequest("PLACED"))))
            .andExpect(status().isForbidden());
    }
}
