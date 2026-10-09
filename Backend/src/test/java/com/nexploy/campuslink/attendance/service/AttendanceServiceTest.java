package com.nexploy.campuslink.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.attendance.client.MlAttendanceClient;
import com.nexploy.campuslink.attendance.client.MlAttendanceClient.AttendancePrediction;
import com.nexploy.campuslink.attendance.client.MlAttendanceClient.AttendancePredictRequest;
import com.nexploy.campuslink.attendance.dto.AttendancePredictRequestBody;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.exception.ScheduleEventNotFoundException;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class AttendanceServiceTest {

    private final ScheduleEventRepository eventRepository =
        org.mockito.Mockito.mock(ScheduleEventRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final MlAttendanceClient mlAttendanceClient = org.mockito.Mockito.mock(MlAttendanceClient.class);
    private final AttendanceService service =
        new AttendanceService(eventRepository, campusAccessService, mlAttendanceClient);

    AttendanceServiceTest() {
        ReflectionTestUtils.setField(service, "modelPolicyVersion", "policy-test");
    }

    @Test
    void derivesSlotSignalsFromEventAndForwardsBehaviouralSignals() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        // Saturday 09:00 slot.
        OffsetDateTime saturday = OffsetDateTime.parse("2026-09-19T09:00:00Z");
        when(eventRepository.findById("slot_demo")).thenReturn(Optional.of(event("campus_demo", saturday)));
        when(mlAttendanceClient.predict(org.mockito.ArgumentMatchers.any())).thenReturn(sample());

        service.predict("slot_demo", "stu_demo", "cycle-2026",
            new AttendancePredictRequestBody(80.0, 24.0, 3, 15));

        ArgumentCaptor<AttendancePredictRequest> captor = ArgumentCaptor.forClass(AttendancePredictRequest.class);
        org.mockito.Mockito.verify(mlAttendanceClient).predict(captor.capture());
        AttendancePredictRequest sent = captor.getValue();
        assertThat(sent.campusId()).isEqualTo("campus_demo");
        assertThat(sent.modelPolicyVersion()).isEqualTo("policy-test");
        // Slot hour and weekend are derived from the event, not the request body.
        assertThat(sent.signals().slotHour()).isEqualTo(9);
        assertThat(sent.signals().isWeekend()).isTrue();
        // Behavioural signals come from the request body.
        assertThat(sent.signals().priorAttendanceRate()).isEqualTo(80.0);
        assertThat(sent.signals().travelMinutes()).isEqualTo(15);
    }

    @Test
    void recruiterCannotPredictForSlotOutsideCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        OffsetDateTime slot = OffsetDateTime.parse("2026-09-21T11:00:00Z");
        when(eventRepository.findById("slot_other")).thenReturn(Optional.of(event("campus_other", slot)));

        assertThatThrownBy(() -> service.predict("slot_other", "stu_demo", "current",
            new AttendancePredictRequestBody(null, null, null, null)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unknownSlotIsRejected() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(eventRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.predict("missing", "stu_demo", "current",
            new AttendancePredictRequestBody(null, null, null, null)))
            .isInstanceOf(ScheduleEventNotFoundException.class);
    }

    private AttendancePrediction sample() {
        return new AttendancePrediction("stu_demo", "slot_demo", 0.7, "LIKELY", List.of(), 0.8,
            List.of(), "attendance-baseline-2026-01", "slot-features-1",
            OffsetDateTime.parse("2026-09-20T12:00:00Z"));
    }

    private ScheduleEvent event(String campusId, OffsetDateTime startsAt) {
        return ScheduleEvent.builder()
            .id("slot_demo").driveId("drive_1").campusId(campusId).venueId("venue_1").panelId("panel_1")
            .startsAt(startsAt).endsAt(startsAt.plusHours(1)).expectedParticipants(20).venueCapacity(30)
            .conflict(false).build();
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
