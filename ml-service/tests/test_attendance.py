from campuslink_ml.attendance import predict_attendance
from campuslink_ml.schemas import AttendanceSignals


def test_favourable_slot_is_likely() -> None:
    result = predict_attendance("stu_001", "slot_001", AttendanceSignals(
        prior_attendance_rate=95,
        notification_lead_hours=48,
        days_to_nearest_exam=14,
        slot_hour=11,
        is_weekend=False,
        travel_minutes=10,
    ))

    assert result.band == "LIKELY"
    assert result.attendance_probability >= 0.65


def test_unfavourable_slot_is_at_risk() -> None:
    result = predict_attendance("stu_002", "slot_002", AttendanceSignals(
        prior_attendance_rate=20,
        notification_lead_hours=2,
        days_to_nearest_exam=0,
        slot_hour=7,
        is_weekend=True,
        travel_minutes=110,
    ))

    assert result.band == "AT_RISK"
    assert result.attendance_probability < 0.40


def test_no_signals_returns_neutral_prior_with_zero_confidence() -> None:
    result = predict_attendance("stu_003", "slot_003", AttendanceSignals())

    assert result.band == "INSUFFICIENT_DATA"
    assert result.attendance_probability == 0.5  # neutral prior, not a confident 0 or 1
    assert result.confidence == 0.0
    assert result.top_factors == []


def test_missing_signals_flagged_and_excluded_not_imputed() -> None:
    result = predict_attendance("stu_004", "slot_004", AttendanceSignals(prior_attendance_rate=90))

    assert result.confidence < 1.0
    assert any("missing" in warning for warning in result.warnings)


def test_factor_direction_reflects_pull_around_prior() -> None:
    result = predict_attendance("stu_005", "slot_005", AttendanceSignals(
        prior_attendance_rate=95,   # pulls up
        days_to_nearest_exam=0,     # pulls down
    ))

    directions = {f.name: f.direction for f in result.top_factors}
    assert directions["prior_attendance_rate"] == "increases-attendance"
    assert directions["days_to_nearest_exam"] == "decreases-attendance"


def test_output_carries_model_and_feature_versions() -> None:
    result = predict_attendance("stu_006", "slot_006", AttendanceSignals(prior_attendance_rate=60))

    assert result.model_version
    assert result.feature_version
    assert result.generated_at is not None
