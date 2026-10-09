"""Candidate ranking for a drive.

Ranking runs *after* Spring Boot's deterministic eligibility gate (SECURITY.md, spec section
5.3). The invariants this module enforces:

- the model never decides eligibility; it consumes the `eligible` flag Spring already computed;
- an ineligible candidate is never ranked as eligible — it is returned as NOT_ELIGIBLE with no
  fit score and no rank, so it cannot outrank an eligible candidate;
- a recruiter-facing result distinguishes four states:
  ELIGIBLE_RANKED / ELIGIBLE_LOW_FIT / NOT_ELIGIBLE / INSUFFICIENT_DATA;
- missing features are recorded as warnings and excluded from the score, never imputed as zero
  fit ("treat incomplete evidence as needing more information, not as low potential");
- features are fit/merit signals only, never protected attributes or their proxies.

This is a transparent, deterministic baseline standing in for a learned ranking model. It exists
so the eligibility-gate contract and explainability shape are correct before a model is trained.
"""

from datetime import datetime, timezone

from campuslink_ml.embedding import semantic_similarity_pct
from campuslink_ml.schemas import (
    Factor,
    MatchingResponse,
    RankCandidate,
    RankCandidateFeatures,
    RankedCandidate,
)

MODEL_VERSION = "ranking-baseline-2026-01"
FEATURE_VERSION = "match-features-3"

# Each feature contributes a weighted fit in [0, 1]; weights sum to 1.0. Certification
# relevance and the historical interview pass rate are supporting merit signals — weighted
# below skills/semantics. The pass rate covers only *past completed* interviews, never the
# drive being ranked, so a drive's own outcomes cannot feed back into its ranking.
FEATURE_WEIGHTS: dict[str, float] = {
    "required_skill_coverage": 0.25,
    "semantic_similarity": 0.17,
    "project_relevance": 0.12,
    "readiness": 0.12,
    "interview_score": 0.09,
    "academics": 0.07,
    "certification_relevance": 0.10,
    "historical_interview_pass_rate": 0.08,
}

# Below this fit, an eligible candidate is surfaced as ELIGIBLE_LOW_FIT rather than hidden.
LOW_FIT_THRESHOLD = 0.40

# Rank only when at least this share of feature weight is backed by real data; otherwise the
# candidate is INSUFFICIENT_DATA rather than ranked on a sliver of evidence.
MIN_CONFIDENCE_TO_RANK = 0.30


def rank_candidates(
    drive_id: str,
    candidates: list[RankCandidate],
    required_skills: list[str] | None = None,
) -> MatchingResponse:
    scored: list[RankedCandidate] = []
    for candidate in candidates:
        scored.append(_evaluate(candidate, required_skills or []))

    # Assign 1-based ranks over the candidates that carry a fit score, best first. Ineligible
    # and insufficient-data candidates never receive a rank, so they cannot appear as ranked.
    rankable = sorted(
        (c for c in scored if c.fit_score is not None),
        key=lambda c: c.fit_score,
        reverse=True,
    )
    ranks = {c.student_id: position for position, c in enumerate(rankable, start=1)}
    ordered = [
        c.model_copy(update={"rank": ranks.get(c.student_id)}) for c in scored
    ]
    # Present ranked candidates first (by rank), then the rest in stable order.
    ordered.sort(key=lambda c: (c.rank is None, c.rank if c.rank is not None else 0))

    return MatchingResponse(
        drive_id=drive_id,
        ranked=ordered,
        model_version=MODEL_VERSION,
        feature_version=FEATURE_VERSION,
        generated_at=_now(),
    )


def _evaluate(candidate: RankCandidate, required_skills: list[str]) -> RankedCandidate:
    # Compute semantic_similarity from skill text when the caller didn't supply it. This is the
    # deterministic lexical-embedding cosine; a supplied value always takes precedence.
    features = candidate.features
    effective_semantic = features.semantic_similarity
    if effective_semantic is None:
        effective_semantic = semantic_similarity_pct(required_skills, candidate.skills)
        if effective_semantic is not None:
            features = features.model_copy(update={"semantic_similarity": effective_semantic})

    # Hard eligibility gate first: an ineligible candidate is never scored or ranked.
    if not candidate.eligible:
        return RankedCandidate(
            student_id=candidate.student_id,
            status="NOT_ELIGIBLE",
            fit_score=None,
            rank=None,
            top_factors=[],
            confidence=0.0,
            semantic_similarity=effective_semantic,
            warnings=["Excluded by a deterministic eligibility rule; not ranked"],
        )

    contributions, warnings = _contributions(features)
    available_weight = sum(FEATURE_WEIGHTS[name] for name in contributions)

    if not contributions or available_weight < MIN_CONFIDENCE_TO_RANK:
        return RankedCandidate(
            student_id=candidate.student_id,
            status="INSUFFICIENT_DATA",
            fit_score=None,
            rank=None,
            top_factors=[],
            confidence=round(available_weight, 4),
            semantic_similarity=effective_semantic,
            warnings=warnings + ["Too little fit evidence to rank; collect more before scoring"],
        )

    fit = sum(contributions[name] * FEATURE_WEIGHTS[name] for name in contributions) / available_weight
    status = "ELIGIBLE_RANKED" if fit >= LOW_FIT_THRESHOLD else "ELIGIBLE_LOW_FIT"

    return RankedCandidate(
        student_id=candidate.student_id,
        status=status,
        fit_score=round(fit, 4),
        rank=None,  # assigned by the caller once all candidates are scored
        top_factors=_top_factors(contributions),
        confidence=round(available_weight, 4),
        semantic_similarity=effective_semantic,
        warnings=warnings,
    )


def _contributions(features: RankCandidateFeatures) -> tuple[dict[str, float], list[str]]:
    """Each present feature yields a fit contribution in [0, 1] (its 0-100 value scaled).
    Missing features are recorded as warnings and left out of the score, never imputed."""
    contributions: dict[str, float] = {}
    warnings: list[str] = []
    for name in FEATURE_WEIGHTS:
        value = getattr(features, name)
        if value is None:
            warnings.append(f"Feature '{name}' missing; excluded from the fit score rather than imputed")
        else:
            contributions[name] = _clamp(value / 100.0)
    return contributions, warnings


def _top_factors(contributions: dict[str, float], limit: int = 3) -> list[Factor]:
    factors = [
        Factor(
            name=name,
            impact=round(FEATURE_WEIGHTS[name] * value, 4),
            direction="increases-fit" if value > 0 else "neutral",
        )
        for name, value in contributions.items()
    ]
    factors.sort(key=lambda factor: factor.impact, reverse=True)
    return factors[:limit]


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def _now() -> datetime:
    return datetime.now(timezone.utc)
