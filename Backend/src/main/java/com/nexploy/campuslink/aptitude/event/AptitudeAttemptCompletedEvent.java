package com.nexploy.campuslink.aptitude.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Published when an OFFICIAL aptitude attempt is submitted — the practice lane never publishes
 * this event, so its only listener ({@link AptitudeAssessmentBridge}, when enabled) can only ever
 * see deterministic bank-based scores. A plain record, mirroring {@code MockInterviewCompletedEvent}.
 */
public record AptitudeAttemptCompletedEvent(
    String studentId,
    String campusId,
    String attemptId,
    String testId,
    BigDecimal score,
    OffsetDateTime completedAt) {
}
