package com.nexploy.campuslink.attendance.service;

import com.nexploy.campuslink.attendance.client.MlAttendanceClient;
import com.nexploy.campuslink.attendance.client.MlAttendanceClient.AttendancePrediction;
import com.nexploy.campuslink.attendance.client.MlAttendanceClient.AttendancePredictRequest;
import com.nexploy.campuslink.attendance.client.MlAttendanceClient.Signals;
import com.nexploy.campuslink.attendance.dto.AttendancePredictRequestBody;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.exception.ScheduleEventNotFoundException;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import java.time.DayOfWeek;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates interview-attendance estimation for a proposed slot. Spring is the system of
 * record: it resolves the schedule event, enforces campus access, and derives slot signals
 * (hour, weekend) from the event's start time; behavioural signals come from the request. The
 * estimate is advisory only — it can inform the OR-Tools soft objective but never drives a
 * scheduling decision here, and campus access is enforced before anything is forwarded.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final ScheduleEventRepository scheduleEventRepository;
    private final CampusAccessService campusAccessService;
    private final MlAttendanceClient mlAttendanceClient;

    @Value("${ml.policy-version:policy-2026-01}")
    private String modelPolicyVersion;

    public AttendancePrediction predict(
        String slotId, String studentId, String placementCycleId, AttendancePredictRequestBody body) {
        UserAccount user = campusAccessService.currentUser();
        ScheduleEvent event = scheduleEventRepository.findById(slotId)
            .orElseThrow(() -> new ScheduleEventNotFoundException(slotId));
        campusAccessService.requireAccess(user, event.getCampusId());

        Integer slotHour = event.getStartsAt() == null ? null : event.getStartsAt().getHour();
        Boolean weekend = event.getStartsAt() == null ? null : isWeekend(event.getStartsAt().getDayOfWeek());

        AttendancePredictRequest request = new AttendancePredictRequest(
            UUID.randomUUID().toString(),
            event.getCampusId(),
            placementCycleId,
            modelPolicyVersion,
            studentId,
            slotId,
            new Signals(
                body.priorAttendanceRate(),
                body.notificationLeadHours(),
                body.daysToNearestExam(),
                slotHour,
                weekend,
                body.travelMinutes()
            )
        );
        return mlAttendanceClient.predict(request);
    }

    private boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
