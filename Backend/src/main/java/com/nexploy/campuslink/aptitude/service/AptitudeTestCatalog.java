package com.nexploy.campuslink.aptitude.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The fixed, code-level catalog of official aptitude tests. Test shape (category, question count,
 * duration) lives here, not in the database — the question bank only decides <em>availability</em>
 * (enough active bank questions to draw the test). Keeping shape in source control makes
 * test-shape drift impossible without a code change.
 */
@Component
public class AptitudeTestCatalog {

    /** Practice-lane attempts carry this literal test id; it is not a catalog entry. */
    public static final String PRACTICE_TEST_ID = "PRACTICE";

    /** Retry-of-missed-questions attempts carry this literal test id; it is not a catalog entry. */
    public static final String RETRY_TEST_ID = "RETRY";

    /** The four bank categories, in display order. */
    public static final List<String> CATEGORIES =
        List.of("QUANTITATIVE", "LOGICAL", "VERBAL", "DATA_INTERPRETATION");

    /** Questions drawn per category for the mixed grand test. */
    private static final int MIXED_PER_CATEGORY = 10;

    /**
     * One official test. {@code category} is null for the mixed grand test, which draws from all
     * four categories.
     */
    public record TestDefinition(
        String testId,
        String title,
        String category,
        int questionCount,
        int durationSeconds,
        String description) {}

    private final List<TestDefinition> tests = List.of(
        new TestDefinition("QUANTITATIVE", "Quantitative Aptitude", "QUANTITATIVE", 15, 900,
            "Numbers, percentages, ratios, time-speed-distance and arithmetic — the core of any "
                + "aptitude screen. 15 questions drawn from the curated bank."),
        new TestDefinition("LOGICAL", "Logical Reasoning", "LOGICAL", 15, 900,
            "Series, syllogisms, seating and ordering puzzles. 15 questions drawn from the curated "
                + "bank."),
        new TestDefinition("VERBAL", "Verbal Ability", "VERBAL", 15, 900,
            "Grammar, vocabulary, sentence completion and short comprehension. 15 questions drawn "
                + "from the curated bank."),
        new TestDefinition("DATA_INTERPRETATION", "Data Interpretation", "DATA_INTERPRETATION", 15, 900,
            "Tables, charts and ratios under time pressure. 15 questions drawn from the curated "
                + "bank."),
        new TestDefinition("MIXED_GRAND", "Grand Mock Test", null, 40, 2400,
            "A full-length mock: 10 questions from each of the four categories, 40 minutes. The "
                + "closest simulation of a real placement aptitude round."));

    /** All catalog tests, in display order. */
    public List<TestDefinition> all() {
        return tests;
    }

    /** Catalog test by id; empty for unknown ids and for the practice lane's literal id. */
    public Optional<TestDefinition> byId(String testId) {
        return tests.stream().filter(test -> test.testId().equals(testId)).findFirst();
    }

    /**
     * Active bank questions needed per category to draw this test. Single-category tests need their
     * full count from one category; the mixed grand test needs {@value #MIXED_PER_CATEGORY} from
     * each of the four.
     */
    public Map<String, Integer> requiredByCategory(TestDefinition test) {
        Map<String, Integer> required = new LinkedHashMap<>();
        if (test.category() != null) {
            required.put(test.category(), test.questionCount());
        } else {
            CATEGORIES.forEach(category -> required.put(category, MIXED_PER_CATEGORY));
        }
        return required;
    }
}
