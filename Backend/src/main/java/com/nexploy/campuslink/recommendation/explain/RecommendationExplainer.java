package com.nexploy.campuslink.recommendation.explain;

import com.nexploy.campuslink.recommendation.dto.RecommendationType;
import java.util.List;

/**
 * Boundary that turns already-verified recommendation facts into a short, human-readable explanation.
 * Implementations MUST only write prose grounded in the supplied facts — they never compute or alter
 * eligibility, scores, or the recommendation type. The only implementation today is the Spring AI
 * {@code ChatClientRecommendationExplainer}, active when {@code nexploy.ai.provider=llm}; when it is
 * absent or throws, the caller falls back to a deterministic template.
 */
public interface RecommendationExplainer {

    ExplanationResult explain(RecommendationFacts facts);

    /**
     * Non-PII facts crossing the LLM boundary: the derived type, eligibility verdict + failed criteria,
     * the fit score and per-factor breakdown, and the deterministic strength/gap lists. No student
     * identity, contact details, or resume text is included.
     */
    record RecommendationFacts(
        RecommendationType recommendationType,
        boolean eligibilityPassed,
        List<String> failedCriteria,
        Integer overallFit,
        List<BreakdownFactor> breakdown,
        List<String> whySelected,
        List<String> weakOrMissing
    ) {
    }

    record BreakdownFactor(String name, int value) {
    }

    /** The model's output: a short explanation and optional improvement bullet points. */
    record ExplanationResult(String explanation, List<String> improvementAreas) {
    }
}
