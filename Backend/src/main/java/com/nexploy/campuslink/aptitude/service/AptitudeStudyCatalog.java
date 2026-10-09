package com.nexploy.campuslink.aptitude.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The curated, deterministic source of <em>aptitude study guidance</em>: for each bank category,
 * concrete revision topics, hand-picked reputable resources and one actionable practice task.
 * Version-controlled, human-authored content — the AI never invents a topic, URL or provider, the
 * same grounding rule as {@link com.nexploy.campuslink.student.service.LearningResourceCatalog}.
 * Same category in -> same study guidance out.
 *
 * <p>Entries are validated at construction (fail-fast): every field non-blank, topics and
 * resources non-empty, every URL an {@code https://} link, and {@code type} drawn from the shared
 * resource-type vocabulary. Consumed by {@code AptitudeService#studyPlan}, which pairs these
 * entries with the student's real weak sections from a completed attempt — an uncatalogued
 * category is skipped, never fabricated.
 */
@Component
public class AptitudeStudyCatalog {

    /** Allowed resource kinds — the same closed vocabulary as the learning-resource catalog. */
    static final Set<String> TYPES =
        Set.of("COURSE", "DOCS", "VIDEO", "BOOK", "INTERACTIVE", "TUTORIAL", "CERTIFICATION");

    /** One curated study resource for a category. */
    public record StudyResource(String title, String provider, String url, String type) {}

    /**
     * Curated study guidance for one aptitude category: concrete topics to revise, hand-picked
     * resources, and one actionable practice task.
     */
    public record StudyEntry(
        String category,
        List<String> topics,
        List<StudyResource> resources,
        String practiceTask) {}

    private final Map<String, StudyEntry> byCategory;

    public AptitudeStudyCatalog() {
        this.byCategory = validateAndIndex(seed());
    }

    /** Curated study guidance for a category (case-insensitive); empty when none is curated. */
    public Optional<StudyEntry> entryFor(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCategory.get(category.toLowerCase(Locale.ROOT)));
    }

    // ---- Curated content (version-controlled; edit here, never generated) ------------------------
    private List<StudyEntry> seed() {
        return List.of(
            new StudyEntry("QUANTITATIVE",
                List.of("Percentages, profit & loss, simple and compound interest",
                    "Ratios, proportions and mixtures",
                    "Time, speed and distance (trains, boats)",
                    "Averages and number systems"),
                List.of(
                    new StudyResource("Quantitative Aptitude Questions & Answers", "IndiaBIX",
                        "https://www.indiabix.com/aptitude/questions-and-answers/", "INTERACTIVE"),
                    new StudyResource("Arithmetic", "Khan Academy",
                        "https://www.khanacademy.org/math/arithmetic", "COURSE")),
                "Time yourself on 20 mixed quantitative MCQs (percentages, ratios, time-speed-distance) "
                    + "in 20 minutes, then redo every wrong question without looking at the solution first."),
            new StudyEntry("LOGICAL",
                List.of("Number and alphabet series",
                    "Syllogisms and statement-based logic",
                    "Seating arrangement and ordering puzzles",
                    "Blood relations and direction sense"),
                List.of(
                    new StudyResource("Logical Reasoning Questions & Answers", "IndiaBIX",
                        "https://www.indiabix.com/logical-reasoning/questions-and-answers/", "INTERACTIVE"),
                    new StudyResource("Logical Reasoning Tutorial", "TutorialsPoint",
                        "https://www.tutorialspoint.com/logical_reasoning/index.htm", "TUTORIAL")),
                "Solve one seating-arrangement set, one syllogism set and 10 series questions; write the "
                    + "rule you used for each before checking the options."),
            new StudyEntry("VERBAL",
                List.of("Grammar: tenses, subject-verb agreement, articles",
                    "Vocabulary: synonyms, antonyms and word roots",
                    "Error spotting and sentence completion",
                    "Short reading-comprehension passages"),
                List.of(
                    new StudyResource("Verbal Ability Questions & Answers", "IndiaBIX",
                        "https://www.indiabix.com/verbal-ability/questions-and-answers/", "INTERACTIVE"),
                    new StudyResource("Grammar", "Khan Academy",
                        "https://www.khanacademy.org/humanities/grammar", "COURSE")),
                "Do 15 error-spotting and 10 sentence-completion questions; for every miss, write down "
                    + "the grammar rule or word root involved."),
            new StudyEntry("DATA_INTERPRETATION",
                List.of("Tables and missing-data puzzles",
                    "Bar, line and pie charts",
                    "Percentage and ratio calculations under time pressure"),
                List.of(
                    new StudyResource("Data Interpretation Questions & Answers", "IndiaBIX",
                        "https://www.indiabix.com/data-interpretation/questions-and-answers/", "INTERACTIVE"),
                    new StudyResource("Statistics & Probability", "Khan Academy",
                        "https://www.khanacademy.org/math/statistics-probability", "COURSE")),
                "Take two chart-based DI sets with a 3-minute-per-question cap; compute percentage change "
                    + "and ratios by hand before checking the options."));
    }

    // ---- Validation + indexing -------------------------------------------------------------------
    static Map<String, StudyEntry> validateAndIndex(List<StudyEntry> entries) {
        Map<String, StudyEntry> index = new LinkedHashMap<>();
        for (StudyEntry entry : entries) {
            requireText(entry.category(), "category", entry);
            requireText(entry.practiceTask(), "practiceTask", entry);
            if (entry.topics() == null || entry.topics().isEmpty()) {
                throw new IllegalStateException(
                    "Study entry topics must not be empty: " + entry.category());
            }
            for (String topic : entry.topics()) {
                requireText(topic, "topic", entry);
            }
            if (entry.resources() == null || entry.resources().isEmpty()) {
                throw new IllegalStateException(
                    "Study entry resources must not be empty: " + entry.category());
            }
            for (StudyResource resource : entry.resources()) {
                requireText(resource.title(), "resource title", entry);
                requireText(resource.provider(), "resource provider", entry);
                requireText(resource.url(), "resource url", entry);
                if (!resource.url().startsWith("https://")) {
                    throw new IllegalStateException("Study resource URL must be https: " + resource.url());
                }
                if (!TYPES.contains(resource.type())) {
                    throw new IllegalStateException("Unknown study resource type '" + resource.type()
                        + "' for " + resource.title());
                }
            }
            String key = entry.category().toLowerCase(Locale.ROOT);
            if (index.put(key, entry) != null) {
                throw new IllegalStateException(
                    "Duplicate study entry for category: " + entry.category());
            }
        }
        return index;
    }

    private static void requireText(String value, String field, StudyEntry entry) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Study entry " + field + " must not be blank: " + entry.category());
        }
    }
}