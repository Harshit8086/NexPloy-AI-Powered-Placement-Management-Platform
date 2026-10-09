from campuslink_ml.readiness import score_readiness
from campuslink_ml.schemas import ReadinessAssessments


def full_assessments(**overrides) -> ReadinessAssessments:
    base = dict(
        academic=84,
        aptitude=79,
        communication=69,
        projects=80,
        interview=72,
    )
    base.update(overrides)
    return ReadinessAssessments(**base)


def test_full_profile_scores_ready_with_full_confidence() -> None:
    result = score_readiness("stu_001", full_assessments())

    assert result.category == "READY"
    # Weighted average over the five factors: 84*.20 + 79*.20 + 69*.15 + 80*.25 + 72*.20.
    assert result.score == 77.35
    assert result.confidence == 1.0
    assert result.breakdown["projects"] == 80
    assert result.warnings == []


def test_low_profile_scores_at_risk() -> None:
    result = score_readiness("stu_002", full_assessments(
        academic=30, aptitude=35, communication=28, projects=22, interview=18,
    ))

    assert result.category == "AT_RISK"
    assert result.score < 50


def test_missing_dimensions_are_flagged_and_do_not_deflate_score() -> None:
    partial = ReadinessAssessments(projects=90, interview=85)
    result = score_readiness("stu_003", partial)

    # Only two strong dimensions provided; score reflects them, not zeros for the rest.
    assert result.score >= 80
    assert result.confidence < 1.0
    assert any("Missing dimensions" in warning for warning in result.warnings)
    assert set(result.breakdown.keys()) == {"projects", "interview"}


def test_no_data_returns_insufficient_data_not_a_fabricated_score() -> None:
    result = score_readiness("stu_004", ReadinessAssessments())

    assert result.category == "INSUFFICIENT_DATA"
    assert result.confidence == 0.0
    assert result.top_factors == []


def test_top_factors_rank_by_absolute_impact_and_report_direction() -> None:
    result = score_readiness("stu_005", full_assessments(communication=10, projects=98))

    assert len(result.top_factors) == 3
    # Highest-weight dimension surfaces first; the deliberately weak one reports negative.
    names = [factor.name for factor in result.top_factors]
    assert names[0] == "projects"
    communication = next((f for f in result.top_factors if f.name == "communication"), None)
    if communication is not None:
        assert communication.direction == "negative"


def test_impacts_are_ordered_descending_by_magnitude() -> None:
    result = score_readiness("stu_006", full_assessments())
    magnitudes = [abs(factor.impact) for factor in result.top_factors]
    assert magnitudes == sorted(magnitudes, reverse=True)