package com.nexploy.campuslink.aptitude.ai;

import java.util.List;

/**
 * Bounded AI capability behind the aptitude <em>practice lane</em> only: generate practice
 * questions and narrate a completed attempt. Two adapters implement it — the LLM-backed {@link
 * ChatClientAptitudeAi} (active only when {@code nexploy.ai.provider=llm}) and the offline {@link
 * RuleBasedAptitudeAi} default — so the app boots and tests run with no API key, exactly like the
 * mock-interview adapters.
 *
 * <p>The official lane never calls this interface: its questions come from the curated bank and its
 * scoring is deterministic. Practice-lane output is read-only evidence and never feeds placement
 * readiness, because an AI answer key may be wrong.
 */
public interface AptitudeAi {

    /**
     * Generate {@code count} practice MCQs for one category (QUANTITATIVE, LOGICAL, VERBAL or
     * DATA_INTERPRETATION). Implementations must return complete, self-consistent questions: four
     * options, a correctOption in A..D and an explanation.
     */
    List<AptitudeQuestionDraft> generatePracticeQuestions(String category, int count);

    /**
     * Narrate a completed attempt from its deterministic totals only — the AI never sees raw
     * questions or answers, so it can neither leak content nor second-guess the scoring.
     */
    AptitudeAnalysis analyze(AptitudeAttemptStats stats);
}
