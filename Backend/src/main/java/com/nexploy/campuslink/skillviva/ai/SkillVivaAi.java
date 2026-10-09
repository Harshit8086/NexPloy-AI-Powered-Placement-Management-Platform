package com.nexploy.campuslink.skillviva.ai;

import java.util.List;

/**
 * Bounded AI capability behind the Skill Viva — a short, skill-scoped verification: generate a few
 * multiple-choice questions that test ONE named skill, and score the chosen option 0-100. Mirrors
 * the mock interview's {@code MockInterviewAi} split: an LLM adapter ({@link ChatClientSkillVivaAi},
 * active only when {@code nexploy.ai.provider=llm}) and an offline {@link RuleBasedSkillVivaAi}
 * default, so the app boots and tests run with no API key.
 *
 * <p>Questions are scored server-side by matching the submitted option's TEXT against the question's
 * correct option (the options are shuffled to a random position before reaching the client, so
 * position gives nothing away), and the correct option is never sent to the client. All model output
 * is read-only evidence: the caller
 * screens the skill label through {@code PromptInjectionGuard} first, and the model never mutates
 * verification status directly.
 */
public interface SkillVivaAi {

    /** Generate the ordered MCQ set probing {@code skill} at roughly {@code targetLevel} (0-100). */
    List<VivaQuestion> generateQuestions(String skill, int targetLevel);

    /** One generated MCQ: the prompt plus exactly 4 options, one of them correct. */
    record VivaQuestion(String prompt, List<String> options, String correctOption) {

        public VivaQuestion {
            if (options == null || options.size() != 4) {
                throw new IllegalArgumentException("A viva MCQ needs exactly 4 options");
            }
            options = List.copyOf(options);
        }
    }
}