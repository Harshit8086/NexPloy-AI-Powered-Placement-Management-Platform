from fastapi import FastAPI

from campuslink_ml.attendance import predict_attendance
from campuslink_ml.demand import forecast_demand
from campuslink_ml.embedding import embed_terms, embedding_dim
from campuslink_ml.matching import rank_candidates
from campuslink_ml.readiness import score_readiness
from campuslink_ml.risk import score_risk
from campuslink_ml.schemas import (
    AttendanceRequest,
    AttendanceResponse,
    DemandRequest,
    DemandResponse,
    EmbeddingRequest,
    EmbeddingResponse,
    MatchingRequest,
    MatchingResponse,
    ReadinessRequest,
    ReadinessResponse,
    RiskRequest,
    RiskResponse,
)

app = FastAPI(title="CampusLink ML Inference", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "ml-inference"}


@app.post("/internal/ml/v1/readiness:score", response_model=ReadinessResponse)
def readiness_score(request: ReadinessRequest) -> ReadinessResponse:
    return score_readiness(request.student_id, request.assessments)


@app.post("/internal/ml/v1/risk:predict", response_model=RiskResponse)
def risk_predict(request: RiskRequest) -> RiskResponse:
    return score_risk(request.student_id, request.signals)


@app.post("/internal/ml/v1/matching:rank", response_model=MatchingResponse)
def matching_rank(request: MatchingRequest) -> MatchingResponse:
    return rank_candidates(request.drive_id, request.candidates, request.required_skills)


@app.post("/internal/ml/v1/embedding:embed", response_model=EmbeddingResponse)
def embedding_embed(request: EmbeddingRequest) -> EmbeddingResponse:
    return EmbeddingResponse(embedding=embed_terms(request.terms), dim=embedding_dim())


@app.post("/internal/ml/v1/attendance:predict", response_model=AttendanceResponse)
def attendance_predict(request: AttendanceRequest) -> AttendanceResponse:
    return predict_attendance(request.student_id, request.slot_id, request.signals)


@app.post("/internal/ml/v1/demand:forecast", response_model=DemandResponse)
def demand_forecast(request: DemandRequest) -> DemandResponse:
    return forecast_demand(request.target_cycle_id, request.series)
