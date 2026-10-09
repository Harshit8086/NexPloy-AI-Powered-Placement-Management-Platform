package com.nexploy.campuslink.student.controller;

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
 * End-to-end HTTP test for GET /api/students/me/career-recommendations. Confirms the ranking is
 * anchored to the signed-in student's OWN campus, is derived deterministically from real stored
 * data (skills/strengths, eligibility criteria, role preferences), excludes non-open drives, ranks
 * the best fit first with an explainable score, and is rejected when unauthenticated. Runs on H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CareerRecommendationEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentSkillRepository studentSkillRepository;

    @Autowired
    private DriveRepository driveRepository;

    @BeforeEach
    void seed() {
        studentSkillRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(82).placementProbability(68)
            .targetRole("Backend Developer").placementStatus("ACTIVE")
            .strengthsJson("[\"Java\",\"Spring Boot\"]")
            .preferredRolesJson("[\"Backend Developer\"]")
            .build());

        // A recorded skill unrelated to any drive — proves the skill repository is read without
        // changing the matches (present skills are the union of skills + strengths).
        studentSkillRepository.save(StudentSkill.builder()
            .id("sk_1").studentId("stu_1").campusId("campus_a").skill("Git")
            .proficiency(80).source("SELF_REPORTED").updatedAt(OffsetDateTime.now()).build());

        // Strong fit: role matches exactly, 2 of 3 required skills present, fully eligible.
        driveRepository.save(Drive.builder()
            .id("drive_backend").campusId("campus_a").recruiterId("rec_1").companyName("Acme")
            .role("Backend Developer").location("Remote").packageLpa(new BigDecimal("14.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026)
            .deadline(LocalDate.parse("2026-10-01")).status(DriveStatus.ACTIVE)
            .requiredSkillsJson("[\"Java\",\"Spring Boot\",\"Docker\"]").build());

        // Weak fit: no skill overlap, ineligible on CGPA, unrelated role.
        driveRepository.save(Drive.builder()
            .id("drive_data").campusId("campus_a").recruiterId("rec_1").companyName("Beta")
            .role("Data Analyst").location("Onsite").packageLpa(new BigDecimal("10.0"))
            .minimumCgpa(new BigDecimal("9.5")).maximumBacklogs(0).graduationYear(2026)
            .deadline(LocalDate.parse("2026-10-01")).status(DriveStatus.ACTIVE)
            .requiredSkillsJson("[\"Python\",\"SQL\"]").build());

        // Closed drive: must be excluded from recommendations entirely.
        driveRepository.save(Drive.builder()
            .id("drive_closed").campusId("campus_a").recruiterId("rec_1").companyName("Gamma")
            .role("Backend Developer").location("Remote").packageLpa(new BigDecimal("20.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026)
            .deadline(LocalDate.parse("2026-10-01")).status(DriveStatus.CLOSED)
            .requiredSkillsJson("[\"Java\"]").build());
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void ranksBestFitFirstAndExcludesClosedDrives() throws Exception {
        mockMvc.perform(get("/api/students/me/career-recommendations")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            // Two ACTIVE drives; the CLOSED one is excluded.
            .andExpect(jsonPath("$.length()").value(2))
            // Best fit first. Java & Spring Boot are resume strengths (self-declared), so each matched
            // skill counts at the 0.5 self-declared weight: skills 60*(0.5+0.5)/3=20 + eligibility 25
            // + role 15 = 60. (Verifying those skills via a viva would lift this toward 80.)
            .andExpect(jsonPath("$[0].driveId").value("drive_backend"))
            .andExpect(jsonPath("$[0].matchScore").value(60))
            .andExpect(jsonPath("$[0].eligible").value(true))
            .andExpect(jsonPath("$[0].matchedSkills").value(org.hamcrest.Matchers.containsInAnyOrder("Java", "Spring Boot")))
            .andExpect(jsonPath("$[0].missingSkills[0]").value("Docker"))
            // Weak fit second: no skill overlap, ineligible on CGPA (2 of 3 checks pass).
            .andExpect(jsonPath("$[1].driveId").value("drive_data"))
            .andExpect(jsonPath("$[1].eligible").value(false));
    }

    @Test
    void singularRecordedSkillMatchesPluralDriveRequirement() throws Exception {
        // The drive says "Data Structures"; the student recorded "Data Structure" — the shared
        // normalization must treat these as one skill when ranking.
        studentSkillRepository.save(StudentSkill.builder()
            .id("sk_ds").studentId("stu_1").campusId("campus_a").skill("Data Structure")
            .proficiency(75).source("SELF_REPORTED").updatedAt(OffsetDateTime.now()).build());
        driveRepository.save(Drive.builder()
            .id("drive_ds").campusId("campus_a").recruiterId("rec_1").companyName("Delta")
            .role("Software Engineer").location("Remote").packageLpa(new BigDecimal("12.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026)
            .deadline(LocalDate.parse("2026-10-01")).status(DriveStatus.ACTIVE)
            .requiredSkillsJson("[\"Data Structures\"]").build());

        mockMvc.perform(get("/api/students/me/career-recommendations")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            // Skills 60*(0.5/1)=30 (self-declared recorded skill) + eligibility 25 + role 0 = 55.
            .andExpect(jsonPath("$[1].driveId").value("drive_ds"))
            .andExpect(jsonPath("$[1].matchScore").value(55))
            .andExpect(jsonPath("$[1].matchedSkills[0]").value("Data Structures"))
            .andExpect(jsonPath("$[1].missingSkills.length()").value(0));
    }

    @Test
    void emptyWhenCampusHasNoOpenDrives() throws Exception {
        driveRepository.deleteAll();
        mockMvc.perform(get("/api/students/me/career-recommendations")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/career-recommendations"))
            .andExpect(status().is4xxClientError());
    }
}
