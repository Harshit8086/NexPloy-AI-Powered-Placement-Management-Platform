package com.nexploy.campuslink.copilot.responder;

import java.util.List;

/**
 * Optional enhancer that rewrites the Copilot's already-computed, data-grounded answer as more fluent
 * prose. It is the only LLM seam in the copilot module and is wired only when
 * {@code nexploy.ai.provider=llm}; in the default rule-based mode no implementation exists and
 * {@code CopilotService} returns its deterministic answer unchanged.
 *
 * <p><strong>Numbers are never the LLM's job.</strong> The responder receives the caller's real facts
 * (all already read from the database) and the deterministic answer built from them, and may only
 * rephrase — it must not invent, recompute, or contradict any number, drive, or skill.
 *
 * <p><strong>No PII crosses this boundary.</strong> The supplied facts are limited to the caller's
 * role and non-identifying numeric/skill facts (target role, readiness score, placement probability,
 * resume score, gap and strength skill names, open-drive count, top-match role and score). Student
 * identity — id, name, email, phone — is intentionally absent.
 */
public interface CopilotResponder {

    /**
     * Returns fluent prose grounded strictly in {@code facts} and {@code deterministicAnswer}. May
     * return null/blank to signal "no usable rewrite" (the caller keeps its deterministic answer); any
     * thrown {@link RuntimeException} is likewise treated by the caller as "fall back to deterministic".
     */
    String rephrase(CopilotFacts facts, String question, String deterministicAnswer);

    /**
     * Non-PII facts grounding a Copilot answer, all read from the caller's own stored data upstream.
     * {@code studentGrounded} is false for non-student roles, where only {@code role} is meaningful.
     */
    record CopilotFacts(
        String role,
        boolean studentGrounded,
        String targetRole,
        Integer readinessScore,
        Integer placementProbability,
        Integer resumeScore,
        List<String> topGaps,
        List<String> strengths,
        int openDriveCount,
        String topMatchRole,
        Integer topMatchScore
    ) {
    }
}
