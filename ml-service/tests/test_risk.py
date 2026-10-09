from campuslink_ml.risk import score_risk
from campuslink_ml.schemas import RiskSignals


def test_strong_student_is_low_risk_with_no_interventions() -> None:
    result = score_risk("stu_001", RiskSignals(
        readiness_score=88,
        backlog_count=0,
        applications_submitted=6,
        interviews_attended=3,
        interviews_missed=0,
        days_since_last_activity=2,
        mock_interviews_completed=4,
    ))

    assert result.band == "LOW"
    assert result.support_probability < 0.35
    assert result.recommended_interventions == [
        "No additional support required at this time; continue routine monitoring"
    ]


def test_struggling_student_needs_support_with_targeted_interventions() -> None:
    result = score_risk("stu_002", RiskSignals(
        readiness_score=30,
        backlog_count=3,
        applications_submitted=0,
        interviews_attended=0,
        interviews_missed=2,
        days_since_last_activity=28,
        mock_interviews_completed=0,
    ))

    assert result.band == "NEEDS_SUPPORT"
    assert result.support_probability >= 0.60
    assert result.recommended_interventions  # non-empty, actionable
    # Interventions map to the factors that drove the risk.
    factor_names = {factor.name for factor in result.top_factors}
    assert factor_names.issubset({
        "readiness_score", "backlog_count", "application_activity",
        "interview_followthrough", "recency", "mock_preparation",
    })


def test_no_signals_returns_insufficient_data_never_high_risk() -> None:
    result = score_risk("stu_003", RiskSignals())

    assert result.band == "INSUFFICIENT_DATA"
    assert result.support_probability == 0.0
    assert result.confidence == 0.0
    assert result.top_factors == []


def test_missing_signals_are_flagged_and_excluded_not_assumed_high_risk() -> None:
    # Only a strong readiness signal is present; the rest are missing.
    result = score_risk("stu_004", RiskSignals(readiness_score=85))

    assert result.confidence < 1.0
    assert any("missing" in warning for warning in result.warnings)
    # A single strong signal must not be pushed to high risk by absent data.
    assert result.band != "NEEDS_SUPPORT"


def test_output_carries_model_and_feature_versions() -> None:
    result = score_risk("stu_005", RiskSignals(readiness_score=50))

    assert result.model_version
    assert result.feature_version
    assert result.generated_at is not None


def test_factors_ordered_by_descending_impact() -> None:
    result = score_risk("stu_006", RiskSignals(
        readiness_score=20, backlog_count=3, applications_submitted=5,
        interviews_attended=2, interviews_missed=0, days_since_last_activity=0,
        mock_interviews_completed=3,
    ))
    impacts = [factor.impact for factor in result.top_factors]
    assert impacts == sorted(impacts, reverse=True)
