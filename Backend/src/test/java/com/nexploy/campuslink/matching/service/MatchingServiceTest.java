package com.nexploy.campuslink.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.matching.client.MlMatchingClient;
import com.nexploy.campuslink.matching.client.MlMatchingClient.Candidate;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingRequest;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingResult;
import com.nexploy.campuslink.matching.dto.RankRequestBody;
import com.nexploy.campuslink.matching.dto.RankRequestBody.CandidateInput;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingServiceTest {

    private final DriveRepository driveRepository = org.mockito.Mockito.mock(DriveRepository.class);
    private final StudentRepository studentRepository = org.mockito.Mockito.mock(StudentRepository.class);
    private final com.nexploy.campuslink.student.repository.StudentSkillRepository studentSkillRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.student.repository.StudentSkillRepository.class);
    private final com.nexploy.campuslink.application.repository.ApplicationRepository applicationRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.application.repository.ApplicationRepository.class);
    private final EligibilityService eligibilityService = org.mockito.Mockito.mock(EligibilityService.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final MlMatchingClient mlMatchingClient = org.mockito.Mockito.mock(MlMatchingClient.class);
    private final com.nexploy.campuslink.drive.service.DriveService driveService =
        org.mockito.Mockito.mock(com.nexploy.campuslink.drive.service.DriveService.class);
    private final com.nexploy.campuslink.student.service.StudentService studentService =
        org.mockito.Mockito.mock(com.nexploy.campuslink.student.service.StudentService.class);
    private final EmbeddingService embeddingService = org.mockito.Mockito.mock(EmbeddingService.class);
    private final com.nexploy.campuslink.matching.repository.MatchResultRepository matchResultRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.matching.repository.MatchResultRepository.class);
    private final com.nexploy.campuslink.placement.repository.PlacementCycleRepository placementCycleRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.placement.repository.PlacementCycleRepository.class);
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    private final com.nexploy.campuslink.student.repository.StudentCertificateRepository
        studentCertificateRepository = org.mockito.Mockito.mock(
            com.nexploy.campuslink.student.repository.StudentCertificateRepository.class);
    private final com.nexploy.campuslink.interview.repository.InterviewRepository interviewRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.interview.repository.InterviewRepository.class);
    private final MatchingService service = new MatchingService(
        driveRepository, studentRepository, studentSkillRepository, applicationRepository, eligibilityService,
        campusAccessService, mlMatchingClient, driveService, studentService, embeddingService,
        matchResultRepository, placementCycleRepository, objectMapper, studentCertificateRepository,
        interviewRepository);

    MatchingServiceTest() {
        ReflectionTestUtils.setField(service, "modelPolicyVersion", "policy-test");
    }

    @Test
    void forwardsSpringComputedEligibilityAndGovernedReadiness() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(driveRepository.findById("drive_demo")).thenReturn(Optional.of(drive("campus_demo")));
        when(studentRepository.findById("stu_a")).thenReturn(Optional.of(student("stu_a", "campus_demo", 82)));
        when(studentRepository.findById("stu_b")).thenReturn(Optional.of(student("stu_b", "campus_demo", 40)));
        when(eligibilityService.evaluate("drive_demo", "stu_a")).thenReturn(eligibility("stu_a", true));
        when(eligibilityService.evaluate("drive_demo", "stu_b")).thenReturn(eligibility("stu_b", false));
        when(interviewRepository.findByStudentId("stu_a")).thenReturn(List.of(
            interview("stu_a", "int_1", "drive_old_1",
                com.nexploy.campuslink.interview.entity.InterviewResult.SELECTED),
            interview("stu_a", "int_2", "drive_old_2",
                com.nexploy.campuslink.interview.entity.InterviewResult.REJECTED)));
        when(mlMatchingClient.rank(org.mockito.ArgumentMatchers.any())).thenReturn(sample());

        service.rank("drive_demo", "cycle-2026", new RankRequestBody(List.of(
            new CandidateInput("stu_a", 90.0, 85.0, 80.0, 88.0, 78.0, 50.0),
            new CandidateInput("stu_b", 70.0, 65.0, 60.0, 62.0, 55.0, null)
        )));

        ArgumentCaptor<MatchingRequest> captor = ArgumentCaptor.forClass(MatchingRequest.class);
        org.mockito.Mockito.verify(mlMatchingClient).rank(captor.capture());
        MatchingRequest sent = captor.getValue();
        assertThat(sent.campusId()).isEqualTo("campus_demo");
        assertThat(sent.modelPolicyVersion()).isEqualTo("policy-test");

        Candidate a = sent.candidates().get(0);
        Candidate b = sent.candidates().get(1);
        // Eligibility comes from Spring's deterministic gate, not the request body.
        assertThat(a.eligible()).isTrue();
        assertThat(b.eligible()).isFalse();
        // Readiness is read from the student record, not supplied by the caller.
        assertThat(a.features().readiness()).isEqualTo(82.0);
        // Fit features come from the request body.
        assertThat(a.features().requiredSkillCoverage()).isEqualTo(90.0);
        assertThat(a.features().certificationRelevance()).isEqualTo(50.0);
        // A null certification relevance is forwarded as missing, not imputed as zero.
        assertThat(b.features().certificationRelevance()).isNull();
        // Historical interview pass rate is aggregated server-side from completed past
        // interviews (1 of 2 selected here); a student with none gets missing, not zero.
        assertThat(a.features().historicalInterviewPassRate()).isEqualTo(50.0);
        assertThat(b.features().historicalInterviewPassRate()).isNull();
    }

    @Test
    void officerCannotRankCandidateOutsideAccessibleCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(driveRepository.findById("drive_demo")).thenReturn(Optional.of(drive("campus_demo")));
        when(studentRepository.findById("stu_other")).thenReturn(Optional.of(student("stu_other", "campus_other", 70)));

        assertThatThrownBy(() -> service.rank("drive_demo", "current",
            new RankRequestBody(List.of(new CandidateInput("stu_other", 50.0, 50.0, 50.0, 50.0, 50.0, 50.0)))))
            .isInstanceOf(AccessDeniedException.class);
    }

    private MatchingResult sample() {
        return new MatchingResult("drive_demo", List.of(), "ranking-baseline-2026-01",
            "match-features-1", OffsetDateTime.parse("2026-09-20T12:00:00Z"));
    }

    private EligibilityResponse eligibility(String studentId, boolean eligible) {
        return new EligibilityResponse(studentId, "drive_demo", eligible, List.of(), List.of());
    }

    private Drive drive(String campusId) {
        return Drive.builder().id("drive_demo").campusId(campusId).recruiterId("rec_1")
            .companyName("Acme").role("SDE").location("Remote").packageLpa(new BigDecimal("12.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026).build();
    }

    private Student student(String id, String campusId, int readiness) {
        return Student.builder()
            .id(id).campusId(campusId).name("Demo").email("d@e.example")
            .rollNumber("D-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(readiness)
            .placementStatus("ACTIVE").build();
    }

    private com.nexploy.campuslink.interview.entity.Interview interview(
        String studentId, String id, String driveId,
        com.nexploy.campuslink.interview.entity.InterviewResult result) {
        return com.nexploy.campuslink.interview.entity.Interview.builder()
            .id(id).studentId(studentId).driveId(driveId).result(result).build();
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
