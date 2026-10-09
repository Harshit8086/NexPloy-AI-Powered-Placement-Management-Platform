package com.nexploy.campuslink.student.dto;

/**
 * Read-only digital-twin summary for the signed-in student: the headline readiness score and a
 * derived status label. {@code finalScore} is the student's stored {@code readinessScore} — never
 * recomputed or fabricated here (derived scores stay authoritative). The label is a deterministic
 * presentation string built from that score.
 */
public record DigitalTwinResponse(Integer finalScore, String label) {
}
