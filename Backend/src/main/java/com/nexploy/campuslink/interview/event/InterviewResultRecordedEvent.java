package com.nexploy.campuslink.interview.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an interview outcome is recorded (selected or rejected). The student is the
 * interested party, so the notification consumer tells them the result. Only terminal outcomes are
 * published — a PENDING result raises no event.
 */
public record InterviewResultRecordedEvent(
    String eventId,
    String campusId,
    String interviewId,
    String studentId,
    String round,
    String companyName,
    boolean selected,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static InterviewResultRecordedEvent of(String campusId, String interviewId, String studentId,
                                                  String round, String companyName, boolean selected,
                                                  OffsetDateTime occurredAt) {
        return new InterviewResultRecordedEvent("evt_" + UUID.randomUUID(), campusId, interviewId,
            studentId, round, companyName, selected, occurredAt);
    }

    @Override
    public String eventType() {
        return "InterviewResultRecorded";
    }

    @Override
    public String aggregateId() {
        return interviewId;
    }

    @Override
    public String summary() {
        return "Interview " + interviewId + " result recorded for student " + studentId
            + (selected ? " (selected)" : " (rejected)");
    }
}
