package com.nexploy.campuslink.attendance.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Boundary to the Python ML inference service for interview-attendance estimation. The result
 * is advisory only: it can inform the OR-Tools soft objective (prefer slots students are likely
 * to attend) but never overrides a hard scheduling constraint. Spring owns feasibility.
 */
public interface MlAttendanceClient {

    AttendancePrediction predict(AttendancePredictRequest request);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AttendancePredictRequest(
        String requestId,
        String campusId,
        String placementCycleId,
        String modelPolicyVersion,
        String studentId,
        String slotId,
        Signals signals
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Signals(
        Double priorAttendanceRate,
        Double notificationLeadHours,
        Integer daysToNearestExam,
        Integer slotHour,
        Boolean isWeekend,
        Integer travelMinutes
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AttendancePrediction(
        String studentId,
        String slotId,
        double attendanceProbability,
        String band,
        List<Factor> topFactors,
        double confidence,
        List<String> warnings,
        String modelVersion,
        String featureVersion,
        OffsetDateTime generatedAt
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Factor(
        String name,
        double impact,
        String direction
    ) {
    }
}
