package com.nexploy.campuslink.application.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.application.repository.ApplicationRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for POST /api/drives/{driveId}/apply (Phase 2a). Exercises the full apply
 * gate — eligibility, drive-open, duplicate — plus the email==username self-resolution, against
 * in-memory H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApplyToDriveEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @BeforeEach
    void seed() {
        applicationRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .placementStatus("ACTIVE").build());
    }

    private void saveDrive(String id, DriveStatus status, BigDecimal minCgpa, LocalDate deadline) {
        driveRepository.save(Drive.builder()
            .id(id).campusId("campus_a").companyName("TechNova").role("SDE")
            .minimumCgpa(minCgpa).maximumBacklogs(1).graduationYear(2026)
            .deadline(deadline).status(status).build());
    }

    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void appliesToEligibleOpenDrive() throws Exception {
        saveDrive("drv_1", DriveStatus.ACTIVE, new BigDecimal("7.0"), LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/drives/drv_1/apply")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"coverNote\":\"Keen to join\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.driveId").value("drv_1"))
            .andExpect(jsonPath("$.studentId").value("stu_1"))
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.coverNote").value("Keen to join"));
    }

    @Test
    void applyingWithNoBodySucceeds() throws Exception {
        saveDrive("drv_1", DriveStatus.ACTIVE, new BigDecimal("7.0"), LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/drives/drv_1/apply").with(authentication(asStudent())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void secondApplyToSameDriveIsConflict() throws Exception {
        saveDrive("drv_1", DriveStatus.ACTIVE, new BigDecimal("7.0"), LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/drives/drv_1/apply").with(authentication(asStudent())))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/drives/drv_1/apply").with(authentication(asStudent())))
            .andExpect(status().isConflict());
    }

    @Test
    void ineligibleStudentIsConflict() throws Exception {
        // Requires CGPA 9.0; student has 8.2.
        saveDrive("drv_1", DriveStatus.ACTIVE, new BigDecimal("9.0"), LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/drives/drv_1/apply").with(authentication(asStudent())))
            .andExpect(status().isConflict());
    }

    @Test
    void closedDriveIsConflict() throws Exception {
        saveDrive("drv_1", DriveStatus.CLOSED, new BigDecimal("7.0"), LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/drives/drv_1/apply").with(authentication(asStudent())))
            .andExpect(status().isConflict());
    }

    @Test
    void pastDeadlineIsConflict() throws Exception {
        saveDrive("drv_1", DriveStatus.ACTIVE, new BigDecimal("7.0"), LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/drives/drv_1/apply").with(authentication(asStudent())))
            .andExpect(status().isConflict());
    }

    @Test
    void unknownDriveIsNotFound() throws Exception {
        mockMvc.perform(post("/api/drives/drv_missing/apply").with(authentication(asStudent())))
            .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        saveDrive("drv_1", DriveStatus.ACTIVE, new BigDecimal("7.0"), LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/drives/drv_1/apply"))
            .andExpect(status().is4xxClientError());
    }
}
