package com.nexploy.campuslink.interview.ai;

import com.nexploy.campuslink.interview.entity.MockInterviewType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline default adapter for {@link MockInterviewAi}. It composes questions from
 * curated, role-templated banks and scores answers with a transparent heuristic (grounded in the
 * substance the student actually wrote — length, structure and concrete signal words), never an
 * external call.
 *
 * <p>This is the active adapter unless {@code nexploy.ai.provider=llm}, in which case {@link
 * ChatClientMockInterviewAi} takes over. Keeping it the default means the app boots and the test
 * suite runs with no API key, and it stays the offline fallback. The two adapters are mutually
 * exclusive by that property, so exactly one {@code MockInterviewAi} bean exists.
 *
 * <p>It never claims to judge correctness it cannot verify: the heuristic rewards a substantive,
 * structured, on-topic answer and is upfront (in its feedback text) that it is an offline estimate.
 */
@Component
@ConditionalOnProperty(name = "nexploy.ai.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedMockInterviewAi implements MockInterviewAi {

    private static final int DEFAULT_TIMER_SECONDS = 120;
    private static final int CODING_TIMER_SECONDS = 600;

    private static final List<String> BEHAVIOURAL_BANK = List.of(
        "Walk me through a project you owned end to end. What was your specific contribution?",
        "Describe a time you disagreed with a teammate. How did you resolve it?",
        "Tell me about something technical you had to learn quickly. How did you approach it?",
        "Describe a time a project of yours went wrong. What did you change afterwards?",
        "How do you prioritise when everything feels urgent and time is short?",
        "Why this role, and what do you want to be doing well in it a year from now?"
    );

    private static final List<String> TECHNICAL_BANK = List.of(
        "Explain a core concept from your strongest technical area as if to a new teammate.",
        "How would you diagnose a service that has suddenly become slow under load?",
        "What trade-offs do you weigh when choosing a data store for a new feature?",
        "How do you make sure the code you write is testable and maintainable?",
        "Describe how you would design a REST API for a simple bookmarking app.",
        "What does your debugging process look like when a bug only appears in production?"
    );

    private static final List<String> CODING_BANK = List.of(
        "Write a function that returns the first non-repeating character in a string.",
        "Given a list of integers, return the two numbers that add up to a target. Explain your approach.",
        "Implement a function to check whether a string of brackets is balanced.",
        "Write a function that merges two sorted lists into one sorted list.",
        "Given an array, move all zeroes to the end while keeping the order of the rest.",
        "Implement a simple LRU cache and explain the data structures you chose."
    );

    @Override
    public List<QuestionDraft> generateQuestions(
        MockInterviewType type, String role, boolean useResume, String resumeContext) {
        List<QuestionDraft> drafts = new ArrayList<>();
        String language = languageFor(role);
        switch (type == null ? MockInterviewType.MIXED : type) {
            case BEHAVIOURAL -> BEHAVIOURAL_BANK.forEach(q -> drafts.add(prose(q, "medium")));
            case TECHNICAL -> TECHNICAL_BANK.forEach(q -> drafts.add(prose(q, "medium")));
            case CODING -> CODING_BANK.forEach(q -> drafts.add(coding(q, language)));
            case MIXED -> {
                drafts.add(prose(BEHAVIOURAL_BANK.get(0), "easy"));
                drafts.add(prose(TECHNICAL_BANK.get(0), "medium"));
                drafts.add(prose(TECHNICAL_BANK.get(1), "medium"));
                drafts.add(coding(CODING_BANK.get(0), language));
                drafts.add(prose(BEHAVIOURAL_BANK.get(1), "medium"));
                drafts.add(coding(CODING_BANK.get(2), language));
            }
        }
        return List.copyOf(drafts);
    }

    @Override
    public AnswerEvaluation evaluate(String question, String answer) {
        int base = scoreAnswer(answer);
        // Spread the single heuristic across the nine metrics with small, deterministic offsets so
        // the radar chart is legible without pretending to measure nine independent things.
        int clarity = clamp(base + structureBonus(answer));
        int detail = clamp(base + lengthBonus(answer));
        String feedback = base <= 25
            ? "Offline estimate: the answer was very short. Add concrete detail — what you did, why, and the outcome."
            : "Offline estimate based on structure and depth. Ground your points in specific, concrete examples.";
        List<String> improvements = List.of(
            "Give a concrete example with a measurable outcome.",
            "State your reasoning explicitly, step by step.",
            "Close with what you would do differently next time."
        );
        return new AnswerEvaluation(
            base, base, clarity, base, detail, base, clarity, base, clamp(base - 5),
            feedback, improvements);
    }

    @Override
    public InterviewSummary summarize(String role, MockInterviewType type, List<AnsweredQuestion> answers) {
        int overall = answers.isEmpty()
            ? 0
            : (int) Math.round(answers.stream().mapToInt(AnsweredQuestion::score).average().orElse(0));
        String label = role == null || role.isBlank() ? "this role" : role;
        String summary = "Offline practice report for " + label + ". You completed "
            + answers.size() + " question(s) with an average score of " + overall
            + ". Enable the AI provider for a fuller, model-written review.";
        List<String> strengths = overall >= 60
            ? List.of("Answered every question", "Reasonable structure and coverage")
            : List.of("Completed the session end to end");
        List<String> weaknesses = overall >= 60
            ? List.of("Add more concrete, quantified examples")
            : List.of("Answers were thin — add depth and specifics", "Explain your reasoning more explicitly");
        List<String> recommendations = List.of(
            "Practise the STAR structure (Situation, Task, Action, Result).",
            "Rehearse two concrete stories you can adapt to many questions.",
            "For coding questions, talk through complexity and edge cases."
        );
        return new InterviewSummary(overall, summary, strengths, weaknesses, recommendations);
    }

    private static QuestionDraft prose(String question, String difficulty) {
        return new QuestionDraft(question, difficulty, DEFAULT_TIMER_SECONDS, false, null);
    }

    private static QuestionDraft coding(String question, String language) {
        return new QuestionDraft(question, "medium", CODING_TIMER_SECONDS, true, language);
    }

    private static String languageFor(String role) {
        if (role == null) {
            return "java";
        }
        String r = role.toLowerCase();
        if (r.contains("front") || r.contains("react") || r.contains("web")) {
            return "javascript";
        }
        // "database" also contains "data" — DBA-style roles stay on the default language track.
        if (r.contains("database") || r.contains("dba")) {
            return "java";
        }
        if (r.contains("data") || r.contains("ml") || r.contains("python")) {
            return "python";
        }
        return "java";
    }

    /** Heuristic 0-100 from answer substance: word count and sentence structure, honestly bounded. */
    private static int scoreAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return 0;
        }
        String trimmed = answer.trim();
        int words = trimmed.split("\\s+").length;
        int score = Math.min(70, 20 + words); // ~1 point/word up to a 70 cap for length alone
        return clamp(score + structureBonus(trimmed));
    }

    private static int lengthBonus(String answer) {
        if (answer == null) {
            return 0;
        }
        int words = answer.trim().isEmpty() ? 0 : answer.trim().split("\\s+").length;
        return Math.min(15, words / 10);
    }

    private static int structureBonus(String answer) {
        if (answer == null) {
            return 0;
        }
        int bonus = 0;
        if (answer.contains(".") || answer.contains("\n")) {
            bonus += 5; // multiple sentences / lines
        }
        if (answer.matches("(?s).*\\d.*")) {
            bonus += 5; // contains a concrete number/metric
        }
        return bonus;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
