package com.nexploy.campuslink.interview.event;

import java.time.OffsetDateTime;

/**
 * Published once, when an AI practice ("mock") interview transitions to COMPLETED and its report is
 * written. Carries only the finished run's descriptive scores so a listener can, when explicitly
 * enabled, fold them into the student's assessment history.
 *
 * <p>Deliberately a plain record and NOT a {@code DomainEvent}: mock-interview activity is practice
 * evidence, so it is neither audited nor published to Kafka as a domain fact. The only listener is
 * {@link MockInterviewAssessmentBridge}, which is off by default. {@code communicationScore} is null
 * when no answered question carried a communication metric.
 */
public record MockInterviewCompletedEvent(
    String studentId,
    String campusId,
    String mockInterviewId,
    Integer overallScore,
    Integer communicationScore,
    OffsetDateTime completedAt) {
}
