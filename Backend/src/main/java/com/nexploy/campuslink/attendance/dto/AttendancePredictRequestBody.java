package com.nexploy.campuslink.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Behavioural/logistics signals for an attendance estimate not derivable from the schedule
 * event. Slot hour and weekend are derived from the event's start time by the service; these
 * are supplied per request. All optional; the ML service records missing signals rather than
 * imputing, and the estimate is advisory only — it never overrides a hard scheduling constraint.
 */
public record AttendancePredictRequestBody(
    @DecimalMin("0") @DecimalMax("100") Double priorAttendanceRate,
    @PositiveOrZero Double notificationLeadHours,
    @PositiveOrZero Integer daysToNearestExam,
    @PositiveOrZero Integer travelMinutes
) {
}
