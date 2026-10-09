package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse.ActionPlan;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse.ExtractedCategory;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse.Metric;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse.ProjectSuggestion;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse.SkillMatch;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse.Weakness;
import com.nexploy.campuslink.student.dto.ResumeIntelligenceResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rule-based, stateless resume analyzer — the deterministic baseline for resume intelligence. Given
 * the raw text of an uploaded resume and the student's target role, it derives the score, strengths,
 * weaknesses, missing keywords, project suggestions and headline recommendation entirely from what
 * the resume actually contains; no two resumes get the same output unless they contain the same
 * signals. Nothing is persisted — the analysis is recomputed on every upload ("Nothing is stored").
 *
 * <p>This is the always-present fallback in the same seam pattern as the skill-gap narrator: when
 * {@code nexploy.ai.provider=llm} an optional {@link ResumeInsightWriter} may rewrite the prose
 * fields (weaknesses / projectSuggestions / recommendation) into richer grounded text, but the
 * {@code score} and {@code missingKeywords} computed here are authoritative and never delegated to
 * the model. The logic is deterministic (same text + role -> same result), so it is unit-testable
 * and needs no network.
 */
@Component
public class ResumeAnalyzer {

    /** Impact verbs that, when paired with a number, signal a quantified achievement. */
    private static final Pattern QUANTIFIED_IMPACT = Pattern.compile(
        "(reduced|improved|increased|decreased|optimi[sz]ed|boosted|cut|accelerated|grew|scaled|saved)\\b[^.]{0,40}?\\d",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENT_OR_LATENCY = Pattern.compile(
        "\\d+\\s?%|\\d+\\s?(ms|rps|qps|k users|users|requests)", Pattern.CASE_INSENSITIVE);

    private static final List<String> DEPLOYMENT_TERMS = List.of(
        "docker", "kubernetes", "k8s", "aws", "gcp", "azure", "ci/cd", "cicd", "jenkins",
        "github actions", "terraform", "deployed", "deployment", "cloud", "heroku", "netlify", "vercel");
    private static final List<String> TESTING_TERMS = List.of(
        "unit test", "junit", "pytest", "jest", "mocha", "cypress", "selenium", "test coverage",
        "coverage", "tdd", "integration test", "testing");
    private static final List<String> LINK_TERMS = List.of(
        "github.com", "gitlab.com", "bitbucket.org", "linkedin.com", "portfolio", "http://", "https://");

    /**
     * Category -> known terms, used to group extracted skills for the structured analysis. Detection
     * is presence-in-text only; a term absent from the resume is never reported. Ordered so the UI
     * renders categories consistently.
     */
    private static final Map<String, List<String>> CATEGORY_TERMS = new LinkedHashMap<>();

    static {
        CATEGORY_TERMS.put("Languages", List.of(
            "Java", "Python", "JavaScript", "TypeScript", "C++", "C#", "Go", "Kotlin", "Swift",
            "Ruby", "PHP", "Rust", "Scala"));
        CATEGORY_TERMS.put("Frameworks", List.of(
            "React", "Angular", "Vue", "Spring Boot", "Spring", "Django", "Flask", "Express",
            "Node.js", "Next.js", "FastAPI", ".NET"));
        CATEGORY_TERMS.put("Databases", List.of(
            "PostgreSQL", "MySQL", "MongoDB", "Redis", "SQLite", "Oracle", "Cassandra", "DynamoDB",
            "Elasticsearch", "SQL"));
        CATEGORY_TERMS.put("Cloud & DevOps", List.of(
            "AWS", "GCP", "Azure", "Docker", "Kubernetes", "Terraform", "Jenkins", "GitHub Actions",
            "CI/CD", "Ansible", "Helm"));
        CATEGORY_TERMS.put("Tools", List.of(
            "Git", "Jira", "Figma", "Postman", "Maven", "Gradle", "Webpack", "Linux"));
    }

    /**
     * Produces a per-resume analysis. {@code resumeText} is the untrusted, already-parsed resume
     * text; {@code targetRole} steers the role-keyword coverage (may be null/blank).
     */
    public ResumeIntelligenceResponse analyze(String resumeText, String targetRole) {
        String text = resumeText == null ? "" : resumeText.toLowerCase(Locale.ROOT);

        boolean hasImpact = QUANTIFIED_IMPACT.matcher(text).find() || PERCENT_OR_LATENCY.matcher(text).find();
        boolean hasDeployment = containsAny(text, DEPLOYMENT_TERMS);
        boolean hasTesting = containsAny(text, TESTING_TERMS);
        boolean hasLinks = containsAny(text, LINK_TERMS);

        Map<String, Boolean> roleCoverage = roleKeywordCoverage(text, targetRole);
        List<String> missingKeywords = roleCoverage.entrySet().stream()
            .filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
        long roleHits = roleCoverage.values().stream().filter(Boolean::booleanValue).count();
        double roleRatio = roleCoverage.isEmpty() ? 1.0 : (double) roleHits / roleCoverage.size();

        String role = (targetRole == null || targetRole.isBlank()) ? "your target role" : targetRole;

        List<String> strengths = buildStrengths(hasImpact, hasDeployment, hasTesting, hasLinks, roleCoverage);
        List<String> weaknesses = buildWeaknesses(hasImpact, hasDeployment, hasTesting, hasLinks, missingKeywords, role);
        List<String> projectSuggestions = buildSuggestions(hasImpact, hasDeployment, hasTesting, hasLinks, missingKeywords, role);
        String recommendation = buildRecommendation(hasImpact, hasDeployment, hasTesting, hasLinks, missingKeywords, role);

        int score = score(text, hasImpact, hasDeployment, hasTesting, hasLinks, roleRatio);

        return new ResumeIntelligenceResponse(score, strengths, weaknesses, missingKeywords, projectSuggestions, recommendation);
    }

    /**
     * Deep structured analysis of an uploaded resume. Reuses the same deterministic signals as
     * {@link #analyze}, but returns grounded, categorized detail: heuristic sub-metrics (each with a
     * stated basis), structured weaknesses with severity/evidence, a role skill-match table,
     * categorized extracted skills, richer project suggestions and a sequenced action plan. No value
     * is fabricated — every metric is a transparent function of detected signals, and every extracted
     * item is present in the text.
     */
    public ResumeAnalysisResponse analyzeStructured(String resumeText, String targetRole) {
        String original = resumeText == null ? "" : resumeText;
        String text = original.toLowerCase(Locale.ROOT);

        boolean hasImpact = QUANTIFIED_IMPACT.matcher(text).find() || PERCENT_OR_LATENCY.matcher(text).find();
        boolean hasDeployment = containsAny(text, DEPLOYMENT_TERMS);
        boolean hasTesting = containsAny(text, TESTING_TERMS);
        boolean hasLinks = containsAny(text, LINK_TERMS);

        Map<String, Boolean> roleCoverage = roleKeywordCoverage(text, targetRole);
        List<String> missingKeywords = roleCoverage.entrySet().stream()
            .filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
        long roleHits = roleCoverage.values().stream().filter(Boolean::booleanValue).count();
        double roleRatio = roleCoverage.isEmpty() ? 1.0 : (double) roleHits / roleCoverage.size();

        String role = (targetRole == null || targetRole.isBlank()) ? "your target role" : targetRole;
        int overall = score(text, hasImpact, hasDeployment, hasTesting, hasLinks, roleRatio);

        List<SkillMatch> skillMatch = buildSkillMatch(original, text, roleCoverage);
        List<Metric> metrics = buildMetrics(roleRatio, roleCoverage, hasImpact, hasDeployment, hasTesting, hasLinks);
        List<String> strengths = buildStrengths(hasImpact, hasDeployment, hasTesting, hasLinks, roleCoverage);
        List<Weakness> weaknesses = buildStructuredWeaknesses(
            hasImpact, hasDeployment, hasTesting, hasLinks, missingKeywords, role);
        List<ProjectSuggestion> suggestions = buildStructuredSuggestions(
            hasImpact, hasDeployment, hasTesting, hasLinks, missingKeywords, role);
        List<ExtractedCategory> extracted = buildExtractedCategories(original, text);
        String recommendation = buildRecommendation(hasImpact, hasDeployment, hasTesting, hasLinks, missingKeywords, role);
        ActionPlan actionPlan = buildActionPlan(hasImpact, hasDeployment, hasTesting, hasLinks, missingKeywords, role);

        // resumeQuality (§10) is overlaid by ResumeIntelligenceService via ResumeQualityAnalyzer once
        // the baseline exists; the stateless analyzer stays dependency-free (constructed with `new` in
        // unit tests), so it leaves the nested quality section null here.
        return new ResumeAnalysisResponse(
            overall, metrics, strengths, weaknesses, missingKeywords, suggestions,
            skillMatch, extracted, recommendation, actionPlan, null);
    }

    /** Heuristic sub-metrics. Each carries the basis it was derived from — never an unexplained number. */
    private List<Metric> buildMetrics(double roleRatio, Map<String, Boolean> roleCoverage,
                                      boolean impact, boolean deploy, boolean test, boolean links) {
        int total = roleCoverage.size();
        int hits = (int) roleCoverage.values().stream().filter(Boolean::booleanValue).count();
        int keywordPct = (int) Math.round(roleRatio * 100);
        // ATS-readiness heuristic: share of four structural signals (impact, deployment, testing, links).
        int atsSignals = (impact ? 1 : 0) + (deploy ? 1 : 0) + (test ? 1 : 0) + (links ? 1 : 0);
        int atsPct = atsSignals * 25;
        // Role match blends keyword coverage with structural quality (70/30).
        int roleMatchPct = (int) Math.round(keywordPct * 0.7 + atsPct * 0.3);

        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("roleMatch", "Role Match", roleMatchPct,
            "Blends role-keyword coverage (70%) with resume quality signals (30%)."));
        metrics.add(new Metric("keywordCoverage", "Keyword Coverage", keywordPct,
            hits + " of " + total + " role-relevant keywords found in the resume."));
        metrics.add(new Metric("skillMatch", "Skill Match", keywordPct,
            hits + " of " + total + " expected skills demonstrated in the resume text."));
        metrics.add(new Metric("ats", "ATS Readiness", atsPct,
            atsSignals + " of 4 structural signals present (impact, deployment, testing, links)."));
        return metrics;
    }

