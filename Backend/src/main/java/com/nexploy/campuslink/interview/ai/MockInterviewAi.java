package com.nexploy.campuslink.interview.ai;

import com.nexploy.campuslink.interview.entity.MockInterviewType;
import java.util.List;

/**
 * Bounded AI capability behind the AI mock-interview feature: generate questions, evaluate a single
 * answer, and summarise a completed session. Two adapters implement it — the LLM-backed {@link
 * ChatClientMockInterviewAi} (active only when {@code nexploy.ai.provider=llm}) and the offline
 * {@link RuleBasedMockInterviewAi} default — so the app boots and tests run with no API key, exactly
 * like the extraction adapters.
 *
 * <p>All model output is read-only evidence. Callers screen free-text student input through {@code
 * PromptInjectionGuard} before it reaches an implementation, and the model is never allowed to
 * mutate authorization, eligibility, offers or scores of record.
 */
public interface MockInterviewAi {

    /**
     * Generate the ordered question set for a new practice interview. {@code resumeContext} is a
     * short, already-sanitised summary of the student's stored resume signals when {@code useResume}
     * is set, otherwise null/blank. Implementations must never fabricate more grounding than they
     * were given.
     */
    List<QuestionDraft> generateQuestions(
        MockInterviewType type, String role, boolean useResume, String resumeContext);

    /** Evaluate one answer to one question across the nine metrics. */
    AnswerEvaluation evaluate(String question, String answer);

    /** Produce the final report from every answered question in the session. */
    InterviewSummary summarize(String role, MockInterviewType type, List<AnsweredQuestion> answers);
}
