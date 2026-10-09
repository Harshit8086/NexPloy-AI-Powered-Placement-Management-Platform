package com.nexploy.campuslink.drive.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * End-to-end HTTP integration test for the eligibility endpoint, the one live-wired frontend
 * contract. It runs through real request routing, the security filter chain, campus-scope
 * enforcement, and JSON serialization — the eligibility flow is deterministic and DB-only (no ML
 * call), so it is fully exercisable here against in-memory H2 without a running Postgres or worker.
 *
 * <p>Covers: the JSON body shape the frontend maps (camelCase, checks/failureReasons), dual
 * campus-scope on both the drive and the student, not-found mapping, and the unauthenticated path.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EligibilityEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DriveRepository driveRepository;

    @BeforeEach
    void seed() {
        studentRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("d@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(2).readinessScore(80)
            .placementStatus("ACTIVE").build());
        driveRepository.save(Drive.builder()
            .id("drive_1").campusId("campus_a").recruiterId("rec_1").companyName("Acme")
            .role("SDE").location("Remote").packageLpa(new BigDecimal("12.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026)
            .deadline(LocalDate.parse("2026-10-01")).status(DriveStatus.ACTIVE).build());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void returnsEligibilityJsonInTheFrontendContractShape() throws Exception {
        // Student has 2 backlogs, drive allows 0 -> ineligible, one failure reason.
        mockMvc.perform(get("/api/drives/drive_1/eligibility/stu_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.studentId").value("stu_1"))
            .andExpect(jsonPath("$.driveId").value("drive_1"))
            .andExpect(jsonPath("$.eligible").value(false))
            .andExpect(jsonPath("$.checks[0].label").value("CGPA"))
            .andExpect(jsonPath("$.checks[0].passed").value(true))
            .andExpect(jsonPath("$.checks[1].label").value("Backlogs"))
            .andExpect(jsonPath("$.checks[1].passed").value(false))
            .andExpect(jsonPath("$.failureReasons").isArray())
            .andExpect(jsonPath("$.failureReasons[0]").value("2 <= 0"));
    }

    @Test
    void recruiterOutsideCampusIsForbidden() throws Exception {
        mockMvc.perform(get("/api/drives/drive_1/eligibility/stu_1")
                .with(authentication(as(UserRole.RECRUITER, "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recruiterInSameCampusIsAllowed() throws Exception {
        mockMvc.perform(get("/api/drives/drive_1/eligibility/stu_1")
                .with(authentication(as(UserRole.RECRUITER, "campus_a"))))
            .andExpect(status().isOk());
    }

    @Test
    void unknownDriveReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/drives/missing/eligibility/stu_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/drives/drive_1/eligibility/stu_1"))
            .andExpect(status().is4xxClientError());
    }
}
