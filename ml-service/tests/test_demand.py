from campuslink_ml.demand import forecast_demand
from campuslink_ml.schemas import DemandSeries, DemandSeriesPoint


def _series(key: str, dimension: str, counts: list[float]) -> DemandSeries:
    return DemandSeries(
        key=key,
        dimension=dimension,
        history=[DemandSeriesPoint(placement_cycle_id=f"c{i}", count=c) for i, c in enumerate(counts)],
    )


def test_rising_series_forecasts_above_last_value_with_interval() -> None:
    result = forecast_demand("2026", [_series("python", "SKILL", [10, 20, 30, 40])])

    forecast = result.forecasts[0]
    assert forecast.trend == "RISING"
    assert forecast.forecast > 40  # extrapolates the upward trend
    assert forecast.lower is not None and forecast.upper is not None
    assert forecast.lower <= forecast.forecast <= forecast.upper


def test_falling_series_trends_down() -> None:
    result = forecast_demand("2026", [_series("cobol", "SKILL", [40, 30, 20, 10])])

    forecast = result.forecasts[0]
    assert forecast.trend == "FALLING"
    assert forecast.forecast < 10


def test_flat_series_is_stable() -> None:
    result = forecast_demand("2026", [_series("cse", "BRANCH", [25, 25, 25, 25])])

    forecast = result.forecasts[0]
    assert forecast.trend == "STABLE"
    assert abs(forecast.forecast - 25) < 0.01
    # A noiseless flat series is a confident forecast.
    assert forecast.confidence > 0.5


def test_insufficient_history_yields_no_forecast_not_a_fabricated_number() -> None:
    result = forecast_demand("2026", [_series("rust", "SKILL", [5])])

    forecast = result.forecasts[0]
    assert forecast.forecast is None
    assert forecast.lower is None and forecast.upper is None
    assert forecast.trend == "UNKNOWN"
    assert forecast.confidence == 0.0
    assert any("history" in warning for warning in forecast.warnings)


def test_forecast_is_never_negative() -> None:
    # A steep decline would extrapolate below zero; demand is floored at 0.
    result = forecast_demand("2026", [_series("fortran", "SKILL", [30, 20, 10, 5])])

    forecast = result.forecasts[0]
    assert forecast.forecast >= 0.0
    assert forecast.lower >= 0.0


def test_noisy_series_widens_interval_and_lowers_confidence() -> None:
    steady = forecast_demand("2026", [_series("a", "SKILL", [20, 21, 20, 21])]).forecasts[0]
    noisy = forecast_demand("2026", [_series("b", "SKILL", [5, 40, 8, 38])]).forecasts[0]

    steady_width = steady.upper - steady.lower
    noisy_width = noisy.upper - noisy.lower
    assert noisy_width > steady_width
    assert noisy.confidence < steady.confidence


def test_multiple_series_forecast_independently() -> None:
    result = forecast_demand("2026", [
        _series("python", "SKILL", [10, 20, 30]),
        _series("cse", "BRANCH", [50, 50, 50]),
    ])

    keys = {f.key for f in result.forecasts}
    assert keys == {"python", "cse"}
    assert result.model_version and result.feature_version
    assert result.generated_at is not None
