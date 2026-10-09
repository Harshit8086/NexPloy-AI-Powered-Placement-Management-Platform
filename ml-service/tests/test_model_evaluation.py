"""Model-evaluation tests (TEST_PLAN.md section 5).

These differ from the per-function unit tests: they run each baseline over a small fixed,
labelled dataset and assert aggregate quality metrics — ranking precision@k / NDCG and the
eligibility false-positive rate for matching, and monotonicity / ordering / calibration-direction
for readiness. They are the guardrail that a future change to the scoring logic (or a swap to a
learned model) does not regress ranking quality or the responsible-AI invariants, and they give a
concrete, reviewable baseline number to compare a trained model against.

The baselines are deterministic, so the expected metrics are exact rather than thresholds where
possible; where a threshold is used it is a floor the baseline must not drop below.
"""

import math

from campuslink_ml.attendance import predict_attendance
from campuslink_ml.matching import rank_candidates
from campuslink_ml.readiness import score_readiness
from campuslink_ml.risk import score_risk
from campuslink_ml.schemas import (
    AttendanceSignals,
    RankCandidate,
    RankCandidateFeatures,
    ReadinessAssessments,
    RiskSignals,
)


# --------------------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------------------

def _candidate(student_id: str, eligible: bool, **features: float) -> RankCandidate:
    return RankCandidate(
        student_id=student_id,
        eligible=eligible,
        features=RankCandidateFeatures(**features),
    )


def _all_features(base: float) -> dict[str, float]:
    return {
        "required_skill_coverage": base,
        "semantic_similarity": base,
        "project_relevance": base,
        "readiness": base,
        "interview_score": base,
        "academics": base,
        "certification_relevance": base,
        "historical_interview_pass_rate": base,
    }


def _dcg(relevances: list[int]) -> float:
    return sum(rel / math.log2(i + 2) for i, rel in enumerate(relevances))


def _ndcg_at_k(ranked_relevances: list[int], k: int) -> float:
    ideal = sorted(ranked_relevances, reverse=True)
    idcg = _dcg(ideal[:k])
    if idcg == 0:
        return 0.0
    return _dcg(ranked_relevances[:k]) / idcg


# A fixed labelled evaluation set for one drive. `relevant` is the ground-truth label a
# recruiter would assign (1 = a genuinely good hire, 0 = not), independent of the model.
# Feature strength is correlated with relevance but deliberately noisy so the metrics are
# not trivially perfect.
RANKING_EVAL_SET = [
    # student_id, eligible, relevant, feature_base
    ("stu_a", True, 1, 92),
    ("stu_b", True, 1, 85),
    ("stu_c", True, 0, 40),
    ("stu_d", True, 1, 78),
    ("stu_e", True, 0, 30),
    ("stu_f", False, 1, 99),   # strong but INELIGIBLE — must never be ranked
    ("stu_g", True, 0, 55),
]


# --------------------------------------------------------------------------------------
# Matching: ranking quality
# --------------------------------------------------------------------------------------

def _run_ranking_eval():
    candidates = [
        _candidate(sid, eligible, **_all_features(base))
        for sid, eligible, _relevant, base in RANKING_EVAL_SET
    ]
    result = rank_candidates("drive_eval", candidates)
    labels = {sid: relevant for sid, _e, relevant, _b in RANKING_EVAL_SET}
    ranked = sorted(
        (c for c in result.ranked if c.rank is not None),
        key=lambda c: c.rank,
    )
    return result, ranked, labels


def test_precision_at_3_meets_baseline() -> None:
    _result, ranked, labels = _run_ranking_eval()
    top3 = ranked[:3]
    assert len(top3) == 3
    hits = sum(labels[c.student_id] for c in top3)
    precision_at_3 = hits / 3
    # The three relevant eligible candidates (a, b, d) are the strongest, so a correct
    # ranker puts all three on top: precision@3 == 1.0.
    assert precision_at_3 == 1.0


