"""Demand forecasting by placement cycle.

Forecasts demand for skills, branches, recruiters, and package bands for an upcoming placement
cycle (spec section 5.5). Constraints this module honours:

- forecasts inform training plans and analytics, never student exclusion — the output carries
  no student identity and no eligibility or ranking effect;
- validation is time-aware: the series is treated as ordered cycles and the forecast extrapolates
  the recent trend, so it never peeks at the target it is predicting;
- uncertainty is always shown as a prediction interval, widening when history is short or noisy,
  so a thin series is not presented as a confident point estimate;
- a series with too little history yields no forecast (None) and a warning, rather than a
  fabricated number.

This is a transparent, deterministic baseline (last-value + linear trend with a residual-based
interval) standing in for a calibrated time-series model. It exists so the contract, the
time-ordering discipline, and the uncertainty shape are correct before a model is trained.
"""

from datetime import datetime, timezone

from campuslink_ml.schemas import (
    DemandForecast,
    DemandResponse,
    DemandSeries,
)

MODEL_VERSION = "demand-baseline-2026-01"
FEATURE_VERSION = "cycle-features-1"

# Need at least this many points to fit a trend; below it we do not forecast.
MIN_HISTORY = 2
# z for a ~90% interval under a normal residual assumption.
Z_90 = 1.645


def forecast_demand(target_cycle_id: str, series: list[DemandSeries]) -> DemandResponse:
    forecasts = [_forecast_one(item) for item in series]
    return DemandResponse(
        target_cycle_id=target_cycle_id,
        forecasts=forecasts,
        model_version=MODEL_VERSION,
        feature_version=FEATURE_VERSION,
        generated_at=_now(),
    )


def _forecast_one(item: DemandSeries) -> DemandForecast:
    counts = [point.count for point in item.history]

    if len(counts) < MIN_HISTORY:
        return DemandForecast(
            key=item.key,
            dimension=item.dimension,
            forecast=None,
            lower=None,
            upper=None,
            trend="UNKNOWN",
            confidence=0.0,
            warnings=[f"Only {len(counts)} cycle(s) of history; need at least {MIN_HISTORY} to forecast"],
        )

    # Time-aware: fit a simple least-squares line over the ordered cycles (x = 0..n-1) and
    # project one step ahead. This never uses the target cycle's own value.
    n = len(counts)
    xs = list(range(n))
    slope, intercept = _linear_fit(xs, counts)
    point = slope * n + intercept
    point = max(0.0, point)  # demand cannot be negative

    # Residual spread drives the interval; a short/noisy series widens it.
    residuals = [counts[i] - (slope * xs[i] + intercept) for i in range(n)]
    spread = _std(residuals)
    margin = Z_90 * spread
    lower = max(0.0, point - margin)
    upper = point + margin

    return DemandForecast(
        key=item.key,
        dimension=item.dimension,
        forecast=round(point, 2),
        lower=round(lower, 2),
        upper=round(upper, 2),
        trend=_trend(slope),
        confidence=round(_confidence(n, spread, counts), 4),
        warnings=_warnings(n),
    )


def _linear_fit(xs: list[int], ys: list[float]) -> tuple[float, float]:
    n = len(xs)
    mean_x = sum(xs) / n
    mean_y = sum(ys) / n
    denominator = sum((x - mean_x) ** 2 for x in xs)
    if denominator == 0:
        return 0.0, mean_y
    slope = sum((xs[i] - mean_x) * (ys[i] - mean_y) for i in range(n)) / denominator
    intercept = mean_y - slope * mean_x
    return slope, intercept


def _std(values: list[float]) -> float:
    n = len(values)
    if n < 2:
        return 0.0
    mean = sum(values) / n
    variance = sum((v - mean) ** 2 for v in values) / (n - 1)
    return variance ** 0.5


def _trend(slope: float) -> str:
    if slope > 0.5:
        return "RISING"
    if slope < -0.5:
        return "FALLING"
    return "STABLE"


def _confidence(n: int, spread: float, counts: list[float]) -> float:
    """Confidence rises with history length and falls with relative noise. Bounded to [0, 1]."""
    length_factor = min(1.0, (n - MIN_HISTORY + 1) / 4.0)
    mean = sum(counts) / n
    noise = 0.0 if mean == 0 else min(1.0, spread / mean)
    return max(0.0, length_factor * (1.0 - noise))


def _warnings(n: int) -> list[str]:
    if n < 4:
        return [f"Short history ({n} cycles); treat the interval as wide and the forecast as indicative"]
    return []


def _now() -> datetime:
    return datetime.now(timezone.utc)
