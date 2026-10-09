from datetime import datetime

from pydantic import BaseModel, Field


class ReadinessAssessments(BaseModel):
    """Raw dimension inputs on a 0-100 scale. Any dimension may be omitted (None);
    missingness is recorded rather than imputed as a low score."""

    academic: float | None = Field(default=None, ge=0, le=100)
    aptitude: float | None = Field(default=None, ge=0, le=100)
    communication: float | None = Field(default=None, ge=0, le=100)
    projects: float | None = Field(default=None, ge=0, le=100)
    interview: float | None = Field(default=None, ge=0, le=100)


class ReadinessRequest(BaseModel):
    request_id: str
    campus_id: str
    placement_cycle_id: str
    model_policy_version: str
    student_id: str
    assessments: ReadinessAssessments


class Factor(BaseModel):
    name: str
    impact: float
    direction: str


class ReadinessResponse(BaseModel):
    student_id: str
    score: float
    category: str
    breakdown: dict[str, float]
    top_factors: list[Factor]
    confidence: float
    warnings: list[str] = Field(default_factory=list)
    model_version: str
    feature_version: str
    generated_at: datetime


class RiskSignals(BaseModel):
    """Support-oriented signals on a 0-100 scale, where higher means more concerning.
    All optional; missingness is recorded, never imputed. These are deliberately
    behavioural/preparation signals, not protected attributes or academic proxies for them."""

    readiness_score: float | None = Field(default=None, ge=0, le=100)
    backlog_count: int | None = Field(default=None, ge=0)
    applications_submitted: int | None = Field(default=None, ge=0)
    interviews_attended: int | None = Field(default=None, ge=0)
    interviews_missed: int | None = Field(default=None, ge=0)
    days_since_last_activity: int | None = Field(default=None, ge=0)
    mock_interviews_completed: int | None = Field(default=None, ge=0)


class RiskRequest(BaseModel):
    request_id: str
    campus_id: str
    placement_cycle_id: str
    model_policy_version: str
    student_id: str
    signals: RiskSignals


class RiskResponse(BaseModel):
    student_id: str
    support_probability: float
    band: str
    top_factors: list[Factor]
    recommended_interventions: list[str]
    confidence: float
    warnings: list[str] = Field(default_factory=list)
    model_version: str
    feature_version: str
    generated_at: datetime


class RankCandidateFeatures(BaseModel):
    """Fit features on a 0-100 scale, higher is a stronger fit. All optional; missingness is
    recorded, never imputed. Deliberately fit/merit signals only, not protected attributes."""

    required_skill_coverage: float | None = Field(default=None, ge=0, le=100)
    semantic_similarity: float | None = Field(default=None, ge=0, le=100)
    project_relevance: float | None = Field(default=None, ge=0, le=100)
    readiness: float | None = Field(default=None, ge=0, le=100)
    interview_score: float | None = Field(default=None, ge=0, le=100)
    academics: float | None = Field(default=None, ge=0, le=100)
    certification_relevance: float | None = Field(default=None, ge=0, le=100)
    # Share of the candidate's completed past interviews that passed (0-100), excluding the
    # drive being ranked so a drive's own outcomes never feed back into its ranking.
    historical_interview_pass_rate: float | None = Field(default=None, ge=0, le=100)


class RankCandidate(BaseModel):
    """A single candidate to rank. `eligible` is decided upstream by Spring's deterministic
    eligibility gate; the ranking model consumes it and never overrides it. `skills` are the
    candidate's skill terms; when present alongside the drive's required skills, the service
    computes semantic_similarity itself (a deterministic lexical embedding cosine) rather than
    requiring the caller to supply that feature."""

    student_id: str
    eligible: bool
    features: RankCandidateFeatures
    skills: list[str] = Field(default_factory=list)


class MatchingRequest(BaseModel):
    request_id: str
    campus_id: str
    placement_cycle_id: str
    model_policy_version: str
    drive_id: str
    candidates: list[RankCandidate]
    # The drive's required skill terms, used to compute each candidate's semantic_similarity.
    required_skills: list[str] = Field(default_factory=list)