def test_ndcg_at_5_meets_baseline() -> None:
    _result, ranked, labels = _run_ranking_eval()
    ranked_relevances = [labels[c.student_id] for c in ranked]
    ndcg = _ndcg_at_k(ranked_relevances, k=5)
    # Deterministic baseline ranks all relevants first -> ideal ordering -> NDCG == 1.0.
    assert ndcg == 1.0


def test_eligibility_false_positive_rate_is_zero() -> None:
    """No ineligible candidate may ever receive a rank — the core safety invariant."""
    _result, ranked, _labels = _run_ranking_eval()
    ineligible_ids = {sid for sid, eligible, _r, _b in RANKING_EVAL_SET if not eligible}
    ranked_ids = {c.student_id for c in ranked}
    false_positives = ineligible_ids & ranked_ids
    assert false_positives == set()


def test_ineligible_relevant_candidate_is_not_ranked_despite_top_features() -> None:
    # stu_f is the strongest by features AND relevant, but ineligible: it must be excluded,
    # proving eligibility gates ranking rather than merit overriding eligibility.
    result, _ranked, _labels = _run_ranking_eval()
    stu_f = next(c for c in result.ranked if c.student_id == "stu_f")
    assert stu_f.status == "NOT_ELIGIBLE"
    assert stu_f.rank is None
    assert stu_f.fit_score is None


def test_ranking_is_deterministic_across_runs() -> None:
    _r1, ranked1, _l1 = _run_ranking_eval()
    _r2, ranked2, _l2 = _run_ranking_eval()
    assert [c.student_id for c in ranked1] == [c.student_id for c in ranked2]
    assert [c.fit_score for c in ranked1] == [c.fit_score for c in ranked2]


# --------------------------------------------------------------------------------------
# Readiness: monotonicity, ordering, calibration direction
# --------------------------------------------------------------------------------------

def _score(base: float) -> float:
    dims = {
        "academic": base, "aptitude": base,
        "communication": base, "projects": base, "interview": base,
    }
    return score_readiness("stu", ReadinessAssessments(**dims)).score


def test_readiness_is_monotonic_in_inputs() -> None:
    # Uniformly raising every dimension must never lower the score.
    scores = [_score(base) for base in range(0, 101, 10)]
    for lower, higher in zip(scores, scores[1:]):
        assert higher >= lower
    assert scores[0] < scores[-1]  # and strictly increases overall


def test_readiness_categories_are_ordered_by_score() -> None:
    at_risk = score_readiness("s1", ReadinessAssessments(**{k: 30 for k in _dims()}))
    developing = score_readiness("s2", ReadinessAssessments(**{k: 60 for k in _dims()}))
    ready = score_readiness("s3", ReadinessAssessments(**{k: 85 for k in _dims()}))
    assert at_risk.category == "AT_RISK"
    assert developing.category == "DEVELOPING"
    assert ready.category == "READY"
    assert at_risk.score < developing.score < ready.score


def test_readiness_all_max_equals_hundred_all_min_equals_zero() -> None:
    # Calibration endpoints: a uniform 100 -> 100, uniform 0 -> 0 (weights renormalise to 1).
    assert _score(100) == 100.0
    assert _score(0) == 0.0


def test_readiness_confidence_tracks_data_completeness() -> None:
    full = score_readiness("s_full", ReadinessAssessments(**{k: 70 for k in _dims()}))
    partial = score_readiness("s_partial", ReadinessAssessments(projects=70, interview=70))
    assert full.confidence == 1.0
    assert partial.confidence < full.confidence
    # A partial profile must not be penalised in score — same inputs, same score level.
    assert partial.score == 70.0


def test_readiness_missing_dimension_does_not_impute_zero() -> None:
    # Dropping a dimension must not drag the score toward zero (no zero-imputation).
    high_two = score_readiness("s", ReadinessAssessments(projects=90, interview=90))
    assert high_two.score == 90.0
    assert high_two.category == "READY"


def _dims() -> list[str]:
    return [
        "academic", "aptitude",
        "communication", "projects", "interview",
    ]


