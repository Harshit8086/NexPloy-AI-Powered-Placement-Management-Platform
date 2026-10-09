package com.nexploy.campuslink.student.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The curated, deterministic source of <em>learning resources</em>: for each skill in the shared
 * roadmap/analyzer vocabulary, a short list of hand-picked, reputable resources (official docs,
 * well-known courses/books). This is version-controlled, human-authored content — the AI never
 * invents a URL, provider, course or certification. Same skill in -> same resources out.
 *
 * <p>Entries are validated at construction (fail-fast): every field non-blank, {@code url} an
 * {@code https://} link, and {@code type}/{@code difficulty} drawn from a fixed vocabulary. Only
 * {@code enabled} resources are indexed and surfaced, so a resource can be retired without deleting
 * its provenance from source control. Consumed by {@link LearningRecommendationService}, which pairs
 * these resources with the student's real, still-pending roadmap gaps.
 */
@Component
public class LearningResourceCatalog {

    /** Allowed resource kinds. Keeping this closed prevents typo'd, unrenderable categories. */
    static final Set<String> TYPES =
        Set.of("COURSE", "DOCS", "VIDEO", "BOOK", "INTERACTIVE", "TUTORIAL", "CERTIFICATION");

    /** Allowed difficulty labels, matching the roadmap's project-difficulty vocabulary. */
    static final Set<String> DIFFICULTIES = Set.of("Beginner", "Intermediate", "Advanced");

    /**
     * One curated learning resource. {@code skill} matches the demonstrated-skill vocabulary so it
     * lines up with a roadmap task; {@code estimatedEffort} is optional (may be null). {@code enabled}
     * false keeps an entry in source control without surfacing it.
     */
    public record LearningResource(
        String skill,
        String title,
        String provider,
        String url,
        String type,
        String difficulty,
        String description,
        String estimatedEffort,
        boolean enabled) {}

    private final Map<String, List<LearningResource>> bySkill;

    public LearningResourceCatalog() {
        this.bySkill = validateAndIndex(seed());
    }

    /** Enabled curated resources for a skill (case-insensitive); empty when none is curated yet. */
    public List<LearningResource> resourcesFor(String skill) {
        if (skill == null || skill.isBlank()) {
            return List.of();
        }
        return bySkill.getOrDefault(skill.toLowerCase(Locale.ROOT), List.of());
    }

    /** Whether at least one enabled resource is curated for the skill. */
    public boolean hasResource(String skill) {
        return !resourcesFor(skill).isEmpty();
    }

    // ---- Curated content (version-controlled; edit here, never generated) ------------------------
    private List<LearningResource> seed() {
        List<LearningResource> r = new ArrayList<>();
        // Backend / API
        r.add(res("REST API", "MDN: HTTP & REST fundamentals", "MDN Web Docs",
            "https://developer.mozilla.org/en-US/docs/Web/HTTP", "DOCS", "Beginner",
            "How HTTP methods, status codes and resources work — the basis of any REST API.", "3-4 hours"));
        r.add(res("REST API", "REST API Tutorial", "restfulapi.net",
            "https://restfulapi.net/", "TUTORIAL", "Intermediate",
            "Resource modelling, status codes and REST constraints with practical examples.", "2-3 hours"));
        r.add(res("REST", "MDN: An overview of HTTP", "MDN Web Docs",
            "https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview", "DOCS", "Beginner",
            "A concise overview of the HTTP protocol that REST is built on.", "1-2 hours"));
        r.add(res("SQL", "PostgreSQL Tutorial", "PostgreSQL",
            "https://www.postgresql.org/docs/current/tutorial.html", "DOCS", "Beginner",
            "The official hands-on introduction to relational tables, queries and joins.", "4-6 hours"));
        r.add(res("SQL", "SQL Tutorial", "Mode",
            "https://mode.com/sql-tutorial/", "TUTORIAL", "Intermediate",
            "Interactive lessons from basic SELECTs through window functions on real data.", "6-8 hours"));
        r.add(res("Microservices", "Microservice Architecture", "microservices.io",
            "https://microservices.io/", "DOCS", "Advanced",
            "Chris Richardson's pattern catalog for decomposing and running microservices.", null));
        r.add(res("Microservices", "Microservices", "martinfowler.com",
            "https://martinfowler.com/articles/microservices.html", "DOCS", "Intermediate",
            "The foundational article defining the microservices style and its trade-offs.", "1 hour"));
        r.add(res("System Design", "The System Design Primer", "GitHub (donnemartin)",
            "https://github.com/donnemartin/system-design-primer", "TUTORIAL", "Advanced",
            "An open, widely-used guide to scalable system design with worked examples.", null));
        r.add(res("Docker", "Docker: Get Started", "Docker",
            "https://docs.docker.com/get-started/", "DOCS", "Beginner",
            "Official walkthrough of images, containers and writing your first Dockerfile.", "2-3 hours"));
        return withFrontend(withData(withDatabase(withDevOps(withMobileAndCore(r)))));
    }

    private List<LearningResource> withFrontend(List<LearningResource> r) {
        r.add(res("React", "React: Learn", "react.dev",
            "https://react.dev/learn", "DOCS", "Beginner",
            "The official interactive introduction to components, props and state.", "5-8 hours"));
        r.add(res("TypeScript", "TypeScript Handbook", "typescriptlang.org",
            "https://www.typescriptlang.org/docs/handbook/intro.html", "DOCS", "Intermediate",
            "The authoritative guide to typing JavaScript for safer refactors.", "4-6 hours"));
        r.add(res("CSS", "MDN: Learn CSS", "MDN Web Docs",
            "https://developer.mozilla.org/en-US/docs/Learn/CSS", "DOCS", "Beginner",
            "Layout, the box model and responsive design from first principles.", "6-10 hours"));
        r.add(res("Accessibility", "W3C: Accessibility Fundamentals", "W3C WAI",
            "https://www.w3.org/WAI/fundamentals/", "DOCS", "Intermediate",
            "How to build UIs usable by everyone — semantics, ARIA and testing.", "3-4 hours"));
        r.add(res("State Management", "React: Managing State", "react.dev",
            "https://react.dev/learn/managing-state", "DOCS", "Intermediate",
            "Structuring, sharing and lifting state in a component-driven UI.", "2-3 hours"));
        return r;
    }

    private List<LearningResource> withData(List<LearningResource> r) {
        r.add(res("Python", "The Python Tutorial", "Python.org",
            "https://docs.python.org/3/tutorial/", "DOCS", "Beginner",
            "The official language tutorial from syntax through modules and data types.", "6-8 hours"));
        r.add(res("Pandas", "Pandas: Getting Started", "pandas.pydata.org",
            "https://pandas.pydata.org/docs/getting_started/index.html", "DOCS", "Beginner",
            "Load, clean and reshape tabular data with the standard Python library.", "3-5 hours"));
        r.add(res("Statistics", "Statistics & Probability", "Khan Academy",
            "https://www.khanacademy.org/math/statistics-probability", "COURSE", "Beginner",
            "A free course covering descriptive and inferential statistics from scratch.", null));
        r.add(res("Machine Learning", "Machine Learning Crash Course", "Google",
            "https://developers.google.com/machine-learning/crash-course", "COURSE", "Intermediate",
            "Google's applied introduction to ML with TensorFlow exercises.", "15 hours"));
        return r;
    }

    private List<LearningResource> withDatabase(List<LearningResource> r) {
        r.add(res("Database Design", "PostgreSQL: DDL Basics", "PostgreSQL",
            "https://www.postgresql.org/docs/current/ddl.html", "DOCS", "Beginner",
            "Table definitions, constraints and schema objects — the basis of sound schema design.", "3-4 hours"));
        r.add(res("Query Optimisation", "PostgreSQL: Performance Tips", "PostgreSQL",
            "https://www.postgresql.org/docs/current/performance-tips.html", "DOCS", "Intermediate",
            "How to plan, index and tune queries so they stay fast as data grows.", "2-4 hours"));
        return r;
    }

    private List<LearningResource> withDevOps(List<LearningResource> r) {
        r.add(res("Kubernetes", "Kubernetes Basics", "kubernetes.io",
            "https://kubernetes.io/docs/tutorials/kubernetes-basics/", "TUTORIAL", "Intermediate",
            "Deploy, scale and update an app on a cluster in the official tutorial.", "3-4 hours"));
        r.add(res("CI/CD", "GitHub Actions Documentation", "GitHub",
            "https://docs.github.com/en/actions", "DOCS", "Intermediate",
            "Automate build, test and deploy pipelines that run on every push.", "3-5 hours"));
        r.add(res("AWS", "AWS: Getting Started", "Amazon Web Services",
            "https://aws.amazon.com/getting-started/", "DOCS", "Beginner",
            "Guided paths for provisioning and running your first cloud workloads.", null));
        r.add(res("Terraform", "Terraform Tutorials", "HashiCorp",
            "https://developer.hashicorp.com/terraform/tutorials", "TUTORIAL", "Intermediate",
            "Manage infrastructure as code across providers with hands-on labs.", "4-6 hours"));
        return r;
    }

    private List<LearningResource> withMobileAndCore(List<LearningResource> r) {
        r.add(res("Kotlin", "Kotlin: Getting Started", "Kotlin (JetBrains)",
            "https://kotlinlang.org/docs/getting-started.html", "DOCS", "Beginner",
            "The official path into Kotlin for Android and server-side development.", "4-6 hours"));
        r.add(res("Swift", "Get Started with Swift", "Swift.org",
            "https://www.swift.org/getting-started/", "DOCS", "Beginner",
            "Install the toolchain and learn Swift fundamentals from the language team.", "3-5 hours"));
        r.add(res("Testing", "The Practical Test Pyramid", "martinfowler.com",
            "https://martinfowler.com/articles/practical-test-pyramid.html", "DOCS", "Intermediate",
            "How to balance unit, integration and end-to-end tests effectively.", "1-2 hours"));
        r.add(res("Data Structures", "VisuAlgo: Data Structures", "VisuAlgo",
            "https://visualgo.net/en", "INTERACTIVE", "Beginner",
            "Animated visualisations of the core data structures and algorithms.", null));
        r.add(res("Git", "Pro Git (2nd Edition)", "git-scm.com",
            "https://git-scm.com/book/en/v2", "BOOK", "Beginner",
            "The complete, free official book on branching, merging and collaboration.", null));
        return r;
    }

    /** Curated entries are enabled by default; retire one by copying it with enabled=false. */
    private LearningResource res(String skill, String title, String provider, String url,
                                 String type, String difficulty, String description, String effort) {
        return new LearningResource(skill, title, provider, url, type, difficulty, description, effort, true);
    }

    // ---- Validation + indexing -------------------------------------------------------------------
    private Map<String, List<LearningResource>> validateAndIndex(List<LearningResource> resources) {
        Map<String, List<LearningResource>> index = new LinkedHashMap<>();
        for (LearningResource res : resources) {
            requireText(res.skill(), "skill", res);
            requireText(res.title(), "title", res);
            requireText(res.provider(), "provider", res);
            requireText(res.url(), "url", res);
            requireText(res.description(), "description", res);
            if (!res.url().startsWith("https://")) {
                throw new IllegalStateException("Learning resource URL must be https: " + res.url());
            }
            if (!TYPES.contains(res.type())) {
                throw new IllegalStateException("Unknown learning resource type '" + res.type()
                    + "' for " + res.title());
            }
            if (!DIFFICULTIES.contains(res.difficulty())) {
                throw new IllegalStateException("Unknown difficulty '" + res.difficulty()
                    + "' for " + res.title());
            }
            if (!res.enabled()) {
                continue;
            }
            index.computeIfAbsent(res.skill().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(res);
        }
        return index;
    }

    private void requireText(String value, String field, LearningResource res) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Learning resource " + field + " must not be blank: " + res);
        }
    }
}
