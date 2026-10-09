package com.nexploy.campuslink.shared.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.matching.client.MlMatchingClient.Candidate;
import com.nexploy.campuslink.matching.client.MlMatchingClient.Features;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingRequest;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingResult;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.Assessments;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScore;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScoreRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * Contract test for the cross-service ML client DTOs. Unlike the frontend-facing REST DTOs, these
 * carry {@code @JsonNaming(SnakeCaseStrategy)} because the Python FastAPI service (Pydantic v2)
 * speaks snake_case. This test locks the wire format both ways — requests must serialize to the
 * snake_case keys the Python schemas expect, and responses must deserialize from snake_case — so a
 * field rename or a dropped naming annotation fails here instead of silently breaking the live call.
 */
@JsonTest
class MlClientContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void matchingRequestSerializesSnakeCase() throws Exception {
        MatchingRequest request = new MatchingRequest(
            "req_1", "campus_1", "cycle_1", "policy-2026-01", "drive_1",
            List.of(new Candidate("stu_1", true,
                new Features(0.8, 0.6, 0.5, 0.7, 0.9, 0.75, 0.5, 0.6), List.of("Java", "Spring Boot"))),
            List.of("Java", "AWS"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.has("request_id")).isTrue();
        assertThat(json.has("campus_id")).isTrue();
        assertThat(json.has("placement_cycle_id")).isTrue();
        assertThat(json.has("model_policy_version")).isTrue();
        assertThat(json.has("drive_id")).isTrue();
        JsonNode candidate = json.get("candidates").get(0);
        assertThat(candidate.has("student_id")).isTrue();
        assertThat(candidate.has("eligible")).isTrue();
        assertThat(candidate.has("skills")).isTrue();
        assertThat(json.has("required_skills")).isTrue();
        JsonNode features = candidate.get("features");
        assertThat(features.has("required_skill_coverage")).isTrue();
        assertThat(features.has("semantic_similarity")).isTrue();
        assertThat(features.has("project_relevance")).isTrue();
        assertThat(features.has("interview_score")).isTrue();
        assertThat(features.has("certification_relevance")).isTrue();
        assertThat(features.has("historical_interview_pass_rate")).isTrue();
        // No camelCase keys should leak onto the wire.
        assertThat(objectMapper.writeValueAsString(request))
            .doesNotContain("requestId", "placementCycleId", "requiredSkillCoverage");
    }

    @Test
    void matchingResultDeserializesFromSnakeCase() throws Exception {
        String wire = """
            {
              "drive_id": "drive_1",
              "ranked": [
                {
                  "student_id": "stu_1",
                  "status": "ELIGIBLE_RANKED",
                  "fit_score": 0.82,
                  "rank": 1,
                  "top_factors": [{"name": "required_skill_coverage", "impact": 0.3, "direction": "positive"}],
                  "confidence": 0.9,
                  "warnings": []
                }
              ],
              "model_version": "matching-baseline-2026-01",
              "feature_version": "feat-1",
              "generated_at": "2026-01-15T10:00:00Z"
            }
            """;

        MatchingResult result = objectMapper.readValue(wire, MatchingResult.class);

        assertThat(result.driveId()).isEqualTo("drive_1");
        assertThat(result.ranked()).hasSize(1);
        assertThat(result.ranked().get(0).studentId()).isEqualTo("stu_1");
        assertThat(result.ranked().get(0).fitScore()).isEqualTo(0.82);
        assertThat(result.ranked().get(0).topFactors().get(0).name())
            .isEqualTo("required_skill_coverage");
        assertThat(result.modelVersion()).isEqualTo("matching-baseline-2026-01");
    }

    @Test
    void readinessRequestSerializesSnakeCase() throws Exception {
        ReadinessScoreRequest request = new ReadinessScoreRequest(
            "req_1", "campus_1", "cycle_1", "policy-2026-01", "stu_1",
            new Assessments(0.8, 0.7, 0.6, 0.5, 0.9));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.has("request_id")).isTrue();
        assertThat(json.has("placement_cycle_id")).isTrue();
        assertThat(json.has("model_policy_version")).isTrue();
        assertThat(json.has("student_id")).isTrue();
        assertThat(json.get("assessments").has("interview")).isTrue();
        assertThat(json.get("assessments").has("academic")).isTrue();
        // The retired technical/coding dimensions must not leak onto the ML wire.
        assertThat(json.get("assessments").has("technical")).isFalse();
        assertThat(json.get("assessments").has("coding")).isFalse();
    }

    @Test
    void readinessScoreDeserializesFromSnakeCase() throws Exception {
        String wire = """
            {
              "student_id": "stu_1",
              "score": 78.5,
              "category": "READY",
              "breakdown": {"academic": 0.7},
              "top_factors": [{"name": "projects", "impact": 0.2, "direction": "positive"}],
              "confidence": 0.85,
              "warnings": ["missing aptitude signal"],
              "model_version": "readiness-baseline-2026-01",
              "feature_version": "feat-1",
              "generated_at": "2026-01-15T10:00:00Z"
            }
            """;

        ReadinessScore score = objectMapper.readValue(wire, ReadinessScore.class);

        assertThat(score.studentId()).isEqualTo("stu_1");
        assertThat(score.score()).isEqualTo(78.5);
        assertThat(score.topFactors().get(0).name()).isEqualTo("projects");
        assertThat(score.modelVersion()).isEqualTo("readiness-baseline-2026-01");
        assertThat(score.warnings()).containsExactly("missing aptitude signal");
    }
}