# --------------------------------------------------------------------------------------
# Risk: band accuracy, zero costly false-negatives, calibration direction
# --------------------------------------------------------------------------------------
#
# Risk is support-oriented, so the evaluation guardrails differ from ranking. The expensive
# error is a *false negative* — clearing a student who actually needs support — so the key
# metric is that no genuinely-struggling student is ever predicted LOW, alongside overall
# band accuracy and the direction of calibration (mean probability rises with true severity).
#
# A fixed labelled set of eight students, each with an independent (not uniformly-scaled)
# signal profile and the support tier a placement officer would assign as ground truth:
# LOW = on track, MONITOR = watch, NEEDS_SUPPORT = act now. Signals are on the module's own
# scale (higher backlog / missed interviews / inactivity = more concerning).

# student_id, true_tier, signals
RISK_EVAL_SET = [
    # On track: strong readiness, no backlogs, active, attending, recent, well-prepared.
    ("stu_low_1", "LOW", dict(readiness_score=90, backlog_count=0, applications_submitted=6,
                              interviews_attended=3, interviews_missed=0,
                              days_since_last_activity=1, mock_interviews_completed=4)),
    ("stu_low_2", "LOW", dict(readiness_score=82, backlog_count=0, applications_submitted=5,
                              interviews_attended=2, interviews_missed=0,
                              days_since_last_activity=3, mock_interviews_completed=3)),
    # Watch: middling readiness, thin activity or a little inactivity — concerning, not urgent.
    ("stu_mon_1", "MONITOR", dict(readiness_score=62, backlog_count=1, applications_submitted=3,
                                  interviews_attended=1, interviews_missed=1,
                                  days_since_last_activity=12, mock_interviews_completed=1)),
    ("stu_mon_2", "MONITOR", dict(readiness_score=58, backlog_count=1, applications_submitted=2,
                                  interviews_attended=1, interviews_missed=0,
                                  days_since_last_activity=15, mock_interviews_completed=1)),
    # Act now: low readiness, backlogs, little/no activity, missed interviews, stale, unprepared.
    ("stu_sup_1", "NEEDS_SUPPORT", dict(readiness_score=28, backlog_count=3, applications_submitted=0,
                                        interviews_attended=0, interviews_missed=2,
                                        days_since_last_activity=30, mock_interviews_completed=0)),
    ("stu_sup_2", "NEEDS_SUPPORT", dict(readiness_score=35, backlog_count=2, applications_submitted=1,
                                        interviews_attended=0, interviews_missed=2,
                                        days_since_last_activity=26, mock_interviews_completed=0)),
    ("stu_sup_3", "NEEDS_SUPPORT", dict(readiness_score=20, backlog_count=3, applications_submitted=0,
                                        interviews_attended=1, interviews_missed=3,
                                        days_since_last_activity=28, mock_interviews_completed=0)),
    ("stu_sup_4", "NEEDS_SUPPORT", dict(readiness_score=40, backlog_count=3, applications_submitted=1,
                                        interviews_attended=0, interviews_missed=2,
                                        days_since_last_activity=30, mock_interviews_completed=0)),
]


def _run_risk_eval() -> list[tuple[str, str, object]]:
    """Returns (true_tier, predicted_band, result) per student in the eval set."""
    rows = []
    for sid, tier, signals in RISK_EVAL_SET:
        result = score_risk(sid, RiskSignals(**signals))
        rows.append((tier, result.band, result))
    return rows


def test_risk_band_accuracy_meets_baseline() -> None:
    rows = _run_risk_eval()
    correct = sum(1 for tier, band, _r in rows if tier == band)
    accuracy = correct / len(rows)
    # The deterministic baseline separates all three tiers on this set: exact band match.
    assert accuracy == 1.0


def test_no_student_needing_support_is_cleared_as_low() -> None:
    """The costly error: a NEEDS_SUPPORT student predicted LOW. This must never happen."""
    rows = _run_risk_eval()
    false_negatives = [
        r for tier, band, r in rows if tier == "NEEDS_SUPPORT" and band == "LOW"
    ]
    assert false_negatives == []


