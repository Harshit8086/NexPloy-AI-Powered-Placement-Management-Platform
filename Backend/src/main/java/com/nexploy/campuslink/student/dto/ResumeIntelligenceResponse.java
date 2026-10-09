package com.nexploy.campuslink.student.dto;

import java.util.List;

/**
 * Read-only resume-intelligence summary for the signed-in student. The {@code score} and
 * {@code strengths} / {@code missingKeywords} are derived from the student's authoritative stored
 * columns (resumeScore, strengthsJson, criticalGapsJson). The coaching text
 * ({@code weaknesses}, {@code projectSuggestions}, {@code recommendation}) is deterministic,
 * rule-based advisory guidance — never fabricated numbers or invented profile facts.
 */
public record ResumeIntelligenceResponse(
    Integer score,
    List<String> strengths,
    List<String> weaknesses,
    List<String> missingKeywords,
    List<String> projectSuggestions,
    String recommendation) {}
