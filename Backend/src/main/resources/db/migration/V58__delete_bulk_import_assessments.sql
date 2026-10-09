-- Purges assessment data that came from the (now-removed) officer CSV / bulk-import feature.
-- Runs in one transaction, in three steps:
--   1. Capture which students currently have bulk-imported rows (source = 'BULK_IMPORT').
--   2. Delete those rows from assessment_records.
--   3. Recompute the current assessment snapshot (assessments_json — the authoritative read
--      source — plus the mirrored assessment_* columns) for ONLY those students, from their
--      REMAINING records (latest test per type; 0 where no record remains).
--
-- Students who never had a bulk import are left completely untouched, so seeded demo baselines
-- are preserved. breakdown_json / readiness_score are NOT touched here (readiness-owned); they
-- refresh on the next readiness recompute, which reads assessment_records directly.
--
-- Postgres-only (the H2 test profile runs with Flyway disabled and never executes this).
-- IRREVERSIBLE: Flyway is forward-only and the deleted rows are not recoverable — back up first.

-- 1. Remember which students had bulk-imported scores (before deleting them).
CREATE TEMP TABLE _bulk_affected ON COMMIT DROP AS
SELECT DISTINCT student_id FROM assessment_records WHERE source = 'BULK_IMPORT';

-- 2. Delete the bulk-imported history rows.
DELETE FROM assessment_records WHERE source = 'BULK_IMPORT';

-- 3. Recompute the snapshot for exactly those students from whatever records remain
--    (e.g. aptitude-quiz or mock-interview rows); a dimension with no remaining record -> 0.
UPDATE students st SET
    assessment_aptitude       = p.apt,
    assessment_coding         = p.cod,
    assessment_technical      = p.tec,
    assessment_communication  = p.com,
    assessment_mock_interview = p.mok,
    assessments_json = json_build_object(
        'aptitude', p.apt,
        'coding', p.cod,
        'technical', p.tec,
        'communication', p.com,
        'mockInterview', p.mok
    )::text
FROM (
    SELECT a.student_id,
        COALESCE(MAX(l.normalized_score) FILTER (WHERE l.assessment_type = 'APTITUDE'), 0)       AS apt,
        COALESCE(MAX(l.normalized_score) FILTER (WHERE l.assessment_type = 'CODING'), 0)         AS cod,
        COALESCE(MAX(l.normalized_score) FILTER (WHERE l.assessment_type = 'TECHNICAL'), 0)      AS tec,
        COALESCE(MAX(l.normalized_score) FILTER (WHERE l.assessment_type = 'COMMUNICATION'), 0)  AS com,
        COALESCE(MAX(l.normalized_score) FILTER (WHERE l.assessment_type = 'MOCK_INTERVIEW'), 0) AS mok
    FROM _bulk_affected a
    LEFT JOIN LATERAL (
        SELECT DISTINCT ON (ar.assessment_type) ar.assessment_type, ar.normalized_score
        FROM assessment_records ar
        WHERE ar.student_id = a.student_id
        ORDER BY ar.assessment_type, ar.assessment_date DESC, ar.updated_at DESC
    ) l ON TRUE
    GROUP BY a.student_id
) p
WHERE st.id = p.student_id;
