package com.nexploy.campuslink.student.dto;

import java.util.List;

/**
 * Rich, structured per-resume analysis returned by {@code POST /me/resume-intelligence/analyze}.
 * Every field is grounded: the {@code overallScore} and {@code missingKeywords} are the
 * deterministic analyzer's and are never delegated to the model; the {@code metrics} are
 * transparently-labeled heuristics (each carries the {@code basis} it was derived from, so the UI
 * never shows an unexplained percentage); the prose fields may be enriched by the grounded LLM but
 * never invent numbers, employers, tools or certifications.
 *
 * <p>Distinct from the flat {@link ResumeIntelligenceResponse} (the pre-upload stored summary);
 * this is the deep analysis of an actual uploaded file.
 */
public record ResumeAnalysisResponse(
    Integer overallScore,
    List<Metric> metrics,
    List<String> strengths,
    List<Weakness> weaknesses,
    List<String> missingKeywords,
    List<ProjectSuggestion> projectSuggestions,
    List<SkillMatch> skillMatch,
    List<ExtractedCategory> extracted,
    String recommendation,
    ActionPlan actionPlan,
    ResumeQualityAnalysis resumeQuality) {

    /** A deterministic heuristic sub-score. {@code basis} states exactly how it was computed. */
    public record Metric(String key, String label, Integer value, String basis) {}

    /** One grounded gap. {@code severity} is HIGH/MEDIUM/LOW; evidence cites the resume. */
    public record Weakness(
        String gap, String evidence, String whyItMatters, String severity, String recommendedAction) {}

    /** One suggested project addressing a specific gap. */
    public record ProjectSuggestion(
        String name,
        String description,
        String addressesGap,
        List<String> skillsDemonstrated,
        String difficulty,
        String outcome) {}

    /** One role-expected skill vs. the resume's evidence. Status: DEMONSTRATED/PARTIAL/NOT_DEMONSTRATED. */
    public record SkillMatch(String requiredSkill, String resumeEvidence, String status) {}

    /** Extracted items grouped by category (languages, frameworks, databases, cloud-devops, tools, certifications). */
    public record ExtractedCategory(String category, List<Item> items) {
        public record Item(String value, String evidence) {}
    }

    /** Sequenced next steps derived only from the detected gaps. */
    public record ActionPlan(List<String> now, List<String> next, List<String> later) {}
}
