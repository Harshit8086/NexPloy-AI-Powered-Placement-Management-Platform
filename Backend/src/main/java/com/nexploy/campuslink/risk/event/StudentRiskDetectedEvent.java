package com.nexploy.campuslink.risk.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a risk prediction lands a student in the HIGH support band (DESIGN.md section 7:
 * {@code StudentRiskDetected}). Support-oriented: it prompts help (e.g. an intervention plan),
 * never exclusion.
 */
public record StudentRiskDetectedEvent(
    String eventId,
    String campusId,
    String studentId,
    String riskEntryId,
    String band,
    double supportProbability,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static StudentRiskDetectedEvent of(String campusId, String studentId, String riskEntryId,
                                              String band, double supportProbability,
                                              OffsetDateTime occurredAt) {
        return new StudentRiskDetectedEvent("evt_" + UUID.randomUUID(), campusId, studentId,
            riskEntryId, band, supportProbability, occurredAt);
    }

    @Override
    public String eventType() {
        return "StudentRiskDetected";
    }

    @Override
    public String aggregateId() {
        return studentId;
    }

    @Override
    public String summary() {
        return "Student " + studentId + " detected in " + band + " support band";
    }
}
