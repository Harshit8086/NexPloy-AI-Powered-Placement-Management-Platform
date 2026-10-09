package com.nexploy.campuslink.student.dto;

import java.util.List;

/**
 * §10 Resume Quality Analysis — a grounded, actionable diagnosis of the uploaded resume's quality,
 * nested inside {@link ResumeAnalysisResponse}. This is <strong>not</strong> another score: it
 * explains, dimension by dimension, what reads well and what could be improved, and cites the resume
 * as evidence for every finding.
 *
 * <p><strong>Grounding (PRIME DIRECTIVE).</strong> Every finding is derived deterministically from
 * what the resume actually contains — the same authority model as the score and missing keywords.
 * Nothing is invented: no metrics, employers, job titles, technologies, certifications, achievements
 * or skills. A skill that is not shown is reported as <em>"not demonstrated in the resume"</em>, never
 * as the student "not knowing" it. When a dimension cannot be judged from the extracted text, its
 * status is {@code NOT_ENOUGH_EVIDENCE} and it carries no invented findings.
 */
public record ResumeQualityAnalysis(
    Dimension structure,
    Dimension contentQuality,
    Dimension projectQuality,
    Dimension experienceQuality,
    Dimension quantification,
    Dimension skillsEvidence,
    Dimension targetRoleRelevance,
    Dimension readability,
    List<Finding> topImprovements) {

    /**
     * One quality dimension. {@code status} is a closed vocabulary describing the dimension at a
     * glance; {@code findings} are the specific, evidence-backed observations (may be empty when the
     * dimension is clean).
     *
     * <p>Status values: {@code GOOD} (no material concern found), {@code NEEDS_ATTENTION} (one or more
     * MEDIUM/HIGH findings), {@code NOT_ENOUGH_EVIDENCE} (the resume lacks the content needed to judge
     * this dimension — reported honestly rather than guessed).
     */
    public record Dimension(String status, List<Finding> findings) {}

    /**
     * One actionable finding. {@code evidence} cites the resume verbatim (a matching line) or states
     * plainly what is absent — never a fabricated quote. {@code severity} is constrained to
     * INFO / LOW / MEDIUM / HIGH.
     */
    public record Finding(
        String category, String issue, String evidence, String recommendation, String severity) {}
}