class RankedCandidate(BaseModel):
    student_id: str
    # One of ELIGIBLE_RANKED / ELIGIBLE_LOW_FIT / NOT_ELIGIBLE / INSUFFICIENT_DATA.
    status: str
    fit_score: float | None
    rank: int | None
    top_factors: list[Factor]
    confidence: float
    # The semantic-similarity value actually used (supplied or service-computed), 0-100, for display.
    semantic_similarity: float | None = None
    warnings: list[str] = Field(default_factory=list)


class MatchingResponse(BaseModel):
    drive_id: str
    ranked: list[RankedCandidate]
    model_version: str
    feature_version: str
    generated_at: datetime


class EmbeddingRequest(BaseModel):
    """Skill terms to embed for storage. The service returns the same normalized vector it uses
    internally for semantic similarity, so cosine distance over stored vectors matches it."""

    terms: list[str] = Field(default_factory=list)


class EmbeddingResponse(BaseModel):
    # The L2-normalized embedding, or None when no terms were supplied (missing evidence).
    embedding: list[float] | None = None
    dim: int


class AttendanceSignals(BaseModel):
    """Slot/logistics signals for an attendance estimate. All optional; missingness is recorded,
    never imputed. Deliberately logistics/behaviour signals, not protected attributes."""

    # Historical share of invited slots the student actually attended, 0-100.
    prior_attendance_rate: float | None = Field(default=None, ge=0, le=100)
    # Hours between notification and the slot; more lead time helps attendance.
    notification_lead_hours: float | None = Field(default=None, ge=0)
    # Days to the nearest exam; a near exam depresses attendance.
    days_to_nearest_exam: int | None = Field(default=None, ge=0)
    # Slot start hour, 0-23; very early/late slots attend worse.
    slot_hour: int | None = Field(default=None, ge=0, le=23)
    # True if the slot falls on a weekend.
    is_weekend: bool | None = Field(default=None)
    # Travel time to the venue in minutes; longer travel depresses attendance.
    travel_minutes: int | None = Field(default=None, ge=0)


class AttendanceRequest(BaseModel):
    request_id: str
    campus_id: str
    placement_cycle_id: str
    model_policy_version: str
    student_id: str
    slot_id: str
    signals: AttendanceSignals


class AttendanceResponse(BaseModel):
    student_id: str
    slot_id: str
    attendance_probability: float
    band: str
    top_factors: list[Factor]
    confidence: float
    warnings: list[str] = Field(default_factory=list)
    model_version: str
    feature_version: str
    generated_at: datetime


class DemandSeriesPoint(BaseModel):
    """One historical observation: the demand count for a cycle. Cycles are supplied in
    chronological order; the label is opaque to the model (e.g. '2024', '2025-spring')."""

    placement_cycle_id: str
    count: float = Field(ge=0)


class DemandSeries(BaseModel):
    """A named entity (a skill, branch, recruiter, or package band) and its demand history."""

    key: str
    # One of SKILL / BRANCH / RECRUITER / PACKAGE_BAND; opaque to the forecaster.
    dimension: str
    history: list[DemandSeriesPoint] = Field(default_factory=list)


class DemandRequest(BaseModel):
    request_id: str
    campus_id: str
    placement_cycle_id: str
    model_policy_version: str
    # The cycle being forecast, e.g. the upcoming one.
    target_cycle_id: str
    series: list[DemandSeries] = Field(default_factory=list)


class DemandForecast(BaseModel):
    key: str
    dimension: str
    # None when there is too little history to forecast.
    forecast: float | None
    # Prediction interval; None alongside forecast when history is insufficient.
    lower: float | None
    upper: float | None
    trend: str
    confidence: float
    warnings: list[str] = Field(default_factory=list)


class DemandResponse(BaseModel):
    target_cycle_id: str
    forecasts: list[DemandForecast]
    model_version: str
    feature_version: str
    generated_at: datetime
