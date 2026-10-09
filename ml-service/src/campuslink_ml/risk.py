"""Placement-risk scoring.

Predicts the probability that a student would benefit from *additional placement support*
within the cycle. This is support-oriented by design (SECURITY.md section 5, spec section 5.2):

- the output is a support probability and a band, never an exclusion or denial decision;
- every non-trivial band carries recommended interventions tied to the driving factors;
- missing signals are recorded as warnings, never imputed as high risk
  ("treat incomplete profiles as needing more information, not as low potential");
- signals are behavioural/preparation measures, not protected attributes or their proxies.

This is a transparent, deterministic baseline standing in for a calibrated, support-labelled
model. Because missing a student who needs support is costly (false negatives are the expensive
error), the mapping errs toward flagging rather than clearing when evidence is thin.
"""

from datetime import datetime, timezone

from campuslink_ml.schemas import Factor, RiskResponse, RiskSignals

MODEL_VERSION = "risk-baseline-2026-01"
FEATURE_VERSION = "student-features-1"

# Each signal contributes a weighted concern in [0, 1]; weights sum to 1.0.
SIGNAL_WEIGHTS: dict[str, float] = {
    "readiness_score": 0.30,
    "backlog_count": 0.15,
    "application_activity": 0.15,
    "interview_followthrough": 0.15,
    "recency": 0.15,
    "mock_preparation": 0.10,
}

SUPPORT_THRESHOLD = 0.60
MONITOR_THRESHOLD = 0.35

INTERVENTIONS: dict[str, str] = {
    "readiness_score": "Schedule a readiness review and targeted skill-building plan",
    "backlog_count": "Refer to academic support for backlog clearance",
    "application_activity": "Assign a placement counsellor to increase application activity",
    "interview_followthrough": "Follow up on missed interviews and confirm attendance ahead of the next slot",
    "recency": "Re-engage the student; no recent placement activity recorded",
    "mock_preparation": "Enrol in mock-interview sessions before the next drive",
}


def score_risk(student_id: str, signals: RiskSignals) -> RiskResponse:
    concerns: dict[str, float] = {}
    warnings: list[str] = []

    _add(concerns, warnings, "readiness_score", _readiness_concern(signals.readiness_score),
         signals.readiness_score is not None)
    _add(concerns, warnings, "backlog_count", _backlog_concern(signals.backlog_count),
         signals.backlog_count is not None)
    _add(concerns, warnings, "application_activity", _application_concern(signals.applications_submitted),
         signals.applications_submitted is not None)
    _add(concerns, warnings, "interview_followthrough",
         _followthrough_concern(signals.interviews_attended, signals.interviews_missed),
         signals.interviews_attended is not None or signals.interviews_missed is not None)
    _add(concerns, warnings, "recency", _recency_concern(signals.days_since_last_activity),
         signals.days_since_last_activity is not None)
    _add(concerns, warnings, "mock_preparation", _mock_concern(signals.mock_interviews_completed),
         signals.mock_interviews_completed is not None)

    if not concerns:
        return RiskResponse(
            student_id=student_id,
            support_probability=0.0,
            band="INSUFFICIENT_DATA",
            top_factors=[],
            recommended_interventions=["Collect placement-activity signals before assessing support needs"],
            confidence=0.0,
            warnings=["No risk signals were provided"],
            model_version=MODEL_VERSION,
            feature_version=FEATURE_VERSION,
            generated_at=_now(),
        )

    available_weight = sum(SIGNAL_WEIGHTS[name] for name in concerns)
    probability = sum(concerns[name] * SIGNAL_WEIGHTS[name] for name in concerns) / available_weight
    factors = _top_factors(concerns)
    band = _band(probability)

    return RiskResponse(
        student_id=student_id,
        support_probability=round(probability, 4),
        band=band,
        top_factors=factors,
        recommended_interventions=_interventions(band, factors),
        confidence=round(available_weight, 4),
        warnings=warnings,
        model_version=MODEL_VERSION,
        feature_version=FEATURE_VERSION,
        generated_at=_now(),
    )


def _add(concerns, warnings, name, value, present) -> None:
    if present:
        concerns[name] = value
    else:
        warnings.append(f"Signal '{name}' missing; excluded from the score rather than assumed high-risk")


def _readiness_concern(readiness: float | None) -> float:
    # Low readiness -> high concern.
    return 0.0 if readiness is None else _clamp((100.0 - readiness) / 100.0)


def _backlog_concern(backlogs: int | None) -> float:
    # 0 backlogs -> 0; concern saturates at 3+.
    return 0.0 if backlogs is None else _clamp(backlogs / 3.0)


def _application_concern(applications: int | None) -> float:
    # No applications -> high concern; concern clears at 5+ applications.
    return 0.0 if applications is None else _clamp((5 - applications) / 5.0)


def _followthrough_concern(attended: int | None, missed: int | None) -> float:
    attended = attended or 0
    missed = missed or 0
    total = attended + missed
    if total == 0:
        return 0.5  # invited-but-no-outcome data is mildly concerning, not cleared
    return _clamp(missed / total)


def _recency_concern(days: int | None) -> float:
    # Concern grows with inactivity, saturating at 30 days.
    return 0.0 if days is None else _clamp(days / 30.0)


def _mock_concern(mocks: int | None) -> float:
    # No mocks -> high concern; clears at 3+.
    return 0.0 if mocks is None else _clamp((3 - mocks) / 3.0)


def _top_factors(concerns: dict[str, float], limit: int = 3) -> list[Factor]:
    factors = [
        Factor(
            name=name,
            impact=round(SIGNAL_WEIGHTS[name] * value, 4),
            direction="increases-risk" if value > 0 else "neutral",
        )
        for name, value in concerns.items()
    ]
    factors.sort(key=lambda factor: factor.impact, reverse=True)
    return factors[:limit]


def _interventions(band: str, factors: list[Factor]) -> list[str]:
    if band == "LOW":
        return ["No additional support required at this time; continue routine monitoring"]
    # Recommend interventions for the factors actually driving the risk.
    return [INTERVENTIONS[factor.name] for factor in factors if factor.impact > 0 and factor.name in INTERVENTIONS]


def _band(probability: float) -> str:
    if probability >= SUPPORT_THRESHOLD:
        return "NEEDS_SUPPORT"
    if probability >= MONITOR_THRESHOLD:
        return "MONITOR"
    return "LOW"


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def _now() -> datetime:
    return datetime.now(timezone.utc)
