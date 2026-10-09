package com.nexploy.campuslink.interview.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a scheduled interview is cancelled. The student is the interested party, so the
 * notification consumer notifies them.
 */
public record InterviewCancelledEvent(
    String eventId,
    String campusId,
    String interviewId,
    String studentId,
    String round,
    String companyName,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static InterviewCancelledEvent of(String campusId, String interviewId, String studentId,
                                             String round, String companyName, OffsetDateTime occurredAt) {
        return new InterviewCancelledEvent("evt_" + UUID.randomUUID(), campusId, interviewId, studentId,
            round, companyName, occurredAt);
    }

    @Override
    public String eventType() {
        return "InterviewCancelled";
    }

    @Override
    public String aggregateId() {
        return interviewId;
    }

    @Override
    public String summary() {
        return "Interview " + interviewId + " cancelled for student " + studentId;
    }
}