def test_risk_probability_is_calibrated_by_tier() -> None:
    """Mean support probability must rise strictly with true severity (LOW < MONITOR < NEEDS)."""
    rows = _run_risk_eval()
    by_tier: dict[str, list[float]] = {"LOW": [], "MONITOR": [], "NEEDS_SUPPORT": []}
    for tier, _band, result in rows:
        by_tier[tier].append(result.support_probability)
    mean_low = sum(by_tier["LOW"]) / len(by_tier["LOW"])
    mean_mon = sum(by_tier["MONITOR"]) / len(by_tier["MONITOR"])
    mean_sup = sum(by_tier["NEEDS_SUPPORT"]) / len(by_tier["NEEDS_SUPPORT"])
    assert mean_low < mean_mon < mean_sup


def test_risk_scoring_is_deterministic_across_runs() -> None:
    first = [(band, r.support_probability) for _t, band, r in _run_risk_eval()]
    second = [(band, r.support_probability) for _t, band, r in _run_risk_eval()]
    assert first == second


def test_absent_data_is_not_imputed_as_risk() -> None:
    """Responsible-AI invariant at eval scale: absent signals are *excluded*, never imputed as
    high-risk, so a lone *benign* signal must never be inflated to a support band by the missing
    signals around it. (The baseline renormalises over present weight and deliberately errs toward
    flagging when a present signal is genuinely concerning — that is a different case, covered by
    the tier eval above — so this sweeps only benign values.) Confidence must reflect the single
    present signal, well below a full profile."""
    benign_lone_signals = (
        [("readiness_score", v) for v in (75, 90, 100)]
        + [("backlog_count", v) for v in (0,)]
        + [("applications_submitted", v) for v in (5, 10)]
        + [("days_since_last_activity", v) for v in (0, 2)]
        + [("mock_interviews_completed", v) for v in (3, 5)]
    )
    for name, value in benign_lone_signals:
        result = score_risk("stu_thin", RiskSignals(**{name: value}))
        assert result.band in {"LOW", "MONITOR"}, f"{name}={value} (benign, lone) reached {result.band}"
        assert result.band != "NEEDS_SUPPORT"
        assert result.confidence < 1.0  # only one of six signals present


def test_no_signals_is_insufficient_data_never_a_risk_band() -> None:
    """Empty profiles must resolve to INSUFFICIENT_DATA, never any actionable band."""
    result = score_risk("stu_empty", RiskSignals())
    assert result.band == "INSUFFICIENT_DATA"
    assert result.band not in {"LOW", "MONITOR", "NEEDS_SUPPORT"}
    assert result.support_probability == 0.0
    assert result.confidence == 0.0
    assert result.top_factors == []


# --------------------------------------------------------------------------------------
# Attendance: band accuracy, calibration direction, the advisory-boundary invariant
# --------------------------------------------------------------------------------------
#
# Attendance is advisory only — it informs the OR-Tools *soft* objective and must never feed
# the solver fabricated certainty. So beyond band accuracy and calibration, the defining
# guardrail is that absent data yields a neutral 0.5 prior at zero confidence (never a confident
# 0 or 1), and the estimate always stays within [0, 1]. A fixed labelled set of six proposed
# slots, each with an independent signal profile and the band a scheduler would assign as ground
# truth: LIKELY = schedule freely, UNCERTAIN = watch, AT_RISK = prefer another slot.

# student_id, slot_id, true_band, signals
ATTENDANCE_EVAL_SET = [
    # Attends freely: strong history, ample notice, no near exam, mid-day weekday, short travel.
    ("stu_a", "slot_a", "LIKELY", dict(prior_attendance_rate=95, notification_lead_hours=48,
                                       days_to_nearest_exam=14, slot_hour=11, is_weekend=False,
                                       travel_minutes=10)),
    ("stu_b", "slot_b", "LIKELY", dict(prior_attendance_rate=88, notification_lead_hours=48,
                                       days_to_nearest_exam=10, slot_hour=14, is_weekend=False,
                                       travel_minutes=20)),
    # Watch: middling history, moderate notice, an exam not far off, some travel.
    ("stu_c", "slot_c", "UNCERTAIN", dict(prior_attendance_rate=55, notification_lead_hours=18,
                                          days_to_nearest_exam=3, slot_hour=9, is_weekend=False,
                                          travel_minutes=45)),
    ("stu_d", "slot_d", "UNCERTAIN", dict(prior_attendance_rate=50, notification_lead_hours=12,
                                          days_to_nearest_exam=2, slot_hour=17, is_weekend=False,
                                          travel_minutes=60)),
    # Prefer another slot: weak history, little notice, exam today, early weekend slot, far travel.
    ("stu_e", "slot_e", "AT_RISK", dict(prior_attendance_rate=20, notification_lead_hours=2,
                                        days_to_nearest_exam=0, slot_hour=7, is_weekend=True,
                                        travel_minutes=110)),
    ("stu_f", "slot_f", "AT_RISK", dict(prior_attendance_rate=25, notification_lead_hours=3,
                                        days_to_nearest_exam=0, slot_hour=20, is_weekend=True,
                                        travel_minutes=100)),
]


