package com.nexploy.campuslink.student.service;

import java.util.List;

/**
 * Optional LLM enhancer for resume intelligence, the sibling of the skill-gap narrator. When
 * {@code nexploy.ai.provider=llm} an implementation is wired and may rewrite the three free-text
 * fields — {@code weaknesses}, {@code projectSuggestions} and {@code recommendation} — into richer,
 * resume-specific prose grounded in the actual resume text. In the default rule-based mode no
 * implementation exists and {@link ResumeIntelligenceService} keeps {@link ResumeAnalyzer}'s
 * deterministic text.
 *
 * <p><strong>Numbers and facts are never the LLM's job.</strong> The {@code score} and
 * {@code missingKeywords} are computed deterministically by {@link ResumeAnalyzer} and are never
 * sent for the model to alter. The writer receives the resume text plus the already-detected gaps
 * only so its prose stays grounded; the caller overlays only the three text fields and discards
 * anything else.
 *
 * <p><strong>Statelessness.</strong> Like the extraction seam, this runs per upload and persists
 * nothing. The resume text crosses the same Groq boundary the resume-extraction endpoint already
 * uses; it is screened for prompt injection before the call and treated by the model as data only.
 */
public interface ResumeInsightWriter {

    /**
     * Returns grounded prose for the three text fields, or {@code null} / a record with null fields
     * if it cannot. Any thrown {@link RuntimeException} is treated by the caller as "keep the
     * deterministic text".
     *
     * @param resumeText     the parsed resume text (screened upstream)
     * @param targetRole     the student's target role (may be blank)
     * @param detectedGaps   the deterministic weaknesses already found, as grounding
     * @param missingKeywords the authoritative missing keywords (the model must not change these)
     */
    ResumeInsight write(String resumeText, String targetRole, List<String> detectedGaps, List<String> missingKeywords);

    /** The three free-text fields the writer may supply; any null field falls back to deterministic text. */
    record ResumeInsight(
        List<String> weaknesses,
        List<String> projectSuggestions,
        String recommendation
    ) {
    }
}
