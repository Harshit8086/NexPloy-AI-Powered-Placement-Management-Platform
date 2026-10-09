package com.nexploy.campuslink.ai.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline default adapter for {@link AiExtractionClient}. It uses a curated skill
 * alias dictionary and qualification patterns to extract structured items with verbatim evidence
 * spans — no external LLM call.
 *
 * <p>This is the active adapter unless {@code nexploy.ai.provider=llm}, in which case
 * {@link ChatClientAiExtractionClient} takes over. Keeping it as the default means the app boots
 * and the test suite runs with no API key, and it remains the offline fallback. The two adapters
 * are mutually exclusive by that property, so exactly one {@code AiExtractionClient} bean exists.
 *
 * <p>It treats all input strictly as data: it only ever reads the text to locate known skill and
 * qualification mentions, and never interprets it as an instruction.
 */
@Component
@ConditionalOnProperty(name = "nexploy.ai.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedAiExtractionClient implements AiExtractionClient {

    private static final String PROVIDER = "rule-based";
    private static final String MODEL_VERSION = "extraction-baseline-2026-01";
    private static final String PROMPT_VERSION = "extraction-prompt-1";

    // Canonical skill name -> aliases matched case-insensitively as whole words.
    private static final Map<String, List<String>> SKILL_ALIASES = Map.ofEntries(
        Map.entry("Java", List.of("java")),
        Map.entry("Spring", List.of("spring", "spring boot", "springboot")),
        Map.entry("Python", List.of("python")),
        Map.entry("JavaScript", List.of("javascript", "js")),
        Map.entry("TypeScript", List.of("typescript", "ts")),
        Map.entry("React", List.of("react", "reactjs", "react.js")),
        Map.entry("SQL", List.of("sql", "postgresql", "postgres", "mysql")),
        Map.entry("Machine Learning", List.of("machine learning", "ml")),
        Map.entry("Docker", List.of("docker")),
        Map.entry("Kubernetes", List.of("kubernetes", "k8s")),
        Map.entry("AWS", List.of("aws", "amazon web services")),
        Map.entry("Git", List.of("git"))
    );

    // Canonical qualification -> aliases.
    private static final Map<String, List<String>> QUALIFICATION_ALIASES = Map.ofEntries(
        Map.entry("B.Tech", List.of("b.tech", "btech", "bachelor of technology")),
        Map.entry("B.E.", List.of("b.e.", "be", "bachelor of engineering")),
        Map.entry("M.Tech", List.of("m.tech", "mtech", "master of technology")),
        Map.entry("MCA", List.of("mca")),
        Map.entry("MBA", List.of("mba")),
        Map.entry("Ph.D.", List.of("ph.d.", "phd", "doctorate"))
    );

    @Override
    public ResumeExtraction extractResume(String resumeText) {
        return new ResumeExtraction(
            findItems(resumeText, SKILL_ALIASES),
            findItems(resumeText, QUALIFICATION_ALIASES),
            metadata()
        );
    }

    @Override
    public JobRequirements parseJobDescription(String jobDescriptionText) {
        // Split into a "required" and a "preferred" section when the text signals one, so a
        // "preferred/nice to have" skill is not reported as hard-required. Everything is still
        // just extraction — nothing here sets an eligibility rule.
        Section section = splitPreferred(jobDescriptionText);
        return new JobRequirements(
            findItems(section.required(), SKILL_ALIASES),
            findItems(section.preferred(), SKILL_ALIASES),
            findItems(jobDescriptionText, QUALIFICATION_ALIASES),
            metadata()
        );
    }

    @Override
    public ExtractionMetadata metadata() {
        return new ExtractionMetadata(PROVIDER, MODEL_VERSION, PROMPT_VERSION);
    }

    /**
     * Finds each canonical value whose alias appears as a whole word, capturing the verbatim
     * matched span as evidence. Order is stable (dictionary insertion order); each canonical
     * value is reported at most once.
     */
    private List<ExtractedItem> findItems(String text, Map<String, List<String>> dictionary) {
        List<ExtractedItem> items = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return items;
        }
        for (Map.Entry<String, List<String>> entry : dictionary.entrySet()) {
            String evidence = firstMatch(text, entry.getValue());
            if (evidence != null) {
                items.add(new ExtractedItem(entry.getKey(), evidence));
            }
        }
        return items;
    }

    /** Returns the verbatim matched substring for the first alias found, or null if none match. */
    private String firstMatch(String text, List<String> aliases) {
        String bestEvidence = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String alias : aliases) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(alias) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find() && matcher.start() < bestIndex) {
                bestIndex = matcher.start();
                bestEvidence = text.substring(matcher.start(), matcher.end());
            }
        }
        return bestEvidence;
    }

    private Section splitPreferred(String text) {
        if (text == null) {
            return new Section("", "");
        }
        Matcher matcher = Pattern.compile(
            "(preferred|nice to have|good to have|bonus|plus)\\s*:?",
            Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            return new Section(text.substring(0, matcher.start()), text.substring(matcher.start()));
        }
        return new Section(text, "");
    }

    private record Section(String required, String preferred) {
    }
}
