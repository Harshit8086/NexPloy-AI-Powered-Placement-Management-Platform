package com.nexploy.campuslink.assessment.service;

import com.nexploy.campuslink.assessment.entity.AssessmentType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A validated, normalized assessment row ready to persist for a resolved student. Produced by an
 * assessment source (e.g. the aptitude quiz or the mock-interview bridge) after all
 * field/score/date/type checks pass, so the transactional writer never has to re-validate.
 */
public record PreparedAssessmentRow(
    AssessmentType type,
    String name,
    BigDecimal rawScore,
    BigDecimal maxScore,
    int normalizedScore,
    LocalDate date,
    String source
) {
}
