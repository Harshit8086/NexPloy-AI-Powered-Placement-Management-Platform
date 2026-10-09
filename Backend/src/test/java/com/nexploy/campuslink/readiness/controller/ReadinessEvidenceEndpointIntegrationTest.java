package com.nexploy.campuslink.readiness.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.readiness.client.MlReadinessClient;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScore;
import com.nexploy.campuslink.readiness.dto.ReadinessScoreRequestBody;
import com.nexploy.campuslink.readiness.dto.RecordSkillEvidenceRequest;
import com.nexploy.campuslink.readiness.repository.ReadinessSnapshotRepository;
import com.nexploy.campuslink.readiness.repository.SkillEvidenceRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the readiness evidence/snapshot slice (DESIGN.md section 2:
 * raw evidence stored separately from derived scores, never overwritten by a model result). The
 * ML client is replaced by a stub so scoring runs without the Python service; the assertions focus
 * on the two-table separation — recording raw evidence, scoring persisting an append-only derived
 * snapshot that references the evidence, and the raw evidence surviving repeated scorings unchanged.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReadinessEvidenceEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SkillEvidenceRepository skillEvidenceRepository;

    @Autowired
    private ReadinessSnapshotRepository readinessSnapshotRepository;

    @Autowired
    private com.nexploy.campuslink.assessment.repository.AssessmentRecordRepository assessmentRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MlReadinessClient mlReadinessClient;

    @BeforeEach
    void reset() {
        readinessSnapshotRepository.deleteAll();
        skillEvidenceRepository.deleteAll();
        assessmentRecordRepository.deleteAll();
        studentRepository.deleteAll();
        Mockito.when(mlReadinessClient.score(Mockito.any())).thenReturn(new ReadinessScore(
            "stu_1", 78.4, "READY", Map.of("academic", 82.0), List.of(), 0.9,
            List.of(), "readiness-baseline-2026-01", "student-features-1",
            OffsetDateTime.parse("2026-09-20T12:00:00Z")));
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void seedStudent(String id, String campusId) {
        studentRepository.save(Student.builder()
            .id(id).campusId(campusId).name("Demo").email(id + "@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80).placementStatus("ACTIVE")
            .build());
    }

    private RecordSkillEvidenceRequest evidence(String skill, int level) {
        return new RecordSkillEvidenceRequest(skill, "ASSESSMENT", level, "Scored in mock test");
    }

    @Test
    void recordedEvidenceIsReadBackVerbatim() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/readiness/evidence")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(evidence("java", 65))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.skill").value("java"))
            .andExpect(jsonPath("$.level").value(65))
            .andExpect(jsonPath("$.recordedBy").value("u-OFFICER-campus_a"));

        mockMvc.perform(get("/api/students/stu_1/readiness/evidence")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].skill").value("java"))
            .andExpect(jsonPath("$[0].level").value(65));
    }

    @Test
    void scoringPersistsAnAppendOnlySnapshotReferencingRawEvidence() throws Exception {
        seedStudent("stu_1", "campus_a");
        // Two raw evidence rows on record before scoring.
        mockMvc.perform(post("/api/students/stu_1/readiness/evidence")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json").content(objectMapper.writeValueAsString(evidence("java", 65))))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/students/stu_1/readiness/evidence")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json").content(objectMapper.writeValueAsString(evidence("sql", 70))))
            .andExpect(status().isCreated());

        ReadinessScoreRequestBody body = new ReadinessScoreRequestBody(
            80.0, 79.0, 69.0, 80.0, 72.0);

        // Score twice -> two snapshots (append-only history), evidence untouched.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/students/stu_1/readiness")
                    .with(authentication(as(UserRole.OFFICER, "campus_a")))
                    .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("READY"));
        }

        mockMvc.perform(get("/api/students/stu_1/readiness/snapshots")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].score").value(78.4))
            .andExpect(jsonPath("$[0].evidenceIds.length()").value(2))
            .andExpect(jsonPath("$[0].calculationVersion").value("policy-2026-01"));

        // Raw evidence survived both scorings unchanged.
        org.assertj.core.api.Assertions.assertThat(skillEvidenceRepository.count()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(readinessSnapshotRepository.count()).isEqualTo(2);
    }

    @Test
    void recomputeDerivesFromExistingDataAndWritesBackToDisplayedColumns() throws Exception {
        seedStudent("stu_1", "campus_a");
        // A real assessment record on file — the derivation reads assessment_records, not the body.
        assessmentRecordRepository.save(com.nexploy.campuslink.assessment.entity.AssessmentRecord.builder()
            .id("asr_1").studentId("stu_1").campusId("campus_a")
            .assessmentType(com.nexploy.campuslink.assessment.entity.AssessmentType.APTITUDE)
            .assessmentName("Aptitude-1").rawScore(new BigDecimal("82")).maxScore(new BigDecimal("100"))
            .normalizedScore(82).assessmentDate(java.time.LocalDate.parse("2026-02-01")).source("TEST")
            .createdAt(OffsetDateTime.parse("2026-02-01T00:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-02-01T00:00:00Z")).build());

        mockMvc.perform(post("/api/students/stu_1/readiness/recompute")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("READY"))
            .andExpect(jsonPath("$.score").value(78.4));

        // Write-back: the column the UI reads now reflects the recomputed score (rounded), and a
        // snapshot was appended as history.
        Student refreshed = studentRepository.findById("stu_1").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(refreshed.getReadinessScore()).isEqualTo(78);
        org.assertj.core.api.Assertions.assertThat(refreshed.getBreakdownJson()).contains("academic");
        org.assertj.core.api.Assertions.assertThat(readinessSnapshotRepository.count()).isEqualTo(1);
    }

    @Test
    void recruiterCannotRecordEvidenceInAnotherCampus() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/readiness/evidence")
                .with(authentication(as(UserRole.RECRUITER, "campus_other")))
                .contentType("application/json").content(objectMapper.writeValueAsString(evidence("java", 65))))
            .andExpect(status().isForbidden());
    }

    @Test
    void evidenceForUnknownStudentIsNotFound() throws Exception {
        mockMvc.perform(get("/api/students/missing/readiness/evidence")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidEvidenceLevelIsBadRequest() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/readiness/evidence")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"skill\":\"java\",\"source\":\"ASSESSMENT\",\"level\":150}"))
            .andExpect(status().isBadRequest());
    }
}
