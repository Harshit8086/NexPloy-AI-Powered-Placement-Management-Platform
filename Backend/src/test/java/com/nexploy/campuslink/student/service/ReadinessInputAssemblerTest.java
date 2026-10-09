package com.nexploy.campuslink.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.assessment.entity.AssessmentRecord;
import com.nexploy.campuslink.assessment.entity.AssessmentType;
import com.nexploy.campuslink.assessment.repository.AssessmentRecordRepository;
import com.nexploy.campuslink.readiness.dto.ReadinessScoreRequestBody;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentProjectEntry;
import com.nexploy.campuslink.student.repository.StudentProjectEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the readiness input derivation. The central guarantee: a dimension the student
 * has no data for is sent as {@code null} (excluded + renormalised by the ML service), never a
 * fabricated zero — so a partial profile is not silently penalised.
 */
class ReadinessInputAssemblerTest {

    private final AssessmentRecordRepository assessmentRepository =
        Mockito.mock(AssessmentRecordRepository.class);
    private final StudentProjectEntryRepository projectRepository =
        Mockito.mock(StudentProjectEntryRepository.class);
    private final ReadinessInputAssembler assembler =
        new ReadinessInputAssembler(assessmentRepository, projectRepository, new ObjectMapper());

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(assembler, "cgpaWeight", 0.6);
        ReflectionTestUtils.setField(assembler, "tenthWeight", 0.2);
        ReflectionTestUtils.setField(assembler, "twelfthWeight", 0.2);
        ReflectionTestUtils.setField(assembler, "backlogPenalty", 3.0);
        ReflectionTestUtils.setField(assembler, "projectBase", 20);
        ReflectionTestUtils.setField(assembler, "summaryBonus", 10);
        ReflectionTestUtils.setField(assembler, "stackPoint", 4);
        ReflectionTestUtils.setField(assembler, "maxStack", 5);
    }

    @Test
    void absentAssessmentTypesAreNullNotZero() {
        when(assessmentRepository.findByStudentId("stu_1")).thenReturn(List.of(
            record(AssessmentType.APTITUDE, 80, LocalDate.parse("2026-01-10"))));
        when(projectRepository.findByStudentIdOrderBySortOrder("stu_1")).thenReturn(List.of());

        ReadinessScoreRequestBody body = assembler.assemble(studentWithAcademics(null, null, null, null));

        assertThat(body.aptitude()).isEqualTo(80.0);
        // Every type with no record stays null so the ML service excludes it, never scores it 0.
        assertThat(body.academic()).isNull();
        assertThat(body.communication()).isNull();
        assertThat(body.projects()).isNull();
        assertThat(body.interview()).isNull();
    }

    @Test
    void latestRecordPerTypeWins() {
        when(assessmentRepository.findByStudentId("stu_1")).thenReturn(List.of(
            record(AssessmentType.APTITUDE, 55, LocalDate.parse("2026-01-01")),
            record(AssessmentType.APTITUDE, 90, LocalDate.parse("2026-03-01"))));
        when(projectRepository.findByStudentIdOrderBySortOrder("stu_1")).thenReturn(List.of());

        ReadinessScoreRequestBody body = assembler.assemble(studentWithAcademics(null, null, null, null));

        assertThat(body.aptitude()).isEqualTo(90.0);
    }

    @Test
    void mockInterviewMapsToInterviewDimension() {
        when(assessmentRepository.findByStudentId("stu_1")).thenReturn(List.of(
            record(AssessmentType.MOCK_INTERVIEW, 72, LocalDate.parse("2026-02-01"))));
        when(projectRepository.findByStudentIdOrderBySortOrder("stu_1")).thenReturn(List.of());

        ReadinessScoreRequestBody body = assembler.assemble(studentWithAcademics(null, null, null, null));

        assertThat(body.interview()).isEqualTo(72.0);
    }

    @Test
    void academicBlendsPresentSignalsAndAppliesBacklogPenalty() {
        emptyAssessmentsAndProjects();

        // cgpa 8.0 -> 80; tenth 90; twelfth 70. base = 0.6*80 + 0.2*90 + 0.2*70 = 80. Two backlogs -> -6.
        ReadinessScoreRequestBody body = assembler.assemble(
            studentWithAcademics(new BigDecimal("8.0"), new BigDecimal("90"), new BigDecimal("70"), 2));

        assertThat(body.academic()).isEqualTo(74.0);
    }

    @Test
    void academicRenormalisesWhenSomeSignalsAbsent() {
        emptyAssessmentsAndProjects();

        // Only cgpa present -> blend renormalises to just cgpa*10 = 80; no backlogs.
        ReadinessScoreRequestBody body = assembler.assemble(
            studentWithAcademics(new BigDecimal("8.0"), null, null, 0));

        assertThat(body.academic()).isEqualTo(80.0);
    }

    @Test
    void academicIsNullWhenNoAcademicSignalPresent() {
        emptyAssessmentsAndProjects();

        ReadinessScoreRequestBody body = assembler.assemble(studentWithAcademics(null, null, null, 0));

        assertThat(body.academic()).isNull();
    }

    @Test
    void projectsHeuristicSumsAndCapsAtHundred() {
        when(assessmentRepository.findByStudentId("stu_1")).thenReturn(List.of());
        when(projectRepository.findByStudentIdOrderBySortOrder("stu_1")).thenReturn(List.of(
            project("A", "did things", "[\"React\",\"Spring\"]"),   // 20 + 10 + 4*2 = 38
            project("B", null, "[\"Go\"]"),                         // 20 + 0 + 4*1 = 24
            project("C", "more", "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]"))); // 20 + 10 + 4*5 = 50

        // 38 + 24 + 50 = 112 -> capped at 100.
        ReadinessScoreRequestBody body = assembler.assemble(studentWithAcademics(null, null, null, null));

        assertThat(body.projects()).isEqualTo(100.0);
    }

    @Test
    void projectsIsNullWhenNoProjects() {
        emptyAssessmentsAndProjects();

        ReadinessScoreRequestBody body = assembler.assemble(studentWithAcademics(null, null, null, null));

        assertThat(body.projects()).isNull();
    }

    private void emptyAssessmentsAndProjects() {
        when(assessmentRepository.findByStudentId("stu_1")).thenReturn(List.of());
        when(projectRepository.findByStudentIdOrderBySortOrder("stu_1")).thenReturn(List.of());
    }

    private AssessmentRecord record(AssessmentType type, int normalized, LocalDate date) {
        return AssessmentRecord.builder()
            .id("asr_" + type + "_" + date)
            .studentId("stu_1").campusId("campus_a")
            .assessmentType(type).assessmentName(type.name())
            .rawScore(new BigDecimal(normalized)).maxScore(new BigDecimal("100"))
            .normalizedScore(normalized).assessmentDate(date).source("TEST")
            .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
            .build();
    }

    private StudentProjectEntry project(String name, String summary, String stackJson) {
        return StudentProjectEntry.builder()
            .id("proj_" + name).studentId("stu_1").campusId("campus_a")
            .name(name).summary(summary).stackJson(stackJson).sortOrder(0)
            .build();
    }

    private Student studentWithAcademics(BigDecimal cgpa, BigDecimal tenth, BigDecimal twelfth, Integer backlogs) {
        return Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("d@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(cgpa).tenthPercentage(tenth).twelfthPercentage(twelfth).backlogs(backlogs)
            .placementStatus("ACTIVE")
            .build();
    }
}
