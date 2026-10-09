from campuslink_ml.matching import rank_candidates
from campuslink_ml.schemas import RankCandidate, RankCandidateFeatures


def _candidate(student_id: str, eligible: bool, **features: float) -> RankCandidate:
    return RankCandidate(
        student_id=student_id,
        eligible=eligible,
        features=RankCandidateFeatures(**features),
    )


def test_ineligible_candidate_is_never_ranked_as_eligible() -> None:
    # A strong ineligible candidate must not outrank a weaker eligible one.
    result = rank_candidates("drive_001", [
        _candidate("stu_strong_ineligible", eligible=False,
                   required_skill_coverage=100, semantic_similarity=100,
                   project_relevance=100, readiness=100, interview_score=100, academics=100),
        _candidate("stu_weak_eligible", eligible=True,
                   required_skill_coverage=50, semantic_similarity=50,
                   project_relevance=50, readiness=50, interview_score=50, academics=50),
    ])

    by_id = {c.student_id: c for c in result.ranked}
    assert by_id["stu_strong_ineligible"].status == "NOT_ELIGIBLE"
    assert by_id["stu_strong_ineligible"].fit_score is None
    assert by_id["stu_strong_ineligible"].rank is None
    # The eligible candidate is the one that gets ranked, despite lower raw features.
    assert by_id["stu_weak_eligible"].rank == 1


def test_eligible_candidates_ranked_by_descending_fit() -> None:
    result = rank_candidates("drive_002", [
        _candidate("stu_high", eligible=True, required_skill_coverage=90,
                   semantic_similarity=85, project_relevance=80, readiness=88,
                   interview_score=82, academics=78),
        _candidate("stu_mid", eligible=True, required_skill_coverage=60,
                   semantic_similarity=55, project_relevance=50, readiness=60,
                   interview_score=58, academics=55),
    ])

    ranked = [c for c in result.ranked if c.rank is not None]
    assert [c.student_id for c in ranked] == ["stu_high", "stu_mid"]
    assert ranked[0].fit_score > ranked[1].fit_score
    assert all(c.status == "ELIGIBLE_RANKED" for c in ranked)


def test_eligible_but_low_fit_is_surfaced_not_hidden() -> None:
    result = rank_candidates("drive_003", [
        _candidate("stu_low_fit", eligible=True, required_skill_coverage=10,
                   semantic_similarity=5, project_relevance=10, readiness=15,
                   interview_score=10, academics=20),
    ])

    candidate = result.ranked[0]
    assert candidate.status == "ELIGIBLE_LOW_FIT"
    assert candidate.fit_score is not None
    assert candidate.rank == 1  # still ranked, just flagged as a weak match


def test_insufficient_features_yields_insufficient_data_not_zero_fit() -> None:
    # Only one low-weight feature present -> not enough evidence to rank.
    result = rank_candidates("drive_004", [
        _candidate("stu_thin", eligible=True, academics=70),
    ])

    candidate = result.ranked[0]
    assert candidate.status == "INSUFFICIENT_DATA"
    assert candidate.fit_score is None
    assert candidate.rank is None
    assert any("missing" in warning for warning in candidate.warnings)


def test_missing_features_flagged_and_excluded_not_imputed() -> None:
    result = rank_candidates("drive_005", [
        _candidate("stu_partial", eligible=True, required_skill_coverage=80,
                   semantic_similarity=70, readiness=75),
    ])

    candidate = result.ranked[0]
    assert candidate.status == "ELIGIBLE_RANKED"
    assert candidate.confidence < 1.0  # not all features present
    assert any("missing" in warning for warning in candidate.warnings)


def test_response_carries_model_and_feature_versions() -> None:
    result = rank_candidates("drive_006", [
        _candidate("stu_x", eligible=True, required_skill_coverage=60,
                   semantic_similarity=60, project_relevance=60, readiness=60),
    ])

    assert result.model_version
    assert result.feature_version
    assert result.generated_at is not None
