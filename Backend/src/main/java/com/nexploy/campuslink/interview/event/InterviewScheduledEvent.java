package com.nexploy.campuslink.interview.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a new interview is scheduled for a student. The student is the interested party,
 * so the notification consumer notifies them with the company and time.
 */
public record InterviewScheduledEvent(
    String eventId,
    String campusId,
    String interviewId,
    String studentId,
    String round,
    String companyName,
    OffsetDateTime scheduledAt,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static InterviewScheduledEvent of(String campusId, String interviewId, String studentId,
                                             String round, String companyName, OffsetDateTime scheduledAt,
                                             OffsetDateTime occurredAt) {
        return new InterviewScheduledEvent("evt_" + UUID.randomUUID(), campusId, interviewId, studentId,
            round, companyName, scheduledAt, occurredAt);
    }

    @Override
    public String eventType() {
        return "InterviewScheduled";
    }

    @Override
    public String aggregateId() {
        return interviewId;
    }

    @Override
    public String summary() {
        return "Interview " + interviewId + " scheduled for student " + studentId;
    }
}
