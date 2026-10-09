from fastapi import FastAPI

from campuslink_ortools.schemas import ScheduleProblem, ScheduleResult
from campuslink_ortools.solver import optimize

app = FastAPI(title="CampusLink OR-Tools Scheduler", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "ortools-worker"}


@app.post("/internal/optimization/schedule", response_model=ScheduleResult)
def optimize_schedule(problem: ScheduleProblem) -> ScheduleResult:
    return optimize(problem)
