package com.nexploy.campuslink.interview.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a scheduled interview is rescheduled. The student is the interested party, so the
 * notification consumer notifies them with the new time.
 */
public record InterviewRescheduledEvent(
    String eventId,
    String campusId,
    String interviewId,
    String studentId,
    String round,
    String companyName,
    OffsetDateTime scheduledAt,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static InterviewRescheduledEvent of(String campusId, String interviewId, String studentId,
                                               String round, String companyName, OffsetDateTime scheduledAt,
                                               OffsetDateTime occurredAt) {
        return new InterviewRescheduledEvent("evt_" + UUID.randomUUID(), campusId, interviewId, studentId,
            round, companyName, scheduledAt, occurredAt);
    }

    @Override
    public String eventType() {
        return "InterviewRescheduled";
    }

    @Override
    public String aggregateId() {
        return interviewId;
    }

    @Override
    public String summary() {
        return "Interview " + interviewId + " rescheduled for student " + studentId;
    }
}
