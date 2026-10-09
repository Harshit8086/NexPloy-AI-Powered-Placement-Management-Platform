package com.nexploy.campuslink.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted, human-readable AI explanation for one scored match, kept deliberately SEPARATE from the
 * deterministic evidence in {@code match_results} (auditability): the evidence row is immutable, while
 * this text is regenerated on data change or explicit request and can fail (then a deterministic
 * fallback is stored with {@code status=FALLBACK} so page loads never re-hit the model).
 *
 * <p>One row per {@code matchResultId}. The recommendation TYPE is intentionally not stored here — it
 * depends on the mutable shortlist decision and is derived at read time so it is always current.
 * {@code sourceComputedAt} is the {@code match_results.computed_at} this text was generated against, used
 * to detect a stale explanation after the evidence is re-scored.
 */
@Entity
@Table(name = "recommendation_explanations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationExplanation {

    @Id
    private String id;

    private String matchResultId;

    @Column(columnDefinition = "text")
    private String aiExplanation;

    /** Improvement / next-step bullet points, serialized as a JSON array of strings. */
    @Column(columnDefinition = "text")
    private String improvementAreasJson;

    /** OK when the LLM produced the text; FALLBACK when the deterministic template was used. */
    private String status;

    private String aiModelVersion;

    private OffsetDateTime sourceComputedAt;
    private OffsetDateTime generatedAt;
}
