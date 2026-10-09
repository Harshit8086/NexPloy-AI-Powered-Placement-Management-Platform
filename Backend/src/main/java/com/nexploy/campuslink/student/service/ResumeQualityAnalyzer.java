package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse;
import com.nexploy.campuslink.student.dto.ResumeQualityAnalysis;
import com.nexploy.campuslink.student.dto.ResumeQualityAnalysis.Dimension;
import com.nexploy.campuslink.student.dto.ResumeQualityAnalysis.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * §10 Resume Quality Analyzer — deterministic, rule-based diagnosis of an uploaded resume's quality.
 * It produces the {@link ResumeQualityAnalysis} nested section of the resume analysis: eight quality
 * dimensions, each with a status and evidence-backed findings, plus a short "Top Improvements" list.
 *
 * <p><strong>Authoritative and grounded, never delegated to the model.</strong> Like the score and
 * missing-keyword list, every quality finding is a transparent function of what the resume actually
 * contains, so it is unit-testable, needs no network, and can never fabricate evidence, metrics,
 * skills, employers or certifications. Absence is reported as absence ("not demonstrated in the
 * resume"), never as the student lacking a skill. Where the extracted text does not contain enough to
 * judge a dimension, that dimension is {@code NOT_ENOUGH_EVIDENCE} rather than guessed.
 *
 * <p>It reuses the already-computed {@link ResumeAnalysisResponse} baseline (skill-match table,
 * missing keywords) for the skills-evidence and target-role dimensions so the two views stay
 * consistent, and derives structure / content / project / experience / quantification / readability
 * signals from the raw resume text. Findings are capped per dimension and overall to keep the
 * diagnosis actionable (§8) rather than a wall of 30 nitpicks.
 */
@Component
public class ResumeQualityAnalyzer {

    /** Severity vocabulary, low to high. Mirrored by the frontend badge rendering. */
    static final String INFO = "INFO";
    static final String LOW = "LOW";
    static final String MEDIUM = "MEDIUM";
    static final String HIGH = "HIGH";

    /** Dimension status vocabulary. */
    static final String GOOD = "GOOD";
    static final String NEEDS_ATTENTION = "NEEDS_ATTENTION";
    static final String NOT_ENOUGH_EVIDENCE = "NOT_ENOUGH_EVIDENCE";

    /** Max findings surfaced per dimension, and max Top Improvements — keeps the diagnosis actionable. */
    private static final int MAX_PER_DIMENSION = 4;
    private static final int MAX_TOP_IMPROVEMENTS = 5;

    /** Canonical resume section -> phrases that signal its presence (case-insensitive, substring). */
    private static final Map<String, List<String>> SECTIONS = new LinkedHashMap<>();

    static {
        SECTIONS.put("summary", List.of("summary", "objective", "profile", "about me"));
        SECTIONS.put("education", List.of(
            "education", "b.tech", "b tech", "bachelor", "b.e.", "b.sc", "university", "college", "degree"));
        SECTIONS.put("skills", List.of("skills", "technical skills", "technologies", "tech stack"));
        SECTIONS.put("experience", List.of(
            "experience", "employment", "work history", "internship", "intern"));
        SECTIONS.put("projects", List.of("projects", "personal projects", "academic projects"));
        SECTIONS.put("certifications", List.of("certification", "certificate", "licenses"));
        SECTIONS.put("achievements", List.of("achievements", "awards", "honors", "honours"));
    }

    /** Low-signal filler phrases that describe no concrete accomplishment. */
    private static final List<String> GENERIC_PHRASES = List.of(
        "hard working", "hard-working", "team player", "detail oriented", "detail-oriented",
        "passionate", "self motivated", "self-motivated", "go-getter", "results-driven",
        "responsible for", "duties included", "worked on various", "good communication");

    /** Strong action verbs used to judge whether experience/projects read as achievements. */
    private static final List<String> ACTION_VERBS = List.of(
        "built", "designed", "developed", "implemented", "created", "led", "architected",
        "optimized", "optimised", "reduced", "improved", "increased", "launched", "shipped",
        "automated", "engineered", "delivered", "migrated", "integrated", "deployed", "scaled");

    /** Technology tokens used to judge whether a project states what it was built with. */
    private static final List<String> TECH_TOKENS = List.of(
        "java", "python", "javascript", "typescript", "react", "angular", "vue", "spring",
        "node", "express", "django", "flask", "sql", "postgresql", "mysql", "mongodb", "redis",
        "docker", "kubernetes", "aws", "gcp", "azure", "kafka", "rest", "graphql", "git");

    private static final Pattern QUANTIFIED = Pattern.compile(
        "(reduced|improved|increased|decreased|optimi[sz]ed|boosted|cut|accelerated|grew|scaled|saved)"
            + "\\b[^.]{0,40}?\\d|\\d+\\s?%|\\d+\\s?(ms|rps|qps|k\\s?users|users|requests|x\\b)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern LINK = Pattern.compile(
        "github\\.com|gitlab\\.com|linkedin\\.com|bitbucket\\.org|https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s-]{7,}\\d)");

    /**
     * Produces the quality analysis for one resume. {@code resumeText} is the untrusted, already
     * screened + parsed resume text; {@code baseline} is the deterministic structured analysis (used
     * for the skills-evidence and target-role dimensions so both views agree); {@code targetRole} may
     * be blank.
     */
    public ResumeQualityAnalysis analyze(String resumeText, String targetRole, ResumeAnalysisResponse baseline) {
        String original = resumeText == null ? "" : resumeText;
        String lower = original.toLowerCase(Locale.ROOT);
        List<String> lines = splitLines(original);
        boolean hasBody = lower.strip().length() >= 80;

        Dimension structure = structure(original, lower);
        Dimension contentQuality = contentQuality(original, lines, hasBody);
        Dimension projectQuality = projectQuality(original, lines);
        Dimension experienceQuality = experienceQuality(original, lines);
        Dimension quantification = quantification(original, lines);
        Dimension skillsEvidence = skillsEvidence(baseline, lines);
        Dimension targetRoleRelevance = targetRoleRelevance(baseline, targetRole, hasBody);
        Dimension readability = readability(original, lines, hasBody);

        List<Finding> top = topImprovements(List.of(
            structure, contentQuality, projectQuality, experienceQuality,
            quantification, skillsEvidence, targetRoleRelevance, readability));

        return new ResumeQualityAnalysis(
            structure, contentQuality, projectQuality, experienceQuality,
            quantification, skillsEvidence, targetRoleRelevance, readability, top);
    }

    // ---- Dimension A: Structure -------------------------------------------------------------

    private Dimension structure(String original, String lower) {
        if (lower.strip().isEmpty()) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        List<Finding> findings = new ArrayList<>();
        boolean hasContact = EMAIL.matcher(original).find() || LINK.matcher(original).find()
            || PHONE.matcher(original).find();
        if (!hasContact) {
            findings.add(new Finding("Structure",
                "No contact details (email, phone or profile link) were found.",
                "No email address, phone number or GitHub/LinkedIn/portfolio link is present in the text.",
                "Add a header with your email and at least one professional link so reviewers can reach you.",
                MEDIUM));
        }
        if (!hasSection(lower, "education")) {
            findings.add(new Finding("Structure",
                "No Education section was detected.",
                "No education, degree or university heading appears in the resume text.",
                "Add an Education section with your degree, institution and graduation year.",
                MEDIUM));
        }
        if (!hasSection(lower, "skills")) {
            findings.add(new Finding("Structure",
                "No Skills section was detected.",
                "No skills/technologies heading appears in the resume text.",
                "Add a Skills section grouping the tools and languages you actually use.",
                LOW));
        }
        boolean hasProjects = hasSection(lower, "projects");
        boolean hasExperience = hasSection(lower, "experience");
        if (!hasProjects && !hasExperience) {
            findings.add(new Finding("Structure",
                "Neither a Projects nor an Experience section was detected.",
                "No projects, experience, internship or employment heading appears in the resume text.",
                "Add a Projects or Experience section — this is where recruiters look for evidence of ability.",
                HIGH));
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Dimension B: Content Quality -------------------------------------------------------

    private Dimension contentQuality(String original, List<String> lines, boolean hasBody) {
        if (!hasBody) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        List<Finding> findings = new ArrayList<>();
        String genericLine = firstLineContainingAny(lines, GENERIC_PHRASES);
        if (genericLine != null) {
            findings.add(new Finding("Content Quality",
                "Generic, low-signal phrasing weakens your descriptions.",
                clip(genericLine),
                "Replace clichés like \"hard working\" or \"responsible for\" with a specific, evidenced accomplishment.",
                LOW));
        }
        String repeated = mostRepeatedWord(lines);
        if (repeated != null) {
            findings.add(new Finding("Content Quality",
                "The word \"" + repeated + "\" is repeated heavily across bullets.",
                "\"" + repeated + "\" appears many times in the resume text.",
                "Vary your verbs so each bullet highlights a distinct contribution.",
                INFO));
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Dimension C: Project Quality -------------------------------------------------------

    private Dimension projectQuality(String original, List<String> lines) {
        String block = blockUnderSection(lines, "projects");
        if (block.isBlank()) {
            // No projects section to judge — reported honestly, not guessed.
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        String blockLower = block.toLowerCase(Locale.ROOT);
        List<Finding> findings = new ArrayList<>();
        if (!containsAny(blockLower, TECH_TOKENS)) {
            findings.add(new Finding("Project Quality",
                "Projects don't clearly state the technologies used.",
                clip(firstNonHeaderLine(block)),
                "For each project, name the languages, frameworks and tools you built it with.",
                MEDIUM));
        }
        if (!containsAny(blockLower, ACTION_VERBS)) {
            findings.add(new Finding("Project Quality",
                "Your specific contribution to each project isn't clearly described.",
                clip(firstNonHeaderLine(block)),
                "Start each project bullet with an action verb describing what you personally built or designed.",
                MEDIUM));
        }
        if (!QUANTIFIED.matcher(blockLower).find()) {
            findings.add(new Finding("Project Quality",
                "Projects describe scope but no measurable outcome.",
                "No metric (%, latency, throughput, users) appears in the projects section.",
                "Add a measurable result only where one genuinely exists — never invent numbers.",
                INFO));
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Dimension D: Experience Quality ----------------------------------------------------

    private Dimension experienceQuality(String original, List<String> lines) {
        String block = blockUnderSection(lines, "experience");
        if (block.isBlank()) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        String blockLower = block.toLowerCase(Locale.ROOT);
        List<Finding> findings = new ArrayList<>();
        if (!containsAny(blockLower, ACTION_VERBS)) {
            findings.add(new Finding("Experience Quality",
                "Experience reads as a list of duties rather than achievements.",
                clip(firstNonHeaderLine(block)),
                "Lead each bullet with an action verb and state what changed as a result of your work.",
                MEDIUM));
        }
        if (!QUANTIFIED.matcher(blockLower).find()) {
            findings.add(new Finding("Experience Quality",
                "Experience bullets state responsibilities without measurable results.",
                "No metric or outcome figure appears in the experience section.",
                "Where a real result exists, quantify it (e.g. time saved, volume handled) — do not fabricate figures.",
                LOW));
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Dimension E: Quantification --------------------------------------------------------

    private Dimension quantification(String original, List<String> lines) {
        if (original.isBlank()) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        List<Finding> findings = new ArrayList<>();
        String quantifiedLine = firstLineMatching(lines, QUANTIFIED);
        if (quantifiedLine != null) {
            findings.add(new Finding("Quantification",
                "Measurable outcomes are present — this is a strength.",
                clip(quantifiedLine),
                "Keep quantifying results this way across the rest of the resume where real numbers exist.",
                INFO));
        } else {
            findings.add(new Finding("Quantification",
                "No measurable outcomes were found anywhere in the resume.",
                "No percentages, latency, throughput or other result figures appear in the text.",
                "Add measurable results only where real evidence exists. Never invent numbers.",
                LOW));
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Dimension F: Skills Evidence -------------------------------------------------------

    private Dimension skillsEvidence(ResumeAnalysisResponse baseline, List<String> lines) {
        List<ResumeAnalysisResponse.SkillMatch> skillMatch =
            baseline == null || baseline.skillMatch() == null ? List.of() : baseline.skillMatch();
        List<ResumeAnalysisResponse.ExtractedCategory> extracted =
            baseline == null || baseline.extracted() == null ? List.of() : baseline.extracted();
        if (skillMatch.isEmpty() && extracted.isEmpty()) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        List<Finding> findings = new ArrayList<>();

        // Consistency: derive both the positives and the concern from the same signal — whether a
        // skill appears OUTSIDE the skills list. This avoids praising and flagging the same term.
        String skillsBlockLower = blockUnderSection(lines, "skills").toLowerCase(Locale.ROOT);
        String nonSkillsLower = removeOnce(joinLower(lines), skillsBlockLower);

        // Positive, grounded: role skills demonstrated in an actual project/experience line, not just listed.
        skillMatch.stream()
            .filter(sm -> "DEMONSTRATED".equals(sm.status()))
            .filter(sm -> nonSkillsLower.contains(sm.requiredSkill().toLowerCase(Locale.ROOT)))
            .limit(2)
            .forEach(sm -> findings.add(new Finding("Skills Evidence",
                sm.requiredSkill() + " is demonstrated in your resume.",
                clip(sm.resumeEvidence()),
                "Keep this evidence prominent — demonstrated skills carry far more weight than a bare list.",
                INFO)));

        // Concern: a listed skill that never appears outside the skills section is unsupported.
        int added = 0;
        for (ResumeAnalysisResponse.ExtractedCategory cat : extracted) {
            for (ResumeAnalysisResponse.ExtractedCategory.Item item : cat.items()) {
                if (added >= 2) break;
                String term = item.value().toLowerCase(Locale.ROOT);
                if (!skillsBlockLower.isBlank() && skillsBlockLower.contains(term) && !nonSkillsLower.contains(term)) {
                    findings.add(new Finding("Skills Evidence",
                        item.value() + " is listed but not demonstrated elsewhere in the resume.",
                        "\"" + item.value() + "\" appears in your skills list but in no project or experience bullet.",
                        "Back " + item.value() + " with a project or experience bullet, or move it lower — this is "
                            + "about evidence in the resume, not whether you know it.",
                        LOW));
                    added++;
                }
            }
        }
        if (findings.isEmpty()) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Dimension G: Target Role Relevance -------------------------------------------------

    private Dimension targetRoleRelevance(ResumeAnalysisResponse baseline, String targetRole, boolean hasBody) {
        if (targetRole == null || targetRole.isBlank() || !hasBody) {
            // No target role, or no resume body to compare against — judged honestly as insufficient.
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        List<ResumeAnalysisResponse.SkillMatch> skillMatch =
            baseline == null || baseline.skillMatch() == null ? List.of() : baseline.skillMatch();
        List<String> missing =
            baseline == null || baseline.missingKeywords() == null ? List.of() : baseline.missingKeywords();
        List<Finding> findings = new ArrayList<>();

        skillMatch.stream()
            .filter(sm -> "DEMONSTRATED".equals(sm.status()))
            .limit(2)
            .forEach(sm -> findings.add(new Finding("Target Role Relevance",
                sm.requiredSkill() + " is relevant to " + targetRole + " and is demonstrated.",
                clip(sm.resumeEvidence()),
                "This content is well targeted — keep it near the top.",
                INFO)));

        if (!missing.isEmpty()) {
            findings.add(new Finding("Target Role Relevance",
                "Some skills expected for " + targetRole + " are not yet evident: " + String.join(", ", missing) + ".",
                "These role-relevant terms were not found in the resume text.",
                "Add resume evidence for these where you have it — this reflects the resume, not your ability.",
                missing.size() >= 3 ? MEDIUM : LOW));
        }
        if (findings.isEmpty()) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Dimension H: Readability / ATS -----------------------------------------------------

    private Dimension readability(String original, List<String> lines, boolean hasBody) {
        if (!hasBody) {
            return new Dimension(NOT_ENOUGH_EVIDENCE, List.of());
        }
        List<Finding> findings = new ArrayList<>();
        String longLine = lines.stream().map(String::strip).filter(l -> l.length() > 220).findFirst().orElse(null);
        if (longLine != null) {
            findings.add(new Finding("Readability",
                "Potential readability concern: a very long line or bullet.",
                clip(longLine),
                "Break long bullets into concise, single-idea lines that scan quickly.",
                LOW));
        }
        String repeatedLine = firstDuplicateLine(lines);
        if (repeatedLine != null) {
            findings.add(new Finding("Readability",
                "Potential readability concern: repeated content.",
                clip(repeatedLine),
                "Remove the duplicated line so each point appears once.",
                LOW));
        }
        long sectionCount = SECTIONS.keySet().stream()
            .filter(k -> hasSection(original.toLowerCase(Locale.ROOT), k)).count();
        if (sectionCount < 2) {
            findings.add(new Finding("Readability",
                "Potential ATS parsing concern: few clear section headings were detected.",
                "Fewer than two standard section headings (e.g. Education, Skills, Projects) were found.",
                "Use clear, conventional section headings so automated parsers can segment your resume.",
                INFO));
        }
        return new Dimension(statusFor(findings, true), cap(findings));
    }

    // ---- Status / capping / ranking ---------------------------------------------------------

    /** A dimension needs attention iff it carries any MEDIUM/HIGH finding; empty is GOOD. */
    private String statusFor(List<Finding> findings, boolean judged) {
        if (!judged || findings.isEmpty()) {
            return GOOD;
        }
        boolean material = findings.stream().anyMatch(f -> MEDIUM.equals(f.severity()) || HIGH.equals(f.severity()));
        return material ? NEEDS_ATTENTION : GOOD;
    }

    /** Keeps the most severe findings, capped per dimension so the diagnosis stays actionable (§8). */
    private List<Finding> cap(List<Finding> findings) {
        return findings.stream()
            .sorted(Comparator.comparingInt((Finding f) -> severityRank(f.severity())).reversed())
            .limit(MAX_PER_DIMENSION)
            .toList();
    }

    /**
     * The cross-dimension "Top Improvements" list: the most severe findings overall, de-duplicated by
     * issue and capped, with lower-severity items backfilled only to reach a useful minimum.
     */
    private List<Finding> topImprovements(List<Dimension> dimensions) {
        List<Finding> all = new ArrayList<>();
        for (Dimension d : dimensions) {
            if (d != null && d.findings() != null) {
                all.addAll(d.findings());
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Finding> ordered = all.stream()
            .sorted(Comparator.comparingInt((Finding f) -> severityRank(f.severity())).reversed())
            .toList();

        List<Finding> priority = new ArrayList<>();
        for (Finding f : ordered) {
            if (severityRank(f.severity()) >= severityRank(MEDIUM) && seen.add(f.issue())) {
                priority.add(f);
            }
        }
        // Backfill with lower-severity, actionable items only if we have very few priority ones.
        if (priority.size() < 3) {
            for (Finding f : ordered) {
                if (priority.size() >= 3) break;
                if (!INFO.equals(f.severity()) && seen.add(f.issue())) {
                    priority.add(f);
                }
            }
        }
        return priority.stream().limit(MAX_TOP_IMPROVEMENTS).toList();
    }

    private int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            default -> 0;
        };
    }

    // ---- Section / line helpers -------------------------------------------------------------

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            out.add(line);
        }
        return out;
    }

    /** True if the whole (lower-cased) resume text mentions any phrase that signals a section. */
    private boolean hasSection(String lower, String key) {
        List<String> phrases = SECTIONS.getOrDefault(key, List.of());
        return phrases.stream().anyMatch(lower::contains);
    }

    /** The section key a short heading-like line represents, or {@code null} if it isn't a heading. */
    private String headerKeyOf(String line) {
        String lower = line == null ? "" : line.strip().toLowerCase(Locale.ROOT);
        if (lower.isEmpty() || lower.length() > 60) {
            return null;
        }
        for (Map.Entry<String, List<String>> e : SECTIONS.entrySet()) {
            for (String phrase : e.getValue()) {
                if (lower.contains(phrase)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** The text between a section's heading and the next recognized heading (may be blank). */
    private String blockUnderSection(List<String> lines, String key) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (key.equals(headerKeyOf(lines.get(i)))) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // Inline heading form ("Skills: Java, Docker, …"): the content after the colon is part of the block.
        String headerLine = lines.get(start);
        int colon = headerLine.indexOf(':');
        if (colon >= 0 && colon + 1 < headerLine.length()) {
            String inline = headerLine.substring(colon + 1).strip();
            if (!inline.isEmpty()) {
                sb.append(inline).append('\n');
            }
        }
        for (int j = start + 1; j < lines.size(); j++) {
            String hk = headerKeyOf(lines.get(j));
            if (hk != null && !hk.equals(key)) {
                break;
            }
            sb.append(lines.get(j)).append('\n');
        }
        return sb.toString();
    }

    private String firstNonHeaderLine(String block) {
        for (String line : splitLines(block)) {
            if (!line.isBlank() && headerKeyOf(line) == null) {
                return line.strip();
            }
        }
        return block == null ? "" : block.strip();
    }

    private String firstLineContainingAny(List<String> lines, List<String> phrases) {
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (!line.isBlank() && phrases.stream().anyMatch(lower::contains)) {
                return line.strip();
            }
        }
        return null;
    }

    private String firstLineMatching(List<String> lines, Pattern pattern) {
        for (String line : lines) {
            if (!line.isBlank() && pattern.matcher(line).find()) {
                return line.strip();
            }
        }
        return null;
    }

    /** The first line (length > 30) that appears more than once — a readability duplicate. */
    private String firstDuplicateLine(List<String> lines) {
        Set<String> seen = new LinkedHashSet<>();
        for (String line : lines) {
            String norm = line.strip().toLowerCase(Locale.ROOT);
            if (norm.length() > 30 && !seen.add(norm)) {
                return line.strip();
            }
        }
        return null;
    }

    /** The most-repeated content word (length ≥ 5) if it recurs heavily, else {@code null}. */
    private String mostRepeatedWord(List<String> lines) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String line : lines) {
            for (String raw : line.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                if (raw.length() >= 5) {
                    counts.merge(raw, 1, Integer::sum);
                }
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return bestCount >= 5 ? best : null;
    }

    private boolean containsAny(String text, List<String> tokens) {
        return tokens.stream().anyMatch(text::contains);
    }

    private String removeOnce(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return haystack;
        }
        int idx = haystack.indexOf(needle);
        if (idx < 0) {
            return haystack;
        }
        return haystack.substring(0, idx) + " " + haystack.substring(idx + needle.length());
    }

    private String joinLower(List<String> lines) {
        return String.join("\n", lines).toLowerCase(Locale.ROOT);
    }

    /** Trims evidence to a short, quotable snippet so the UI never shows a wall of text. */
    private String clip(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() > 160 ? t.substring(0, 157) + "..." : t;
    }
}
