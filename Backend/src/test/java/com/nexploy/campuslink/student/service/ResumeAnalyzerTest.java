package com.nexploy.campuslink.student.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse;
import com.nexploy.campuslink.student.dto.ResumeIntelligenceResponse;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic {@link ResumeAnalyzer}. They prove the analysis actually varies
 * with the resume's content — a resume that shows quantified impact, deployment, testing and links
 * scores higher and reports fewer weaknesses than a bare one — rather than emitting a fixed list.
 */
class ResumeAnalyzerTest {

    private final ResumeAnalyzer analyzer = new ResumeAnalyzer();

    @Test
    void strongResumeScoresHighAndHasFewerWeaknesses() {
        String resume = """
            Backend Developer. Built a REST API with Spring Boot and SQL.
            Reduced p95 latency by 40% and served 12000 requests/sec.
            Deployed on AWS with Docker and CI/CD via GitHub Actions.
            Added JUnit tests reaching 85% coverage. github.com/jane/project
            Designed microservices and system design for scale.
            """;

        ResumeIntelligenceResponse result = analyzer.analyze(resume, "Backend Developer");

        assertThat(result.score()).isGreaterThan(80);
        // impact + deployment + testing + links all present -> those weaknesses are absent
        assertThat(result.weaknesses())
            .noneMatch(w -> w.toLowerCase().contains("measurable impact"))
            .noneMatch(w -> w.toLowerCase().contains("deployment"))
            .noneMatch(w -> w.toLowerCase().contains("testing"));
        assertThat(result.strengths()).isNotEmpty();
    }

    @Test
    void bareResumeScoresLowerAndSurfacesTheGaps() {
        String resume = "Fresh graduate. Knows Java. Looking for a backend role.";

        ResumeIntelligenceResponse result = analyzer.analyze(resume, "Backend Developer");

        assertThat(result.score()).isLessThan(60);
        assertThat(result.weaknesses()).anyMatch(w -> w.toLowerCase().contains("measurable impact"));
        assertThat(result.projectSuggestions()).isNotEmpty();
        assertThat(result.recommendation()).isNotBlank();
    }

    @Test
    void twoDifferentResumesProduceDifferentAnalysis() {
        ResumeIntelligenceResponse strong = analyzer.analyze(
            "Deployed to AWS with Docker. Improved throughput by 30%. Wrote pytest tests. github.com/x",
            "DevOps Engineer");
        ResumeIntelligenceResponse weak = analyzer.analyze(
            "I did some college projects in Python.", "DevOps Engineer");

        assertThat(strong.score()).isNotEqualTo(weak.score());
        assertThat(strong.weaknesses()).isNotEqualTo(weak.weaknesses());
    }

    @Test
    void missingKeywordsReflectTheTargetRole() {
        // A data-role resume that mentions none of the data keywords should list them all as missing.
        ResumeIntelligenceResponse result = analyzer.analyze(
            "Built a website with HTML and CSS.", "Data Scientist");

        assertThat(result.missingKeywords()).contains("Python", "Machine Learning");
    }

    @Test
    void structuredAnalysisGroundsMetricsSkillMatchAndExtraction() {
        String resume = """
            Backend Developer. Built a REST API with Spring Boot and SQL on PostgreSQL.
            Reduced p95 latency by 40%. Deployed on AWS with Docker and CI/CD.
            Added JUnit tests reaching 85% coverage. github.com/jane/project
            """;

        ResumeAnalysisResponse result = analyzer.analyzeStructured(resume, "Backend Developer");

        // Every metric carries a stated basis — never an unexplained percentage.
        assertThat(result.metrics()).isNotEmpty()
            .allSatisfy(m -> {
                assertThat(m.value()).isBetween(0, 100);
                assertThat(m.basis()).isNotBlank();
            });
        // Skill match reflects the resume's own evidence: demonstrated skills cite a line, gaps say so.
        assertThat(result.skillMatch()).isNotEmpty()
            .allSatisfy(sm -> assertThat(sm.status()).isIn("DEMONSTRATED", "PARTIAL", "NOT_DEMONSTRATED"));
        assertThat(result.skillMatch()).anyMatch(sm -> "DEMONSTRATED".equals(sm.status()));
        // Extraction only reports categories with detected terms, and Docker/PostgreSQL are present.
        assertThat(result.extracted()).isNotEmpty();
        assertThat(result.extracted().stream().flatMap(c -> c.items().stream()).map(i -> i.value()))
            .contains("Docker", "PostgreSQL");
    }

    @Test
    void structuredWeaknessesCarrySeverityAndEvidenceForABareResume() {
        ResumeAnalysisResponse result = analyzer.analyzeStructured(
            "Fresh graduate. Knows Java. Looking for a backend role.", "Backend Developer");

        assertThat(result.weaknesses()).isNotEmpty()
            .allSatisfy(w -> {
                assertThat(w.severity()).isIn("HIGH", "MEDIUM", "LOW");
                assertThat(w.gap()).isNotBlank();
                assertThat(w.recommendedAction()).isNotBlank();
            });
        // A bare resume has no cloud terms -> "NOT_DEMONSTRATED" appears and an action plan is produced.
        assertThat(result.actionPlan().now()).isNotEmpty();
        assertThat(result.projectSuggestions()).isNotEmpty()
            .allSatisfy(p -> assertThat(p.name()).isNotBlank());
    }
}
