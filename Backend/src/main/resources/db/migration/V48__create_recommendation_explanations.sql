-- ============================================================
-- Explainable AI Recommendation: persisted AI explanation text, kept SEPARATE from the
-- deterministic evidence in match_results (auditability). One row per match_result; the AI
-- text is regenerated on data change or explicit request, while the evidence row is immutable.
-- recommendation_type is NOT stored here — it is derived at read time from eligibilityPassed
-- plus the latest shortlist decision, so it is always current.
-- ============================================================
CREATE TABLE recommendation_explanations (
    id                     VARCHAR(80)  NOT NULL PRIMARY KEY,
    match_result_id        VARCHAR(80)  NOT NULL UNIQUE REFERENCES match_results (id) ON DELETE CASCADE,
    ai_explanation         TEXT,
    improvement_areas_json TEXT,
    -- OK when the LLM produced the text; FALLBACK when the deterministic template was used
    -- (persisted so page loads do not re-hit the LLM; regeneration retries a live call).
    status                 VARCHAR(20)  NOT NULL,
    ai_model_version       VARCHAR(80),
    -- The match_results.computed_at this explanation was generated against, for staleness detection.
    source_computed_at     TIMESTAMPTZ,
    generated_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_recommendation_explanations_match_result
    ON recommendation_explanations (match_result_id);
