package com.nexploy.campuslink.readiness.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.readiness.client.MlReadinessClient;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScore;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScoreRequest;
import com.nexploy.campuslink.readiness.dto.ReadinessScoreRequestBody;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class ReadinessServiceTest {

    private final StudentRepository studentRepository = org.mockito.Mockito.mock(StudentRepository.class);
    private final com.nexploy.campuslink.readiness.repository.SkillEvidenceRepository skillEvidenceRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.readiness.repository.SkillEvidenceRepository.class);
    private final com.nexploy.campuslink.readiness.repository.ReadinessSnapshotRepository snapshotRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.readiness.repository.ReadinessSnapshotRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final MlReadinessClient mlReadinessClient = org.mockito.Mockito.mock(MlReadinessClient.class);
    private final com.nexploy.campuslink.student.service.ReadinessInputAssembler inputAssembler =
        org.mockito.Mockito.mock(com.nexploy.campuslink.student.service.ReadinessInputAssembler.class);
    private final ReadinessService service =
        new ReadinessService(studentRepository, skillEvidenceRepository, snapshotRepository,
            campusAccessService, mlReadinessClient, inputAssembler,
            new com.fasterxml.jackson.databind.ObjectMapper());

    ReadinessServiceTest() {
        ReflectionTestUtils.setField(service, "modelPolicyVersion", "policy-test");
    }

    @Test
    void forwardsResolvedCampusAndAssessmentsToMlClient() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo")));
        when(mlReadinessClient.score(org.mockito.ArgumentMatchers.any()))
            .thenReturn(sampleScore());

        service.score("stu_demo", "cycle-2026", new ReadinessScoreRequestBody(
            80.0, 79.0, 69.0, 80.0, 72.0));

        ArgumentCaptor<ReadinessScoreRequest> captor = ArgumentCaptor.forClass(ReadinessScoreRequest.class);
        org.mockito.Mockito.verify(mlReadinessClient).score(captor.capture());
        ReadinessScoreRequest sent = captor.getValue();
        assertThat(sent.campusId()).isEqualTo("campus_demo");
        assertThat(sent.placementCycleId()).isEqualTo("cycle-2026");
        assertThat(sent.modelPolicyVersion()).isEqualTo("policy-test");
        assertThat(sent.assessments().aptitude()).isEqualTo(79.0);
    }

    @Test
    void recomputeWritesDerivedScoreBackToDisplayedColumns() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Student student = student("campus_demo");
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student));
        when(inputAssembler.assemble(student)).thenReturn(new ReadinessScoreRequestBody(
            80.0, null, null, null, null));
        when(mlReadinessClient.score(org.mockito.ArgumentMatchers.any())).thenReturn(sampleScore());

        service.recompute("stu_demo", "cycle-2026");

        // Score is written back to the column the UI reads (rounded), and the breakdown JSON is set.
        assertThat(student.getReadinessScore()).isEqualTo(78);
        assertThat(student.getBreakdownJson()).contains("academic");
        org.mockito.Mockito.verify(studentRepository).save(student);
        // Derived inputs came from the assembler, never a caller-supplied body.
        org.mockito.Mockito.verify(inputAssembler).assemble(student);
    }

    @Test
    void recruiterCannotScoreStudentOutsideCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_other")));

        assertThatThrownBy(() -> service.score("stu_demo", "current",
            new ReadinessScoreRequestBody(80.0, null, null, null, null)))
            .isInstanceOf(AccessDeniedException.class);
    }

    private ReadinessScore sampleScore() {
        return new ReadinessScore("stu_demo", 78.4, "READY", Map.of("academic", 82.0),
            List.of(), 1.0, List.of(), "readiness-baseline-2026-01", "student-features-1",
            OffsetDateTime.parse("2026-09-20T12:00:00Z"));
    }

    private Student student(String campusId) {
        return Student.builder()
            .id("stu_demo").campusId(campusId).name("Demo").email("d@e.example")
            .rollNumber("D-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80).placementStatus("ACTIVE")
            .build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder().username("user-" + role).password("{noop}s")
            .campusId(campusId).role(role).enabled(true).build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
