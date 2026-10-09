package com.nexploy.campuslink.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.entity.MockInterview;
import com.nexploy.campuslink.interview.entity.MockInterviewStatus;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.interview.repository.MockInterviewRepository;
import com.nexploy.campuslink.risk.client.MlRiskClient;
import com.nexploy.campuslink.risk.client.MlRiskClient.RiskPrediction;
import com.nexploy.campuslink.risk.client.MlRiskClient.RiskPredictRequest;
import com.nexploy.campuslink.risk.dto.RiskPredictRequestBody;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class RiskServiceTest {

    private final StudentRepository studentRepository = org.mockito.Mockito.mock(StudentRepository.class);
    private final com.nexploy.campuslink.risk.repository.RiskEntryRepository riskEntryRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.risk.repository.RiskEntryRepository.class);
    private final ApplicationRepository applicationRepository = org.mockito.Mockito.mock(ApplicationRepository.class);
    private final InterviewRepository interviewRepository = org.mockito.Mockito.mock(InterviewRepository.class);
    private final MockInterviewRepository mockInterviewRepository =
        org.mockito.Mockito.mock(MockInterviewRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final MlRiskClient mlRiskClient = org.mockito.Mockito.mock(MlRiskClient.class);
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
        org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
    private final RiskService service = new RiskService(studentRepository, riskEntryRepository, applicationRepository,
        interviewRepository, mockInterviewRepository, campusAccessService, mlRiskClient,
        new com.fasterxml.jackson.databind.ObjectMapper(), eventPublisher);

    RiskServiceTest() {
        ReflectionTestUtils.setField(service, "modelPolicyVersion", "policy-test");
    }

    @Test
    void readsGovernedFieldsFromStudentAndForwardsActivitySignals() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo", 80, 1)));
        when(applicationRepository.findByStudentId("stu_demo")).thenReturn(List.of());
        when(interviewRepository.findByStudentId("stu_demo")).thenReturn(List.of());
        when(mockInterviewRepository.findByStudentIdOrderByCreatedAtDesc("stu_demo")).thenReturn(List.of());
        when(mlRiskClient.predict(org.mockito.ArgumentMatchers.any())).thenReturn(sample());
        when(riskEntryRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.predict("stu_demo", "cycle-2026", new RiskPredictRequestBody(4, 2, 1, 5, 3));

        ArgumentCaptor<RiskPredictRequest> captor = ArgumentCaptor.forClass(RiskPredictRequest.class);
        org.mockito.Mockito.verify(mlRiskClient).predict(captor.capture());
        RiskPredictRequest sent = captor.getValue();
        assertThat(sent.campusId()).isEqualTo("campus_demo");
        assertThat(sent.modelPolicyVersion()).isEqualTo("policy-test");
        // Governed fields come from the student record, not the request body.
        assertThat(sent.signals().readinessScore()).isEqualTo(80.0);
        assertThat(sent.signals().backlogCount()).isEqualTo(1);
        // Explicit activity signals override values derived from stored records.
        assertThat(sent.signals().applicationsSubmitted()).isEqualTo(4);
        assertThat(sent.signals().interviewsMissed()).isEqualTo(1);
    }

    @Test
    void derivesActivitySignalsFromApplicationsAndInterviewsWhenNotSupplied() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo", 80, 1)));
        when(applicationRepository.findByStudentId("stu_demo")).thenReturn(List.of(
            Application.builder().id("app1").studentId("stu_demo").status(ApplicationStatus.APPLIED)
                .appliedOn(LocalDate.now().minusDays(6)).build(),
            Application.builder().id("app2").studentId("stu_demo").status(ApplicationStatus.INTERVIEW)
                .appliedOn(LocalDate.now().minusDays(4)).build()
        ));
        when(interviewRepository.findByStudentId("stu_demo")).thenReturn(List.of(
            Interview.builder().id("int1").studentId("stu_demo").status(InterviewStatus.COMPLETED)
                .scheduledAt(OffsetDateTime.now().minusDays(3)).build(),
            Interview.builder().id("int2").studentId("stu_demo").status(InterviewStatus.NO_SHOW)
                .scheduledAt(OffsetDateTime.now().minusDays(1)).build()
        ));
        when(mockInterviewRepository.findByStudentIdOrderByCreatedAtDesc("stu_demo")).thenReturn(List.of(
            MockInterview.builder().id("mock1").studentId("stu_demo").status(MockInterviewStatus.COMPLETED)
                .updatedAt(OffsetDateTime.now().minusDays(2)).build(),
            MockInterview.builder().id("mock2").studentId("stu_demo").status(MockInterviewStatus.IN_PROGRESS)
                .updatedAt(OffsetDateTime.now()).build()
        ));
        when(mlRiskClient.predict(org.mockito.ArgumentMatchers.any())).thenReturn(sample());
        when(riskEntryRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.predict("stu_demo", "cycle-2026", new RiskPredictRequestBody(null, null, null, null, null));

        ArgumentCaptor<RiskPredictRequest> captor = ArgumentCaptor.forClass(RiskPredictRequest.class);
        org.mockito.Mockito.verify(mlRiskClient).predict(captor.capture());
        assertThat(captor.getValue().signals().applicationsSubmitted()).isEqualTo(2);
        assertThat(captor.getValue().signals().interviewsAttended()).isEqualTo(1);
        assertThat(captor.getValue().signals().interviewsMissed()).isEqualTo(1);
        assertThat(captor.getValue().signals().daysSinceLastActivity()).isEqualTo(1);
        assertThat(captor.getValue().signals().mockInterviewsCompleted()).isEqualTo(1);
    }

    @Test
    void recruiterCannotPredictStudentOutsideCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_other", 80, 0)));

        assertThatThrownBy(() -> service.predict("stu_demo", "current",
            new RiskPredictRequestBody(null, null, null, null, null)))
            .isInstanceOf(AccessDeniedException.class);
    }

    private RiskPrediction sample() {
        return new RiskPrediction("stu_demo", 0.2, "LOW", List.of(), List.of("none"), 1.0,
            List.of(), "risk-baseline-2026-01", "student-features-1",
            OffsetDateTime.parse("2026-09-20T12:00:00Z"));
    }

    private Student student(String campusId, int readiness, int backlogs) {
        return Student.builder()
            .id("stu_demo").campusId(campusId).name("Demo").email("d@e.example")
            .rollNumber("D-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(backlogs).readinessScore(readiness)
            .placementStatus("ACTIVE").build();
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
