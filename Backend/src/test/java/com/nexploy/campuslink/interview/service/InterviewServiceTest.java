package com.nexploy.campuslink.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.interview.dto.InterviewResponse;
import com.nexploy.campuslink.interview.dto.RecordResultRequest;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewMode;
import com.nexploy.campuslink.interview.entity.InterviewResult;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.exception.InterviewNotFoundException;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    private final InterviewRepository interviewRepository = org.mockito.Mockito.mock(InterviewRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final com.nexploy.campuslink.application.service.ApplicationService applicationService =
        org.mockito.Mockito.mock(com.nexploy.campuslink.application.service.ApplicationService.class);
    private final com.nexploy.campuslink.drive.repository.DriveRepository driveRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.drive.repository.DriveRepository.class);
    private final com.nexploy.campuslink.student.repository.StudentRepository studentRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.student.repository.StudentRepository.class);
    private final com.nexploy.campuslink.recruiter.repository.RecruiterRepository recruiterRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.recruiter.repository.RecruiterRepository.class);
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
        org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
    private final InterviewService service =
        new InterviewService(interviewRepository, campusAccessService, applicationService,
            driveRepository, studentRepository, recruiterRepository, eventPublisher);

    @Test
    void recruiterListSeesOnlyOwnCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(interviewRepository.findByCampusId("campus_demo"))
            .thenReturn(List.of(interview("int_1", "campus_demo")));

        List<InterviewResponse> interviews = service.list("campus_other");

        assertThat(interviews).extracting(InterviewResponse::campusId).containsExactly("campus_demo");
    }

    /** Officers are pinned to their own campus: a requested campusId is ignored, not honored. */
    @Test
    void officerListIsPinnedToOwnCampus() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(interviewRepository.findByCampusId("campus_demo"))
            .thenReturn(List.of(interview("int_2", "campus_demo")));

        List<InterviewResponse> interviews = service.list("campus_other");

        assertThat(interviews).extracting(InterviewResponse::campusId).containsExactly("campus_demo");
    }

    @Test
    void recruiterCannotReadInterviewOutsideCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(interviewRepository.findById("int_x"))
            .thenReturn(Optional.of(interview("int_x", "campus_other")));

        assertThatThrownBy(() -> service.get("int_x"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void recordResultUpdatesResultAndCompletesInterview() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Interview interview = interview("int_1", "campus_demo");
        when(interviewRepository.findById("int_1")).thenReturn(Optional.of(interview));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));

        InterviewResponse response = service.recordResult("int_1",
            new RecordResultRequest(InterviewResult.SELECTED, "Strong system design"));

        assertThat(response.result()).isEqualTo(InterviewResult.SELECTED);
        assertThat(response.status()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(interview.getFeedback()).isEqualTo("Strong system design");
    }

    @Test
    void recordResultSelectedPublishesResultEventForStudent() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Interview interview = interview("int_1", "campus_demo");
        when(interviewRepository.findById("int_1")).thenReturn(Optional.of(interview));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));

        service.recordResult("int_1",
            new RecordResultRequest(InterviewResult.SELECTED, "Strong system design"));

        org.mockito.ArgumentCaptor<com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent> captor =
            org.mockito.ArgumentCaptor.forClass(
                com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent.class);
        org.mockito.Mockito.verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().studentId()).isEqualTo("stu_demo");
        assertThat(captor.getValue().selected()).isTrue();
    }

    @Test
    void recordResultRejectedPublishesResultEventForStudent() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Interview interview = interview("int_1", "campus_demo");
        when(interviewRepository.findById("int_1")).thenReturn(Optional.of(interview));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));

        service.recordResult("int_1",
            new RecordResultRequest(InterviewResult.REJECTED, "Needs more practice"));

        org.mockito.ArgumentCaptor<com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent> captor =
            org.mockito.ArgumentCaptor.forClass(
                com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent.class);
        org.mockito.Mockito.verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().studentId()).isEqualTo("stu_demo");
        assertThat(captor.getValue().selected()).isFalse();
    }

    @Test
    void getMissingInterviewThrows() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(interviewRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing"))
            .isInstanceOf(InterviewNotFoundException.class);
    }

    @Test
    void recruiterCanRecordResultForOwnDrive() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        Interview interview = interview("int_1", "campus_demo");
        when(interviewRepository.findById("int_1")).thenReturn(Optional.of(interview));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));
        when(recruiterRepository.findByEmail("user-RECRUITER"))
            .thenReturn(Optional.of(com.nexploy.campuslink.recruiter.entity.Recruiter.builder()
                .id("rec_technova").campusId("campus_demo").email("user-RECRUITER").build()));

        InterviewResponse response = service.recordResult("int_1",
            new RecordResultRequest(InterviewResult.SELECTED, "Strong system design"));

        assertThat(response.status()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(response.result()).isEqualTo(InterviewResult.SELECTED);
    }

    @Test
    void recruiterCannotRecordResultForAnotherRecruitersDrive() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        Interview interview = interview("int_1", "campus_demo");
        when(interviewRepository.findById("int_1")).thenReturn(Optional.of(interview));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));
        when(recruiterRepository.findByEmail("user-RECRUITER"))
            .thenReturn(Optional.of(com.nexploy.campuslink.recruiter.entity.Recruiter.builder()
                .id("rec_other").campusId("campus_demo").email("user-RECRUITER").build()));

        assertThatThrownBy(() -> service.recordResult("int_1",
            new RecordResultRequest(InterviewResult.SELECTED, "Strong system design")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private com.nexploy.campuslink.drive.entity.Drive technovaDrive() {
        return com.nexploy.campuslink.drive.entity.Drive.builder()
            .id("drv_technova")
            .campusId("campus_demo")
            .recruiterId("rec_technova")
            .build();
    }

    private Interview interview(String id, String campusId) {
        return Interview.builder()
            .id(id)
            .campusId(campusId)
            .studentId("stu_demo")
            .driveId("drv_technova")
            .scheduleEventId("evt_technova")
            .mode(InterviewMode.ON_CAMPUS)
            .scheduledAt(OffsetDateTime.parse("2026-09-22T10:00:00Z"))
            .status(InterviewStatus.SCHEDULED)
            .result(InterviewResult.PENDING)
            .createdAt(OffsetDateTime.parse("2026-09-20T09:00:00Z"))
            .build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder()
            .username("user-" + role)
            .password("{noop}secret")
            .campusId(campusId)
            .role(role)
            .enabled(true)
            .build();
    }

    private void authenticate(UserAccount user) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()));
    }
}
