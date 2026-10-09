-- ============================================================================
-- V49 — correct the shape of risk_entries.factors_json seeded in V38.
--
-- V38 seeded factors as [{"factor":"coding","impact":"high"}, ...], but the
-- entity contract (RiskEntryResponse.RiskFactorView) is {name:String,
-- impact:double, direction:String}. The "impact":"high" STRING cannot be
-- deserialized into the primitive double `impact`, so Jackson throws and
-- GET /api/campuses/{id}/risk-entries returns 500 ("Could not read risk
-- factors"). This rewrites each seeded row into the correct shape:
--   name       <- the factor word
--   impact     <- high=0.85, medium=0.55, low=0.25 (numeric)
--   direction  <- "increases-risk" for high/medium, "neutral" for low
-- (direction matches the frontend mapper in mappers.ts).
--
-- Idempotent: targeted per-id UPDATEs. Only rows still holding the old
-- string-impact shape need this; running it twice is harmless.
-- ============================================================================

UPDATE risk_entries SET factors_json =
  '[{"name":"coding","impact":0.85,"direction":"increases-risk"},{"name":"placementActivity","impact":0.85,"direction":"increases-risk"},{"name":"projects","impact":0.55,"direction":"increases-risk"}]'
  WHERE id = 'risk_amit';

UPDATE risk_entries SET factors_json =
  '[{"name":"technical","impact":0.85,"direction":"increases-risk"},{"name":"placementActivity","impact":0.85,"direction":"increases-risk"},{"name":"aptitude","impact":0.55,"direction":"increases-risk"}]'
  WHERE id = 'risk_aman';

UPDATE risk_entries SET factors_json =
  '[{"name":"coding","impact":0.85,"direction":"increases-risk"},{"name":"interview","impact":0.85,"direction":"increases-risk"},{"name":"placementActivity","impact":0.55,"direction":"increases-risk"}]'
  WHERE id = 'risk_vikram';

UPDATE risk_entries SET factors_json =
  '[{"name":"interview","impact":0.85,"direction":"increases-risk"},{"name":"communication","impact":0.55,"direction":"increases-risk"}]'
  WHERE id = 'risk_sneha';

UPDATE risk_entries SET factors_json =
  '[{"name":"coding","impact":0.55,"direction":"increases-risk"},{"name":"placementActivity","impact":0.55,"direction":"increases-risk"}]'
  WHERE id = 'risk_rohit';

UPDATE risk_entries SET factors_json =
  '[{"name":"coding","impact":0.85,"direction":"increases-risk"},{"name":"projects","impact":0.55,"direction":"increases-risk"}]'
  WHERE id = 'risk_farhan';

UPDATE risk_entries SET factors_json =
  '[{"name":"interview","impact":0.25,"direction":"neutral"}]'
  WHERE id = 'risk_neha';
