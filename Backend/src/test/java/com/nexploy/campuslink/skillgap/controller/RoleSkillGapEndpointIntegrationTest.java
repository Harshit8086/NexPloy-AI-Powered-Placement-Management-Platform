package com.nexploy.campuslink.skillgap.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.repository.DriveSkillRequirementRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
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
 * HTTP integration test for the drive-requirements-driven GET skill-gap endpoints.
 * Verifies level-based analysis, NO_EVIDENCE handling, presence-only fallback, and security.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RoleSkillGapEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentSkillRepository studentSkillRepository;

    @Autowired
    private DriveSkillRequirementRepository requirementRepository;

    @Autowired
    private DriveRepository driveRepository;

    @BeforeEach
    void seed() {
        studentSkillRepository.deleteAll();
        studentRepository.deleteAll();
        requirementRepository.deleteAll();
        driveRepository.deleteAll();
    }

    private Drive drive(String id, String campusId, String company, String role, String json) {
        return driveRepository.save(Drive.builder()
            .id(id).campusId(campusId).companyName(company).role(role)
            .location("Remote").status(DriveStatus.ACTIVE).driveType("On-Campus")
            .requiredSkillsJson(json).build());
    }

    private DriveSkillRequirement req(String id, String driveId, String skill, int level, int weight) {
        return requirementRepository.save(DriveSkillRequirement.builder()
            .id(id).driveId(driveId).skill(skill).requiredLevel(level).weight(weight)
            .mandatory(true).approved(true).createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now()).build());
    }

    private Student student(String id, String campusId, String targetRole) {
        return studentRepository.save(Student.builder()
            .id(id).campusId(campusId).name("Demo").email("d@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .targetRole(targetRole).placementStatus("ACTIVE").build());
    }

    private void skill(String id, String studentId, String campusId, String name, int level, String verificationStatus) {
        studentSkillRepository.save(StudentSkill.builder()
            .id(id).studentId(studentId).campusId(campusId).skill(name)
            .proficiency(level).source("SELF_REPORTED").verificationStatus(verificationStatus)
            .updatedAt(OffsetDateTime.now()).build());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void resolvesDriveRequirementsAndComputesWeightedMatch() throws Exception {
        Drive d = drive("drv_1", "campus_a", "Acme Corp", "Backend Developer", null);
        req("r_java", "drv_1", "Java", 80, 2);
        req("r_aws", "drv_1", "AWS", 60, 1);

        student("stu_1", "campus_a", "Backend Developer");
        skill("sk_java", "stu_1", "campus_a", "Java", 85, "VERIFIED");

        mockMvc.perform(get("/api/students/stu_1/skill-gap/drives/drv_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.studentId").value("stu_1"))
            .andExpect(jsonPath("$.targetRole").value("Backend Developer"))
            .andExpect(jsonPath("$.roleRequirementsAvailable").value(true))
            .andExpect(jsonPath("$.driveSkillsFallback").value(false))
            .andExpect(jsonPath("$.overallMatch").value(67))
            .andExpect(jsonPath("$.strengths.length()").value(1))
            .andExpect(jsonPath("$.strengths[0].skill").value("Java"))
            .andExpect(jsonPath("$.strengths[0].currentLevel").value(85))
            .andExpect(jsonPath("$.gaps.length()").value(1))
            .andExpect(jsonPath("$.gaps[0].skill").value("AWS"))
            .andExpect(jsonPath("$.gaps[0].evidenceStatus").value("NO_EVIDENCE"))
            .andExpect(jsonPath("$.gaps[0].currentLevel").doesNotExist());
    }

    @Test
    void driveFitFallsBackToPresenceOnlyWhenNoApprovedRequirements() throws Exception {
        drive("drv_2", "campus_a", "Cloud Services", "Cloud Engineer", "[\"AWS\",\"Docker\"]");
        student("stu_2", "campus_a", "Cloud Engineer");
        skill("sk_aws", "stu_2", "campus_a", "AWS", 70, "VERIFIED");

        mockMvc.perform(get("/api/students/stu_2/skill-gap/drives/drv_2")
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roleRequirementsAvailable").value(false))
            .andExpect(jsonPath("$.driveSkillsFallback").value(true))
            .andExpect(jsonPath("$.analysisType").value("PRESENCE_ONLY"))
            .andExpect(jsonPath("$.overallMatch").value(50))
            .andExpect(jsonPath("$.strengths[0].skill").value("AWS"))
            .andExpect(jsonPath("$.gaps[0].skill").value("Docker"))
            .andExpect(jsonPath("$.gaps[0].evidenceStatus").value("NO_EVIDENCE"));
    }

    @Test
    void enforcesCrossCampusAccess() throws Exception {
        drive("drv_3", "campus_b", "Other Corp", "Dev", null);
        student("stu_3", "campus_b", "Dev");

        mockMvc.perform(get("/api/students/stu_3/skill-gap/drives/drv_3")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isForbidden());
    }
}
