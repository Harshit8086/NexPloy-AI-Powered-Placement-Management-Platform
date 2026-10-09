"""Interview-attendance estimation for a proposed slot.

Estimates the probability that a shortlisted student attends a proposed time slot (spec section
5.4). Its role is strictly advisory:

- the output can inform the OR-Tools *soft* objective (prefer slots students are likely to
  attend) but must never override a *hard* scheduling constraint — Spring and the solver own
  feasibility, this service only offers a preference signal;
- signals are slot/logistics and past-behaviour measures, not protected attributes or proxies;
- missing signals are recorded as warnings and excluded from the estimate, never imputed;
- with no signals the estimate falls back to a neutral prior (0.5) with zero confidence rather
  than a confident 0 or 1, so the solver is not steered by fabricated certainty.

This is a transparent, deterministic baseline standing in for a calibrated attendance model. It
exists so the contract and its advisory boundary are correct before a model is trained.
"""

from datetime import datetime, timezone

from campuslink_ml.schemas import AttendanceResponse, AttendanceSignals, Factor

MODEL_VERSION = "attendance-baseline-2026-01"
FEATURE_VERSION = "slot-features-1"

# Neutral prior used when a signal is absent, and as the base the signals adjust around.
NEUTRAL_PRIOR = 0.5

# Each signal contributes a weighted attendance likelihood in [0, 1]; weights sum to 1.0.
SIGNAL_WEIGHTS: dict[str, float] = {
    "prior_attendance_rate": 0.35,
    "notification_lead_hours": 0.15,
    "days_to_nearest_exam": 0.20,
    "slot_hour": 0.10,
    "is_weekend": 0.05,
    "travel_minutes": 0.15,
}

LIKELY_THRESHOLD = 0.65
AT_RISK_THRESHOLD = 0.40


def predict_attendance(student_id: str, slot_id: str, signals: AttendanceSignals) -> AttendanceResponse:
    likelihoods: dict[str, float] = {}
    warnings: list[str] = []

    _add(likelihoods, warnings, "prior_attendance_rate",
         _prior_attendance(signals.prior_attendance_rate), signals.prior_attendance_rate is not None)
    _add(likelihoods, warnings, "notification_lead_hours",
         _lead_time(signals.notification_lead_hours), signals.notification_lead_hours is not None)
    _add(likelihoods, warnings, "days_to_nearest_exam",
         _exam_proximity(signals.days_to_nearest_exam), signals.days_to_nearest_exam is not None)
    _add(likelihoods, warnings, "slot_hour", _slot_hour(signals.slot_hour), signals.slot_hour is not None)
    _add(likelihoods, warnings, "is_weekend", _weekend(signals.is_weekend), signals.is_weekend is not None)
    _add(likelihoods, warnings, "travel_minutes", _travel(signals.travel_minutes), signals.travel_minutes is not None)

    if not likelihoods:
        return AttendanceResponse(
            student_id=student_id,
            slot_id=slot_id,
            attendance_probability=NEUTRAL_PRIOR,
            band="INSUFFICIENT_DATA",
            top_factors=[],
            confidence=0.0,
            warnings=["No attendance signals were provided; returning a neutral prior"],
            model_version=MODEL_VERSION,
            feature_version=FEATURE_VERSION,
            generated_at=_now(),
        )

    available_weight = sum(SIGNAL_WEIGHTS[name] for name in likelihoods)
    probability = sum(likelihoods[name] * SIGNAL_WEIGHTS[name] for name in likelihoods) / available_weight

    return AttendanceResponse(
        student_id=student_id,
        slot_id=slot_id,
        attendance_probability=round(probability, 4),
        band=_band(probability),
        top_factors=_top_factors(likelihoods),
        confidence=round(available_weight, 4),
        warnings=warnings,
        model_version=MODEL_VERSION,
        feature_version=FEATURE_VERSION,
        generated_at=_now(),
    )


def _add(likelihoods, warnings, name, value, present) -> None:
    if present:
        likelihoods[name] = value
    else:
        warnings.append(f"Signal '{name}' missing; excluded from the estimate rather than imputed")


def _prior_attendance(rate: float | None) -> float:
    return NEUTRAL_PRIOR if rate is None else _clamp(rate / 100.0)


def _lead_time(hours: float | None) -> float:
    # Too little notice hurts; benefit saturates around 48h.
    return NEUTRAL_PRIOR if hours is None else _clamp(hours / 48.0)


def _exam_proximity(days: int | None) -> float:
    # An exam today strongly depresses attendance; effect clears at 7+ days.
    return NEUTRAL_PRIOR if days is None else _clamp(days / 7.0)


def _slot_hour(hour: int | None) -> float:
    # Mid-day slots (10-16) attend best; very early/late slots worse.
    if hour is None:
        return NEUTRAL_PRIOR
    if 10 <= hour <= 16:
        return 0.9
    if 8 <= hour <= 18:
        return 0.6
    return 0.3


def _weekend(is_weekend: bool | None) -> float:
    if is_weekend is None:
        return NEUTRAL_PRIOR
    return 0.4 if is_weekend else 0.7


def _travel(minutes: int | None) -> float:
    # Short travel is fine; likelihood falls off, saturating at 120 min.
    return NEUTRAL_PRIOR if minutes is None else _clamp(1.0 - minutes / 120.0)


def _top_factors(likelihoods: dict[str, float], limit: int = 3) -> list[Factor]:
    # Direction reflects whether the signal pulls attendance above or below the neutral prior.
    factors = [
        Factor(
            name=name,
            impact=round(SIGNAL_WEIGHTS[name] * abs(value - NEUTRAL_PRIOR), 4),
            direction="increases-attendance" if value >= NEUTRAL_PRIOR else "decreases-attendance",
        )
        for name, value in likelihoods.items()
    ]
    factors.sort(key=lambda factor: factor.impact, reverse=True)
    return factors[:limit]


def _band(probability: float) -> str:
    if probability >= LIKELY_THRESHOLD:
        return "LIKELY"
    if probability >= AT_RISK_THRESHOLD:
        return "UNCERTAIN"
    return "AT_RISK"


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def _now() -> datetime:
    return datetime.now(timezone.utc)
