package com.nexploy.campuslink.shared.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.document.dto.DocumentResponse;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.DocumentType;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.entity.ScanStatus;
import com.nexploy.campuslink.drive.dto.DriveResponse;
import com.nexploy.campuslink.drive.dto.DriveRound;
import com.nexploy.campuslink.drive.dto.DriveSkillRequirementResponse;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.dto.EligibilityResponse.EligibilityCheck;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.skillgap.dto.RoleSkillGapResponse;
import com.nexploy.campuslink.skillgap.dto.RoleSkillGapResponse.SkillMatchItem;
import com.nexploy.campuslink.skillgap.dto.SkillGapResponse.SkillGapItem;
import com.nexploy.campuslink.student.dto.StudentResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.service.StudentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class RestDtoContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void studentResponseSerializesCamelCase() throws Exception {
        Student student = Student.builder()
            .id("stu_1")
            .campusId("campus_1")
            .name("Aarav Patel")
            .email("aarav@test.edu")
            .department("CSE")
            .branch("Computer Science")
            .branchCode("CSE")
            .cgpa(new BigDecimal("8.50"))
            .graduationYear(2025)
            .placementStatus("APPLIED")
            .targetRole("Full Stack Developer")
            .projectsJson("[{\"name\":\"Portal\",\"stack\":[\"React\",\"Node\"],\"summary\":\"Campus portal\",\"githubUrl\":\"https://github.com/a/p\",\"liveUrl\":\"https://portal.dev\"}]")
            .experienceJson("[{\"company\":\"Acme\",\"role\":\"Intern\",\"duration\":\"3 mos\",\"summary\":\"Backend intern\"}]")
            .assessmentsJson("{\"aptitude\":80,\"coding\":85,\"technical\":75,\"communication\":70,\"mockInterview\":80}")
            .breakdownJson("{\"technical\":80,\"coding\":75,\"softSkills\":85,\"academic\":70,\"verifiedProjects\":78}")
            .build();

        StudentResponse response = new StudentService(objectMapper).toResponse(student);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"campusId\"", "\"graduationYear\"", "\"placementStatus\"",
            "\"targetRole\"", "\"projects\"", "\"experience\"");
        assertThat(json).doesNotContain("campus_id", "graduation_year", "placement_status",
            "target_role");

        StudentResponse back = objectMapper.readValue(json, StudentResponse.class);
        assertThat(back.id()).isEqualTo("stu_1");
        assertThat(back.targetRole()).isEqualTo("Full Stack Developer");
        assertThat(back.projects()).hasSize(1);
        assertThat(back.projects().get(0).githubUrl()).isEqualTo("https://github.com/a/p");
    }

    @Test
    void eligibilityResponseSerializesCamelCase() throws Exception {
        EligibilityResponse response = new EligibilityResponse(
            "stu_1",
            "drv_1",
            true,
            List.of(
                new EligibilityCheck("CGPA", true, "8.5 >= 7.0"),
                new EligibilityCheck("Graduation Year", true, "2025 matches 2025")
            ),
            List.of()
        );

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"eligible\"", "\"checks\"", "\"label\"", "\"passed\"", "\"detail\"");
        assertThat(json).doesNotContain("is_eligible");

        EligibilityResponse back = objectMapper.readValue(json, EligibilityResponse.class);
        assertThat(back.eligible()).isTrue();
        assertThat(back.checks()).hasSize(2);
    }

    @Test
    void driveResponseSerializesCamelCaseWithEnrichmentFields() throws Exception {
        Drive drive = Drive.builder()
            .id("drv_1")
            .campusId("campus_1")
            .recruiterId("rec_1")
            .companyName("Acme Corp")
            .role("SDE-1")
            .location("Bangalore")
            .packageLpa(new BigDecimal("12.50"))
            .minimumCgpa(new BigDecimal("7.50"))
            .maximumBacklogs(0)
            .graduationYear(2025)
            .deadline(LocalDate.parse("2026-11-01"))
            .status(DriveStatus.ACTIVE)
            .driveType("On-Campus")
            .description("Full-time SDE-1")
            .expectedHiring(10)
            .venue("Auditorium B")
            .minimumTenthPercentage(new BigDecimal("75.00"))
            .minimumTwelfthPercentage(new BigDecimal("75.00"))
            .build();

        List<String> skills = List.of("Java", "Spring Boot");
        List<DriveRound> rounds = List.of(
            new DriveRound("Online Assessment", "90 min DSA test"),
            new DriveRound("Technical Interview", "1 hour round")
        );
        List<String> branches = List.of("CSE", "IT");

        DriveResponse response = DriveResponse.from(drive, skills, rounds, branches);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"campusId\"", "\"recruiterId\"", "\"companyName\"",
            "\"packageLpa\"", "\"minimumCgpa\"", "\"maximumBacklogs\"", "\"graduationYear\"",
            "\"driveType\"", "\"expectedHiring\"", "\"minimumTenthPercentage\"",
            "\"minimumTwelfthPercentage\"", "\"requiredSkills\"", "\"rounds\"", "\"allowedBranches\"");
        assertThat(json).doesNotContain("campus_id", "recruiter_id", "company_name",
            "package_lpa", "minimum_cgpa", "maximum_backlogs", "graduation_year",
            "drive_type", "expected_hiring", "minimum_tenth_percentage",
            "minimum_twelfth_percentage", "required_skills", "allowed_branches");
    }

    @Test
    void roleSkillGapResponseSerializesCamelCase() throws Exception {
        RoleSkillGapResponse response = new RoleSkillGapResponse(
            "stu_1", "Backend Developer", true, true, false, true, 67,
            List.of(new SkillMatchItem("Java", 85, 85, "VERIFIED", "VERIFIED")),
            List.of(new SkillGapItem("Docker", null, 70, "No Evidence", "Advanced", "HIGH", "", "", null, "NO_EVIDENCE")),
            "drv_1", "Acme Corp", 1, 2, "PRESENCE_ONLY"
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"driveSkillsFallback\"", "\"roleRequirementsAvailable\"",
            "\"overallMatch\"", "\"verificationStatus\"", "\"hasTargetRole\"", "\"hasSkills\"",
            "\"driveId\"", "\"companyName\"", "\"analysisType\"");
        assertThat(json).doesNotContain("drive_skills_fallback", "role_requirements_available",
            "overall_match", "verification_status", "has_target_role", "drive_id", "company_name");

        RoleSkillGapResponse back = objectMapper.readValue(json, RoleSkillGapResponse.class);
        assertThat(back).isEqualTo(response);
    }

    @Test
    void driveSkillRequirementResponseSerializesCamelCase() throws Exception {
        DriveSkillRequirementResponse response = new DriveSkillRequirementResponse(
            "dsr_1", "drv_1", "SQL", 75, 3, true, "AI_EXTRACTED", 90, true,
            OffsetDateTime.parse("2026-10-06T10:00:00Z"),
            OffsetDateTime.parse("2026-10-06T10:00:00Z")
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"driveId\"", "\"requiredLevel\"", "\"updatedAt\"",
            "\"createdAt\"", "\"weight\"", "\"skill\"", "\"mandatory\"", "\"confidence\"", "\"approved\"");
        assertThat(json).doesNotContain("drive_id", "required_level", "updated_at", "created_at");

        DriveSkillRequirementResponse back = objectMapper.readValue(json, DriveSkillRequirementResponse.class);
        assertThat(back).isEqualTo(response);
    }

    @Test
    void documentResponseSerializesCamelCaseWithHasFile() throws Exception {
        DocumentResponse response = DocumentResponse.from(Document.builder()
            .id("doc_1").campusId("campus_1").studentId("stu_1").type(DocumentType.RESUME)
            .fileName("resume.pdf").contentType("application/pdf").sizeBytes(2048)
            .storageKey("key/resume.pdf").scanStatus(ScanStatus.CLEAN)
            .reviewStatus(ReviewStatus.APPROVED).reviewedBy("officer@campus_1")
            .reviewedAt(OffsetDateTime.parse("2026-10-05T10:00:00Z"))
            .uploadedAt(OffsetDateTime.parse("2026-10-05T09:00:00Z")).build(), true);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"hasFile\"", "\"scanStatus\"", "\"reviewStatus\"",
            "\"reviewedBy\"", "\"reviewedAt\"", "\"uploadedAt\"", "\"contentType\"",
            "\"sizeBytes\"", "\"studentId\"", "\"campusId\"");
        assertThat(json).doesNotContain("has_file", "scan_status", "review_status",
            "reviewed_by", "reviewed_at", "uploaded_at", "content_type", "size_bytes");

        DocumentResponse back = objectMapper.readValue(json, DocumentResponse.class);
        assertThat(back).isEqualTo(response);
    }
}