def _run_attendance_eval() -> list[tuple[str, str, object]]:
    """Returns (true_band, predicted_band, result) per slot in the eval set."""
    rows = []
    for sid, slot_id, band, signals in ATTENDANCE_EVAL_SET:
        result = predict_attendance(sid, slot_id, AttendanceSignals(**signals))
        rows.append((band, result.band, result))
    return rows


def test_attendance_band_accuracy_meets_baseline() -> None:
    rows = _run_attendance_eval()
    correct = sum(1 for true, pred, _r in rows if true == pred)
    accuracy = correct / len(rows)
    # The deterministic baseline separates all three bands on this set: exact band match.
    assert accuracy == 1.0


def test_attendance_probability_is_calibrated_by_band() -> None:
    """Mean probability must rise strictly with true attendance likelihood
    (AT_RISK < UNCERTAIN < LIKELY)."""
    rows = _run_attendance_eval()
    by_band: dict[str, list[float]] = {"LIKELY": [], "UNCERTAIN": [], "AT_RISK": []}
    for true, _pred, result in rows:
        by_band[true].append(result.attendance_probability)
    mean_at_risk = sum(by_band["AT_RISK"]) / len(by_band["AT_RISK"])
    mean_uncertain = sum(by_band["UNCERTAIN"]) / len(by_band["UNCERTAIN"])
    mean_likely = sum(by_band["LIKELY"]) / len(by_band["LIKELY"])
    assert mean_at_risk < mean_uncertain < mean_likely


def test_attendance_estimates_stay_within_unit_interval() -> None:
    """A probability the solver consumes must always be a valid [0, 1] value."""
    rows = _run_attendance_eval()
    for _true, _pred, result in rows:
        assert 0.0 <= result.attendance_probability <= 1.0


def test_attendance_scoring_is_deterministic_across_runs() -> None:
    first = [(pred, r.attendance_probability) for _t, pred, r in _run_attendance_eval()]
    second = [(pred, r.attendance_probability) for _t, pred, r in _run_attendance_eval()]
    assert first == second


def test_absent_signals_never_feed_the_solver_fabricated_certainty() -> None:
    """The advisory-boundary invariant: with no signals the estimate is the neutral 0.5 prior at
    zero confidence — never a confident 0 or 1 that would steer the soft objective — and any
    single present signal leaves confidence well below a full profile."""
    empty = predict_attendance("stu_none", "slot_none", AttendanceSignals())
    assert empty.band == "INSUFFICIENT_DATA"
    assert empty.attendance_probability == 0.5
    assert empty.confidence == 0.0
    assert empty.top_factors == []

    # Each signal alone: still a bounded estimate at partial confidence, never full certainty.
    lone_signals = (
        [("prior_attendance_rate", v) for v in (0, 50, 100)]
        + [("notification_lead_hours", v) for v in (0, 24, 48)]
        + [("days_to_nearest_exam", v) for v in (0, 3, 7)]
        + [("travel_minutes", v) for v in (0, 60, 120)]
    )
    for name, value in lone_signals:
        result = predict_attendance("stu_lone", "slot_lone", AttendanceSignals(**{name: value}))
        assert 0.0 <= result.attendance_probability <= 1.0
        assert 0.0 < result.confidence < 1.0  # exactly one of six signals present