    /** Role-expected skills vs. the resume's own evidence. Never claims a skill the text does not show. */
    private List<SkillMatch> buildSkillMatch(String original, String lowerText, Map<String, Boolean> roleCoverage) {
        List<SkillMatch> out = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : roleCoverage.entrySet()) {
            String skill = entry.getKey();
            if (entry.getValue()) {
                String evidence = evidenceLine(original, skill);
                out.add(new SkillMatch(skill, evidence, "DEMONSTRATED"));
            } else {
                out.add(new SkillMatch(skill, "Not demonstrated in the resume text", "NOT_DEMONSTRATED"));
            }
        }
        return out;
    }


    private List<String> buildStrengths(boolean impact, boolean deploy, boolean test, boolean links,
                                        Map<String, Boolean> roleCoverage) {
        List<String> out = new ArrayList<>();
        if (impact) out.add("Quantified impact — your resume backs claims with measurable results.");
        if (deploy) out.add("Deployment or cloud experience is present.");
        if (test) out.add("Testing and code-quality practices are mentioned.");
        if (links) out.add("Links to your code or portfolio are included.");
        roleCoverage.entrySet().stream().filter(Map.Entry::getValue)
            .limit(3).forEach(e -> out.add("Covers a role-relevant skill: " + e.getKey() + "."));
        if (out.isEmpty()) {
            out.add("Your resume was read successfully — add the specifics below to strengthen it.");
        }
        return out.stream().limit(5).toList();
    }

    private List<String> buildWeaknesses(boolean impact, boolean deploy, boolean test, boolean links,
                                         List<String> missingKeywords, String role) {
        List<String> out = new ArrayList<>();
        if (!impact) out.add("Projects describe scope but not measurable impact — no metrics like %, latency, or user counts.");
        if (!deploy) out.add("No deployment, cloud, or infrastructure experience is listed.");
        if (!test) out.add("Testing and code-quality practices (unit tests, coverage) are not mentioned.");
        if (!links) out.add("No links to your code (GitHub) or an online portfolio.");
        if (!missingKeywords.isEmpty()) {
            out.add("Missing " + role + " keywords: " + String.join(", ", missingKeywords) + ".");
        }
        if (out.isEmpty()) {
            out.add("No major gaps detected — refine wording and tailor to each posting.");
        }
        return out;
    }

    private List<String> buildSuggestions(boolean impact, boolean deploy, boolean test, boolean links,
                                          List<String> missingKeywords, String role) {
        List<String> out = new ArrayList<>();
        if (!impact) out.add("Add throughput, latency, or percentage numbers to quantify one existing project's impact.");
        if (!deploy) out.add("Deploy a project to a cloud provider (AWS/GCP/Azure) and document the architecture.");
        if (!test) out.add("Add automated tests to a project and state the coverage you reached.");
        if (!links) out.add("Publish your projects on GitHub and add the links to your resume.");
        if (!missingKeywords.isEmpty()) {
            out.add("Build a small project that demonstrates " + missingKeywords.get(0) + " for your " + role + " target.");
        }
        if (out.isEmpty()) {
            out.add("Your fundamentals are strong — tailor the wording of each project to the " + role + " you apply for.");
        }
        return out.stream().limit(4).toList();
    }

    private String buildRecommendation(boolean impact, boolean deploy, boolean test, boolean links,
                                       List<String> missingKeywords, String role) {
        String action;
        if (!impact) action = "add measurable impact (numbers, %, latency) to your projects";
        else if (!deploy) action = "show deployment or cloud experience";
        else if (!test) action = "mention testing and code-quality practices";
        else if (!links) action = "add links to your GitHub or portfolio";
        else if (!missingKeywords.isEmpty()) action = "cover the missing keyword \"" + missingKeywords.get(0) + "\"";
        else return "Your resume already covers impact, deployment, testing and links — tailor the wording to each "
            + role + " posting.";
        return "The highest-signal change for your " + role + " target is to " + action + ".";
    }

    private int score(String text, boolean impact, boolean deploy, boolean test, boolean links, double roleRatio) {
        int score = 30;
        if (impact) score += 15;
        if (deploy) score += 15;
        if (test) score += 12;
        if (links) score += 8;
        score += (int) Math.round(roleRatio * 20);
        // A resume with almost no content should not score as if it had signals.
        if (text.length() < 200) score = Math.min(score, 45);
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Role-relevant keywords for the target role, each mapped to whether the resume contains it.
     * The keyword set is chosen by matching well-known role families; an unknown role falls back to
     * broadly-relevant engineering keywords.
     */
    private Map<String, Boolean> roleKeywordCoverage(String text, String targetRole) {
        String role = targetRole == null ? "" : targetRole.toLowerCase(Locale.ROOT);
        List<String> keywords;
        // "database" also contains "data" — match the more specific family first.
        if (role.contains("database") || role.contains("dba")) {
            keywords = List.of("SQL", "Database Design", "Query Optimisation", "Data Modelling", "Docker");
        } else if (role.contains("data") || role.contains("ml") || role.contains("machine")) {
            keywords = List.of("Python", "SQL", "Pandas", "Machine Learning", "Statistics");
        } else if (role.contains("devops") || role.contains("sre") || role.contains("infra")) {
            keywords = List.of("Docker", "Kubernetes", "CI/CD", "AWS", "Terraform");
        } else if (role.contains("front")) {
            keywords = List.of("React", "TypeScript", "CSS", "Accessibility", "REST");
        } else if (role.contains("full")) {
            keywords = List.of("React", "REST API", "SQL", "Docker", "TypeScript");
        } else if (role.contains("back") || role.contains("java") || role.contains("server")) {
            keywords = List.of("REST API", "SQL", "Microservices", "Docker", "System Design");
        } else if (role.contains("mobile") || role.contains("android") || role.contains("ios")) {
            keywords = List.of("Kotlin", "Swift", "REST API", "State Management", "Testing");
        } else {
            keywords = List.of("Data Structures", "REST API", "SQL", "Git", "Testing");
        }
        Map<String, Boolean> coverage = new LinkedHashMap<>();
        for (String keyword : keywords) {
            coverage.put(keyword, text.contains(keyword.toLowerCase(Locale.ROOT)));
        }
        return coverage;
    }

    private List<Weakness> buildStructuredWeaknesses(boolean impact, boolean deploy, boolean test,
                                                     boolean links, List<String> missingKeywords, String role) {
        List<Weakness> out = new ArrayList<>();
        if (!impact) {
            out.add(new Weakness(
                "Projects lack quantified impact",
                "No metrics such as %, latency, throughput or user counts were found.",
                "Recruiters and ATS scans favour measurable outcomes over responsibilities for " + role + ".",
                "HIGH",
                "Add throughput, latency or percentage numbers to one existing project."));
        }
        if (!deploy) {
            out.add(new Weakness(
                "No deployment or cloud experience listed",
                "No mention of Docker, Kubernetes, AWS/GCP/Azure, CI/CD or deployment.",
                "Most " + role + " roles expect familiarity with shipping and operating software.",
                "HIGH",
                "Deploy a project to a cloud provider and document the architecture."));
        }
        if (!test) {
            out.add(new Weakness(
                "Testing and code-quality practices not mentioned",
                "No unit tests, coverage, TDD or CI test references were found.",
                "Testing signals engineering maturity that " + role + " interviewers look for.",
                "MEDIUM",
                "Add automated tests to a project and state the coverage reached."));
        }
        if (!links) {
            out.add(new Weakness(
                "No links to code or portfolio",
                "No GitHub, GitLab, portfolio or project URLs were found.",
                "Links let reviewers verify your work directly for " + role + " applications.",
                "MEDIUM",
                "Publish projects on GitHub and add the links to your resume."));
        }
        if (!missingKeywords.isEmpty()) {
            out.add(new Weakness(
                "Missing role keywords: " + String.join(", ", missingKeywords),
                "These role-relevant terms were not found in the resume text.",
                "Keyword gaps reduce match rate against " + role + " postings and ATS filters.",
                missingKeywords.size() >= 3 ? "HIGH" : "MEDIUM",
                "Build or describe work that demonstrates " + missingKeywords.get(0) + "."));
        }
        if (out.isEmpty()) {
            out.add(new Weakness(
                "No major gaps detected",
                "The resume covers impact, deployment, testing and links.",
                "Refinement rather than new content is the highest-value next step.",
                "LOW",
                "Tailor the wording of each project to the specific " + role + " posting."));
        }
        return out;
    }

    private List<ProjectSuggestion> buildStructuredSuggestions(boolean impact, boolean deploy, boolean test,
                                                               boolean links, List<String> missingKeywords, String role) {
        List<ProjectSuggestion> out = new ArrayList<>();
        if (!impact) {
            out.add(new ProjectSuggestion(
                "Impact-Instrumented Rewrite",
                "Take one existing project and add measurable outcomes (latency, throughput, adoption).",
                "Projects lack quantified impact",
                List.of("Performance measurement", "Metrics reporting"),
                "Beginner",
                "A project you can describe with concrete numbers on your resume."));
        }
        if (!deploy) {
            out.add(new ProjectSuggestion(
                "Cloud-Deployed Service",
                "Containerize a service and deploy it to AWS/GCP/Azure with a documented architecture.",
                "No deployment or cloud experience listed",
                List.of("Docker", "Cloud deployment", "CI/CD"),
                "Intermediate",
                "Demonstrable experience shipping and operating software."));
        }
        if (!test) {
            out.add(new ProjectSuggestion(
                "Test-Covered Module",
                "Add an automated test suite to a project and reach a stated coverage target.",
                "Testing and code-quality practices not mentioned",
                List.of("Unit testing", "Test coverage"),
                "Beginner",
                "Evidence of engineering rigour for interviews."));
        }
        if (!missingKeywords.isEmpty()) {
            String kw = missingKeywords.get(0);
            out.add(new ProjectSuggestion(
                kw + " Showcase Project",
                "Build a focused project that exercises " + kw + " for your " + role + " target.",
                "Missing role keywords: " + String.join(", ", missingKeywords),
                List.of(kw),
                "Intermediate",
                "A concrete demonstration of " + kw + " on your resume."));
        }
        if (out.isEmpty()) {
            out.add(new ProjectSuggestion(
                "Role-Tailored Capstone",
                "Combine your strongest skills into one capstone aligned to the " + role + " you target.",
                "No major gaps detected",
                List.of("System design"),
                "Advanced",
                "A flagship project tailored to your target role."));
        }
        return out.stream().limit(4).toList();
    }

    /** Groups extracted skills by category. Only categories with at least one detected term appear. */
    private List<ExtractedCategory> buildExtractedCategories(String original, String lowerText) {
        List<ExtractedCategory> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : CATEGORY_TERMS.entrySet()) {
            List<ExtractedCategory.Item> items = new ArrayList<>();
            for (String term : entry.getValue()) {
                if (lowerText.contains(term.toLowerCase(Locale.ROOT))) {
                    items.add(new ExtractedCategory.Item(term, evidenceLine(original, term)));
                }
            }
            if (!items.isEmpty()) {
                out.add(new ExtractedCategory(entry.getKey(), items));
            }
        }
        return out;
    }

    private ActionPlan buildActionPlan(boolean impact, boolean deploy, boolean test, boolean links,
                                       List<String> missingKeywords, String role) {
        List<String> now = new ArrayList<>();
        List<String> next = new ArrayList<>();
        List<String> later = new ArrayList<>();
        if (!impact) now.add("Quantify one project's impact with concrete numbers.");
        if (!links) now.add("Add GitHub or portfolio links to your resume.");
        if (!test) next.add("Add automated tests to a project and record the coverage.");
        if (!missingKeywords.isEmpty()) {
            next.add("Start work that demonstrates " + missingKeywords.get(0) + ".");
        }
        if (!deploy) later.add("Deploy a project to the cloud and document the architecture.");
        if (missingKeywords.size() > 1) {
            later.add("Cover the remaining " + role + " keywords: "
                + String.join(", ", missingKeywords.subList(1, missingKeywords.size())) + ".");
        }
        if (now.isEmpty()) now.add("Tailor the wording of each project to the " + role + " posting.");
        return new ActionPlan(now, next, later);
    }

    /**
     * Returns the trimmed line of the original resume that first contains {@code term} (case-insensitive),
     * capped for display. This is the verbatim evidence span, never a fabricated quote.
     */
    private String evidenceLine(String original, String term) {
        String lowerTerm = term.toLowerCase(Locale.ROOT);
        for (String line : original.split("\\r?\\n")) {
            if (line.toLowerCase(Locale.ROOT).contains(lowerTerm)) {
                String trimmed = line.strip();
                return trimmed.length() > 160 ? trimmed.substring(0, 157) + "..." : trimmed;
            }
        }
        return "Found in resume";
    }

    private boolean containsAny(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
