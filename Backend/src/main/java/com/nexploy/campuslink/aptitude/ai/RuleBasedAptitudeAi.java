package com.nexploy.campuslink.aptitude.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline default adapter for {@link AptitudeAi}. Practice questions come from a
 * curated, hand-verified MCQ bank per category (cycled when more are requested than curated), and
 * the analysis is a transparent threshold over the attempt's deterministic section totals — never
 * an external call.
 *
 * <p>This is the active adapter unless {@code nexploy.ai.provider=llm}, in which case {@link
 * ChatClientAptitudeAi} takes over. Keeping it the default means the app boots and the test suite
 * runs with no API key, and it stays the inline fallback when the LLM adapter degrades. The two
 * adapters are mutually exclusive by that property, so exactly one {@code AptitudeAi} bean exists.
 */
@Component
@ConditionalOnProperty(name = "nexploy.ai.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedAptitudeAi implements AptitudeAi {

    private static final Map<String, List<AptitudeQuestionDraft>> BANKS = Map.of(
        "QUANTITATIVE", List.of(
            draft("A train 150 m long crosses a pole in 15 seconds. What is its speed?",
                "36 km/h", "40 km/h", "45 km/h", "50 km/h", "A",
                "Speed = 150/15 = 10 m/s = 10 × 18/5 = 36 km/h."),
            draft("What is 25% of 480?",
                "100", "110", "120", "130", "C",
                "25% is one quarter, and 480/4 = 120."),
            draft("The average of 5 consecutive odd numbers is 27. What is the largest of them?",
                "29", "31", "33", "35", "B",
                "The middle (third) number equals the average, 27; the largest is 27 + 4 = 31."),
            draft("A can finish a job in 12 days and B in 24 days. Working together, how long do they take?",
                "6 days", "8 days", "9 days", "10 days", "B",
                "Combined rate = 1/12 + 1/24 = 3/24 = 1/8, so 8 days."),
            draft("The simple interest on ₹2000 at 5% per annum for 3 years is:",
                "₹150", "₹250", "₹300", "₹350", "C",
                "SI = 2000 × 5 × 3 / 100 = ₹300."),
            draft("If x + y = 10 and x − y = 4, what is x × y?",
                "21", "24", "25", "28", "A",
                "Adding gives 2x = 14, so x = 7 and y = 3; x × y = 21.")),
        "LOGICAL", List.of(
            draft("Find the next number in the series: 2, 6, 12, 20, 30, ?",
                "40", "42", "44", "46", "B",
                "Differences grow by 2 (4, 6, 8, 10, 12), so next is 30 + 12 = 42."),
            draft("If CAT is coded as 24, how is DOG coded? (A = 1, B = 2, ...)",
                "24", "26", "27", "28", "B",
                "CAT = 3 + 1 + 20 = 24, so DOG = 4 + 15 + 7 = 26."),
            draft("Pointing to a photograph, Ravi says, \"She is the daughter of my grandfather's only son.\" How is the girl related to Ravi?",
                "Mother", "Aunt", "Sister", "Cousin", "C",
                "The grandfather's only son is Ravi's father, so his daughter is Ravi's sister."),
            draft("Complete the series: AZ, BY, CX, ?",
                "DW", "EV", "DV", "EW", "A",
                "First letters advance (A, B, C, D) while second letters retreat (Z, Y, X, W): DW."),
            draft("In a row of 40 students, Amit is 7th from the left and Bala is 11th from the right. How many students sit between them?",
                "20", "21", "22", "23", "C",
                "40 − 7 − 11 = 22 students between them."),
            draft("\"All Bloops are Razzies and all Razzies are Lazzies\" — the conclusion \"All Bloops are Lazzies\" is:",
                "True", "False", "Uncertain", "Contradictory", "A",
                "Membership is transitive: Bloops ⊆ Razzies ⊆ Lazzies.")),
        "VERBAL", List.of(
            draft("Choose the word closest in meaning to CANDID:",
                "Secretive", "Frank", "Cautious", "Hostile", "B",
                "Candid means open and frank in expression."),
            draft("Choose the antonym of EXPAND:",
                "Grow", "Contract", "Extend", "Widen", "B",
                "To contract is to become smaller — the opposite of expand."),
            draft("Fill in the blank: She has been working here ___ 2015.",
                "since", "for", "from", "by", "A",
                "'Since' marks a starting point in time with the perfect tense."),
            draft("Identify the correctly spelled word:",
                "Accomodate", "Accommodate", "Acommodate", "Acomodate", "B",
                "Accommodate takes double c and double m."),
            draft("Choose the word closest in meaning to UBIQUITOUS:",
                "Rare", "Omnipresent", "Obsolete", "Insignificant", "B",
                "Ubiquitous means present everywhere at once."),
            draft("Fill in the blank: Neither of the candidates ___ suitable for the role.",
                "are", "were", "is", "have", "C",
                "'Neither' is singular, so the verb is singular: is.")),
        "DATA_INTERPRETATION", List.of(
            draft("A shop sold 120 units on Monday and 150 on Tuesday. What is the percentage increase?",
                "20%", "25%", "30%", "35%", "B",
                "Increase = 30 over a base of 120 → 30/120 = 25%."),
            draft("In a survey of 200 students, 40% chose science as their stream. How many chose science?",
                "60", "70", "80", "90", "C",
                "40% of 200 = 80."),
            draft("In a pie chart of expenses, rent occupies 90° of the 360° circle. What fraction of expenses is rent?",
                "1/2", "1/3", "1/4", "1/5", "C",
                "90/360 = 1/4 of the whole."),
            draft("Sales grew from ₹50 lakh to ₹65 lakh in a year. What is the growth rate?",
                "15%", "20%", "25%", "30%", "D",
                "Growth = 15 over a base of 50 → 15/50 = 30%."),
            draft("The average of 4 readings is 25. If one reading of 40 is removed, the new average is:",
                "20", "22", "24", "26", "A",
                "Total = 100; remaining = 60 over 3 readings → 20."),
            draft("In a class of 60, the ratio of boys to girls is 3:2. How many girls are there?",
                "20", "24", "30", "36", "B",
                "Girls = 2/5 of 60 = 24.")));

    @Override
    public List<AptitudeQuestionDraft> generatePracticeQuestions(String category, int count) {
        List<AptitudeQuestionDraft> bank =
            BANKS.getOrDefault(normalize(category), BANKS.get("QUANTITATIVE"));
        List<AptitudeQuestionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(bank.get(i % bank.size()));
        }
        return List.copyOf(drafts);
    }

    @Override
    public AptitudeAnalysis analyze(AptitudeAttemptStats stats) {
        int overallPercent = stats.totalQuestions() == 0
            ? 0
            : (int) Math.round(100.0 * stats.correctCount() / stats.totalQuestions());
        String summary = "Offline heuristic analysis. You answered " + stats.correctCount()
            + " of " + stats.totalQuestions() + " questions correctly (" + overallPercent
            + "%) in " + safeTitle(stats.testTitle()) + ". Enable the AI provider for a richer, "
            + "model-written review.";
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        for (AptitudeAttemptStats.CategoryStats section : stats.categories()) {
            int percent = section.total() == 0 ? 0 : 100 * section.correct() / section.total();
            if (percent >= 70) {
                strengths.add(label(section.category()) + ": " + percent + "% — a solid section for you.");
            } else if (percent < 50) {
                weaknesses.add(label(section.category()) + ": " + percent + "% — needs focused practice.");
            }
        }
        if (strengths.isEmpty()) {
            strengths.add("Completed the attempt end to end");
        }
        List<String> tips = List.of(
            "Revisit the explanations of every question you got wrong before retaking a test.",
            "Practise under the full time limit so pacing becomes automatic.",
            "Mark doubtful questions for review instead of stalling on them mid-test.");
        return new AptitudeAnalysis(summary, List.copyOf(strengths), List.copyOf(weaknesses), tips);
    }

    private static AptitudeQuestionDraft draft(String prompt, String a, String b, String c, String d,
                                               String correctOption, String explanation) {
        return new AptitudeQuestionDraft(prompt, a, b, c, d, correctOption, explanation);
    }

    private static String normalize(String category) {
        return category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
    }

    private static String label(String category) {
        String normalized = normalize(category);
        return normalized.isEmpty() ? "General" : normalized.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static String safeTitle(String title) {
        return title == null || title.isBlank() ? "the aptitude test" : title;
    }
}
