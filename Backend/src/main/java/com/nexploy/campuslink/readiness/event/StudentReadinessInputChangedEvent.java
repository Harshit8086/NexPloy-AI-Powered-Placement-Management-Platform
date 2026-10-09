package com.nexploy.campuslink.readiness.event;

/**
 * Signals that a readiness INPUT for a student changed — an academic field (CGPA / 10th / 12th),
 * active backlogs, projects, or a newly recorded assessment / mock-interview score — and their
 * readiness should be recomputed from existing data.
 *
 * <p>Deliberately a lightweight internal signal, not a {@code DomainEvent}: it drives a recompute,
 * it is not itself an audited business fact, so it stays out of the domain-event audit trail and
 * Kafka stream. Handled off-request by {@code ReadinessRecomputeListener}.
 */
public record StudentReadinessInputChangedEvent(String studentId, String campusId) {
}
