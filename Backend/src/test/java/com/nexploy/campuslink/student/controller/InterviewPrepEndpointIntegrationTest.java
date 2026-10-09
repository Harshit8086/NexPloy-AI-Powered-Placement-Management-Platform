package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * End-to-end HTTP test for GET /api/students/me/interview-prep. Confirms plans are produced only for
 * drives the student has applied to, that technical-topic coverage and the overall preparation score
 * come from the student's recorded skill proficiencies against the drive's required skills, and that
 * an unauthenticated request is rejected. Runs on H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class InterviewPrepEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentSkillRepository studentSkillRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @BeforeEach
    void seed() {
        applicationRepository.deleteAll();
        studentSkillRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(82).placementStatus("ACTIVE")
            .build());

        studentSkillRepository.save(StudentSkill.builder()
            .id("sk_1").studentId("stu_1").campusId("campus_a").skill("Java")
            .proficiency(80).source("SELF_REPORTED").updatedAt(OffsetDateTime.now()).build());

        driveRepository.save(Drive.builder()
            .id("drive_applied").campusId("campus_a").recruiterId("rec_1").companyName("Acme")
            .role("Backend Developer").location("Remote").packageLpa(new BigDecimal("14.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026)
            .deadline(LocalDate.parse("2026-10-01")).status(DriveStatus.ACTIVE)
            .requiredSkillsJson("[\"Java\",\"Docker\"]").build());

        // A drive the student did NOT apply to — must not produce a plan.
        driveRepository.save(Drive.builder()
            .id("drive_notapplied").campusId("campus_a").recruiterId("rec_1").companyName("Beta")
            .role("Data Analyst").location("Onsite").packageLpa(new BigDecimal("10.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026)
            .deadline(LocalDate.parse("2026-10-01")).status(DriveStatus.ACTIVE)
            .requiredSkillsJson("[\"Python\"]").build());

        applicationRepository.save(Application.builder()
            .id("app_1").studentId("stu_1").driveId("drive_applied")
            .appliedOn(LocalDate.parse("2026-09-01")).status(ApplicationStatus.APPLIED).build());
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void producesPlanOnlyForAppliedDrivesWithRealCoverage() throws Exception {
        mockMvc.perform(get("/api/students/me/interview-prep")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            // Only the applied drive yields a plan.
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].driveId").value("drive_applied"))
            // Coverage from recorded proficiency: Java 80, Docker 0 → preparationScore round((80+0)/2)=40.
            .andExpect(jsonPath("$[0].preparationScore").value(40))
            .andExpect(jsonPath("$[0].technicalTopics.length()").value(2))
            .andExpect(jsonPath("$[0].behaviouralTopics.length()").value(4))
            .andExpect(jsonPath("$[0].likelyQuestions.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void emptyWhenStudentHasNoApplications() throws Exception {
        applicationRepository.deleteAll();
        mockMvc.perform(get("/api/students/me/interview-prep")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/interview-prep"))
            .andExpect(status().is4xxClientError());
    }
}
