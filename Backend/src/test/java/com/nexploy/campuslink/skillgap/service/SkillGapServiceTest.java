package com.nexploy.campuslink.skillgap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.repository.DriveSkillRequirementRepository;
import com.nexploy.campuslink.drive.service.DriveService;
import com.nexploy.campuslink.skillgap.dto.RoleSkillGapResponse;
import com.nexploy.campuslink.skillgap.dto.RoleSkillGapResponse.SkillMatchItem;
import com.nexploy.campuslink.skillgap.dto.SkillGapRequest;
import com.nexploy.campuslink.skillgap.dto.SkillGapRequest.SkillInput;
import com.nexploy.campuslink.skillgap.dto.SkillGapResponse;
import com.nexploy.campuslink.skillgap.dto.SkillGapResponse.SkillGapItem;
import com.nexploy.campuslink.skillgap.narrative.SkillGapNarrator;
import com.nexploy.campuslink.skillgap.narrative.SkillGapNarrator.SkillNarrative;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SkillGapServiceTest {

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final StudentSkillRepository studentSkillRepository = mock(StudentSkillRepository.class);
    private final DriveSkillRequirementRepository driveSkillRequirementRepository = mock(DriveSkillRequirementRepository.class);
    private final DriveRepository driveRepository = mock(DriveRepository.class);
    private final DriveService driveService = mock(DriveService.class);
    private final com.nexploy.campuslink.application.repository.ApplicationRepository applicationRepository =
        mock(com.nexploy.campuslink.application.repository.ApplicationRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();

    private final SkillGapService service = serviceWith(null);

    @SuppressWarnings("unchecked")
    private SkillGapService serviceWith(SkillGapNarrator narrator) {
        ObjectProvider<SkillGapNarrator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(narrator);
        return new SkillGapService(
            studentRepository, campusAccessService, studentSkillRepository,
            driveRepository, driveSkillRequirementRepository, driveService,
            applicationRepository, provider
        );
    }

    @Test
    void classifiesPriorityByGapSizeAndSkipsMetRequirements() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student("campus_a")));

        SkillGapResponse response = service.analyze("stu_1", new SkillGapRequest("Backend Engineer", List.of(
            new SkillInput("AWS", 0, 60),           // gap 60 -> HIGH
            new SkillInput("Docker", 45, 70),       // gap 25 -> MEDIUM
            new SkillInput("Git", 80, 90),          // gap 10 -> LOW
            new SkillInput("Java", 85, 80)          // already meets requirement -> skipped
        )));

        assertThat(response.studentId()).isEqualTo("stu_1");
        assertThat(response.targetRole()).isEqualTo("Backend Engineer");
        assertThat(response.skillGaps()).extracting(SkillGapItem::skill)
            .containsExactly("AWS", "Docker", "Git");
        assertThat(response.skillGaps()).extracting(SkillGapItem::priority)
            .containsExactly("HIGH", "MEDIUM", "LOW");
    }

    @Test
    void missingSkillSetsNoEvidenceStatusAndNullCurrentLevelInsteadOfZero() {
        authenticate(user(UserRole.STUDENT, "campus_a"));
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student("campus_a")));
        when(driveRepository.findById("drive_fintech")).thenReturn(Optional.of(
            drive("drive_fintech", "campus_a", "Fintech Co", "Software Engineer", null)));
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc("stu_1")).thenReturn(List.of(
            storedSkill("Java", 85, "VERIFIED")));

        // Fintech Co requires Java (80) and Docker (70). Student has Java but NO Docker evidence.
        when(driveSkillRequirementRepository.findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc("drive_fintech"))
            .thenReturn(List.of(
                driveRequirement("dsr_1", "drive_fintech", "Java", 80, 3, true),
                driveRequirement("dsr_2", "drive_fintech", "Docker", 70, 2, true)
            ));

        RoleSkillGapResponse response = service.analyzeForDrive("stu_1", "drive_fintech");

        assertThat(response.roleRequirementsAvailable()).isTrue();
        assertThat(response.analysisType()).isEqualTo("LEVEL_BASED");
        assertThat(response.strengths()).hasSize(1);
        assertThat(response.strengths().get(0).skill()).isEqualTo("Java");

        assertThat(response.gaps()).hasSize(1);
        SkillGapItem dockerGap = response.gaps().get(0);
        assertThat(dockerGap.skill()).isEqualTo("Docker");
        // CRITICAL REQUIREMENT: Missing skill -> currentLevel must NOT be assumed to be 0!
        assertThat(dockerGap.currentLevel()).isNull();
        assertThat(dockerGap.evidenceStatus()).isEqualTo("NO_EVIDENCE");
        assertThat(dockerGap.currentLabel()).isEqualTo("No Evidence");
        assertThat(dockerGap.targetLevel()).isEqualTo(70);
    }

    @Test
    void studentAndDriveSpecificGapsDifferAcrossDrives() {
        authenticate(user(UserRole.STUDENT, "campus_a"));
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student("campus_a")));
        // Student knows Java 85 and React 75
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc("stu_1")).thenReturn(List.of(
            storedSkill("Java", 85, "VERIFIED"),
            storedSkill("React", 75, "VERIFIED")));

        // Drive X (Company X: Backend Java & Concurrency)
        when(driveRepository.findById("drive_x")).thenReturn(Optional.of(
            drive("drive_x", "campus_a", "Fintech Corp", "Backend Developer", null)));
        when(driveSkillRequirementRepository.findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc("drive_x"))
            .thenReturn(List.of(
                driveRequirement("req_x1", "drive_x", "Java", 80, 3, true),
                driveRequirement("req_x2", "drive_x", "Kubernetes", 60, 2, true)
            ));

        // Drive Y (Company Y: Frontend Web & TypeScript)
        when(driveRepository.findById("drive_y")).thenReturn(Optional.of(
            drive("drive_y", "campus_a", "Web Works", "Frontend Developer", null)));
        when(driveSkillRequirementRepository.findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc("drive_y"))
            .thenReturn(List.of(
                driveRequirement("req_y1", "drive_y", "React", 70, 3, true),
                driveRequirement("req_y2", "drive_y", "TypeScript", 70, 2, true)
            ));

        RoleSkillGapResponse fitX = service.analyzeForDrive("stu_1", "drive_x");
        RoleSkillGapResponse fitY = service.analyzeForDrive("stu_1", "drive_y");

        assertThat(fitX.companyName()).isEqualTo("Fintech Corp");
        assertThat(fitX.gaps()).extracting(SkillGapItem::skill).containsExactly("Kubernetes");

        assertThat(fitY.companyName()).isEqualTo("Web Works");
        assertThat(fitY.gaps()).extracting(SkillGapItem::skill).containsExactly("TypeScript");
    }

    @Test
    void requiredSkillsJsonFallbackUsesPresenceOnlyWithoutNumericGaps() {
        authenticate(user(UserRole.STUDENT, "campus_a"));
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student("campus_a")));
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc("stu_1")).thenReturn(List.of(
            storedSkill("Java", 90, "VERIFIED")));

        // No approved drive_skill_requirements, only requiredSkillsJson
        when(driveRepository.findById("drive_legacy")).thenReturn(Optional.of(
            drive("drive_legacy", "campus_a", "Startup Inc", "Full Stack", "[\"Java\",\"Python\"]")));
        when(driveSkillRequirementRepository.findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc("drive_legacy"))
            .thenReturn(List.of());
        when(driveService.readStringList("[\"Java\",\"Python\"]")).thenReturn(List.of("Java", "Python"));

        RoleSkillGapResponse response = service.analyzeForDrive("stu_1", "drive_legacy");

        assertThat(response.analysisType()).isEqualTo("PRESENCE_ONLY");
        assertThat(response.driveSkillsFallback()).isTrue();
        assertThat(response.roleRequirementsAvailable()).isFalse();
        assertThat(response.overallMatch()).isEqualTo(50); // 1 of 2 covered

        assertThat(response.strengths()).extracting(SkillMatchItem::skill).containsExactly("Java");
        assertThat(response.gaps()).hasSize(1);
        SkillGapItem pythonGap = response.gaps().get(0);
        assertThat(pythonGap.skill()).isEqualTo("Python");
        // CRITICAL REQUIREMENT: No numeric required level -> DO NOT calculate gap
        assertThat(pythonGap.currentLevel()).isNull();
        assertThat(pythonGap.targetLevel()).isNull();
        assertThat(pythonGap.evidenceStatus()).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void enforcesCampusAccessForCrossCampusStudentLookup() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(studentRepository.findById("stu_b")).thenReturn(Optional.of(student("campus_b")));

        assertThatThrownBy(() -> service.analyze("stu_b", new SkillGapRequest("Role", List.of())))
            .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private Drive drive(String id, String campusId, String company, String role, String json) {
        return Drive.builder()
            .id(id).campusId(campusId).companyName(company).role(role)
            .status(DriveStatus.ACTIVE).requiredSkillsJson(json).build();
    }

    private DriveSkillRequirement driveRequirement(
        String id, String driveId, String skill, int level, int weight, boolean mandatory
    ) {
        return DriveSkillRequirement.builder()
            .id(id).driveId(driveId).skill(skill).requiredLevel(level)
            .weight(weight).mandatory(mandatory).approved(true).createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now()).build();
    }

    private StudentSkill storedSkill(String skill, int level, String status) {
        return StudentSkill.builder()
            .id("sk_" + skill).skill(skill).proficiency(level)
            .verificationStatus(status).source("VERIFIED").build();
    }

    private Student student(String campusId) {
        return Student.builder()
            .id("stu_1").campusId(campusId).name("Test Student").email("t@example.com")
            .rollNumber("R1").branch("CSE").branchCode("CS").graduationYear(2025)
            .cgpa(new BigDecimal("8.5")).backlogs(0).readinessScore(80)
            .placementStatus("REGISTERED").build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder()
            .username("u-" + role).password("x").campusId(campusId)
            .role(role).enabled(true).build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
