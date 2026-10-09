# CampusLink OR-Tools Worker

This service solves conflict-free placement schedules for the Spring Boot backend.

## Run

```powershell
python -m pip install -r requirements.txt
$env:PYTHONPATH = "src"
python -m uvicorn campuslink_ortools.main:app --host 0.0.0.0 --port 8090
```

Health check:

```text
GET http://localhost:8090/health
```

Optimization endpoint:

```text
POST http://localhost:8090/internal/optimization/schedule
```

The worker applies hard venue, panel-member, student, capacity, and blocked-slot constraints. It returns structured solver status, selected assignments, hard-violation count, soft score, reason codes, and evidence. It never writes to the database or makes authorization decisions.

## CampusLink ML Inference (separate service)

The `campuslink_ml` package is a distinct FastAPI app for versioned, explainable predictions (readiness, risk, candidate ranking, interview attendance, and demand forecasting). It shares this directory for prototype convenience only; the two apps have different failure modes and release policies and should deploy separately.

```powershell
$env:PYTHONPATH = "src"
python -m uvicorn campuslink_ml.main:app --host 0.0.0.0 --port 8091
```

Endpoints:

```text
POST http://localhost:8091/internal/ml/v1/readiness:score
POST http://localhost:8091/internal/ml/v1/risk:predict
POST http://localhost:8091/internal/ml/v1/matching:rank
POST http://localhost:8091/internal/ml/v1/attendance:predict
POST http://localhost:8091/internal/ml/v1/demand:forecast
```

The readiness scorer is a transparent, deterministic weighted baseline standing in for a trained, calibrated model. It scores the eight assessment dimensions, renormalises weights over the dimensions actually supplied (missing dimensions are reported as warnings, never imputed as zero), and returns a category, per-dimension breakdown, top contributing factors, and a confidence equal to the fraction of total weight covered by real data.

The risk predictor is support-oriented: it estimates the probability a student would benefit from additional placement support, returns a band (LOW / MONITOR / NEEDS_SUPPORT / INSUFFICIENT_DATA) and recommended interventions tied to the factors driving the risk. It never emits an exclusion decision, and no signals yields INSUFFICIENT_DATA rather than high risk.

The candidate-ranking endpoint runs *after* Spring's deterministic eligibility gate. Spring computes eligibility and passes each candidate's `eligible` flag; the ranker consumes it and never overrides it, so an ineligible candidate is returned as NOT_ELIGIBLE with no fit score and no rank and can never outrank an eligible one. Each candidate is labelled ELIGIBLE_RANKED, ELIGIBLE_LOW_FIT, NOT_ELIGIBLE, or INSUFFICIENT_DATA; low-fit eligible candidates are surfaced, not hidden, and thin evidence yields INSUFFICIENT_DATA rather than a zero fit.

The attendance predictor is advisory only: it estimates the probability a shortlisted student attends a proposed slot and returns a band (LIKELY / UNCERTAIN / AT_RISK / INSUFFICIENT_DATA) with factors. It can inform the OR-Tools *soft* objective (prefer slots students are likely to attend) but never overrides a *hard* scheduling constraint — Spring and the solver own feasibility. With no signals it returns a neutral 0.5 prior at zero confidence rather than a confident 0 or 1, so the solver is never steered by fabricated certainty.

The demand forecaster is analytics/planning input only: it forecasts demand for a skill, branch, recruiter, or package band in an upcoming cycle from its per-cycle history. It fits a time-ordered trend (never peeking at the target cycle), always returns a prediction interval that widens on short or noisy series, floors demand at zero, labels the trend RISING / FALLING / STABLE, and returns no forecast (rather than a fabricated number) when history is too short. It carries no student identity and never drives student exclusion.

All five scorers use no protected attributes and never write to the database or make authorization decisions.

