"""Readiness scoring.

This is a transparent, deterministic baseline standing in for a trained, calibrated model
(the specification calls for a calibrated gradient-boosting/logistic baseline once labelled
data exists). It is intentionally explainable end to end: the score is a weighted average of
the provided dimensions, and each reported factor is that dimension's weighted deviation from
a neutral midpoint. No protected attributes are used, and missing dimensions are recorded as
warnings rather than imputed as low scores.
"""

from datetime import datetime, timezone

from campuslink_ml.schemas import (
    Factor,
    ReadinessAssessments,
    ReadinessResponse,
)

MODEL_VERSION = "readiness-5factor-2026-10"
FEATURE_VERSION = "student-features-1"

# Weights sum to 1.0. These encode domain priority, not a learned model. The model is
# five-factor: technical/coding were retired (they never had a data producer), and
# Projects carries the largest weight as the strongest placement signal.
DIMENSION_WEIGHTS: dict[str, float] = {
    "academic": 0.20,
    "aptitude": 0.20,
    "communication": 0.15,
    "projects": 0.25,
    "interview": 0.20,
}

NEUTRAL_MIDPOINT = 50.0
READY_THRESHOLD = 70.0
DEVELOPING_THRESHOLD = 50.0


def score_readiness(student_id: str, assessments: ReadinessAssessments) -> ReadinessResponse:
    provided = {
        name: value
        for name, value in assessments.model_dump().items()
        if value is not None
    }
    warnings: list[str] = []
    missing = [name for name in DIMENSION_WEIGHTS if name not in provided]
    if missing:
        warnings.append("Missing dimensions treated as absent, not zero: " + ", ".join(sorted(missing)))

    if not provided:
        # Nothing to score. Per policy, absence of evidence is not low ability: return an
        # explicitly low-confidence, uncategorised result rather than a fabricated score.
        return ReadinessResponse(
            student_id=student_id,
            score=0.0,
            category="INSUFFICIENT_DATA",
            breakdown={},
            top_factors=[],
            confidence=0.0,
            warnings=["No assessment dimensions were provided"],
            model_version=MODEL_VERSION,
            feature_version=FEATURE_VERSION,
            generated_at=_now(),
        )

    # Renormalise weights across the dimensions actually provided so a missing dimension
    # neither inflates nor deflates the score.
    available_weight = sum(DIMENSION_WEIGHTS[name] for name in provided)
    score = sum(provided[name] * DIMENSION_WEIGHTS[name] for name in provided) / available_weight

    factors = _top_factors(provided)
    confidence = round(available_weight, 4)  # fraction of total weight covered by real data

    return ReadinessResponse(
        student_id=student_id,
        score=round(score, 2),
        category=_category(score),
        breakdown={name: round(value, 2) for name, value in provided.items()},
        top_factors=factors,
        confidence=confidence,
        warnings=warnings,
        model_version=MODEL_VERSION,
        feature_version=FEATURE_VERSION,
        generated_at=_now(),
    )


def _top_factors(provided: dict[str, float], limit: int = 3) -> list[Factor]:
    # Contribution of a dimension = weight * (value - midpoint), normalised to [-1, 1].
    scale = NEUTRAL_MIDPOINT  # max absolute deviation from midpoint
    contributions = [
        Factor(
            name=name,
            impact=round(DIMENSION_WEIGHTS[name] * (value - NEUTRAL_MIDPOINT) / scale, 4),
            direction="positive" if value >= NEUTRAL_MIDPOINT else "negative",
        )
        for name, value in provided.items()
    ]
    contributions.sort(key=lambda factor: abs(factor.impact), reverse=True)
    return contributions[:limit]


def _category(score: float) -> str:
    if score >= READY_THRESHOLD:
        return "READY"
    if score >= DEVELOPING_THRESHOLD:
        return "DEVELOPING"
    return "AT_RISK"


def _now() -> datetime:
    return datetime.now(timezone.utc)
