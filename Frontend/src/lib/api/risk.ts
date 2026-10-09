// ============================================================
// RISK API — live only. Per-student and per-campus risk come from the
// backend; there is no un-scoped cross-student endpoint, so nothing here
// is fabricated. An empty DB yields empty lists / undefined.
// ============================================================

import { apiRequest, ApiError } from "@/lib/api/client";
import { mapRiskEntry, type RiskEntryResponseDto } from "@/lib/api/mappers";
import type { RiskEntry } from "@/types";

/**
 * GET /api/campuses/:campusId/risk-entries — the latest risk entry per student on a campus
 * (the Risk Radar roll-up), scoped server-side to campuses the caller may access.
 */
export async function listRiskEntriesByCampus(campusId: string): Promise<RiskEntry[]> {
  const dtos = await apiRequest<RiskEntryResponseDto[]>(`/campuses/${campusId}/risk-entries`);
  return dtos.map(mapRiskEntry);
}

/**
 * POST /api/campuses/:campusId/risk-entries/assess-all — triggers batch risk prediction
 * across all students on the campus, updating their risk entries in one shot.
 */
export async function assessCampusRisk(campusId: string): Promise<RiskEntry[]> {
  const dtos = await apiRequest<RiskEntryResponseDto[]>(`/campuses/${campusId}/risk-entries/assess-all`, {
    method: "POST",
  });
  return dtos.map(mapRiskEntry);
}

/**
 * GET /api/students/:studentId/risk/entries — persisted risk history, newest first. We take the
 * most recent snapshot (index 0). No history (or an unknown student → 404) surfaces as undefined.
 */
export async function getStudentRisk(studentId: string): Promise<RiskEntry | undefined> {
  try {
    const dtos = await apiRequest<RiskEntryResponseDto[]>(`/students/${studentId}/risk/entries`);
    if (!dtos || dtos.length === 0) return undefined;
    return mapRiskEntry(dtos[0]);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return undefined;
    throw error;
  }
}

/**
 * Optional overrides for behavioural activity signals. Blank fields are derived server-side from
 * saved applications, interviews, and mock interviews. Readiness and backlog count also come from
 * the student record. Mirrors RiskPredictRequestBody.
 */
export interface RiskSignalsInput {
  applicationsSubmitted?: number;
  interviewsAttended?: number;
  interviewsMissed?: number;
  daysSinceLastActivity?: number;
  mockInterviewsCompleted?: number;
}

/** The ML service's raw prediction, as returned by POST /api/students/:id/risk (snake_case DTO). */
export interface RiskPredictionResult {
  studentId: string;
  /** Raw model band: LOW / MONITOR / NEEDS_SUPPORT / INSUFFICIENT_DATA (or legacy HIGH/MEDIUM). */
  band: string;
  supportProbability: number;
  recommendedInterventions: string[];
  modelVersion: string;
}

interface RiskPredictionResponseDto {
  student_id: string;
  support_probability: number;
  band: string;
  top_factors: { name: string; impact: number; direction: string }[];
  recommended_interventions: string[];
  confidence: number;
  warnings: string[];
  model_version: string;
  feature_version: string;
  generated_at: string;
}

/**
 * POST /api/students/:studentId/risk — runs a placement-risk prediction with the given activity
 * signals and persists it server-side as an append-only risk entry (plus the campus roll-up).
 * Returns the ML result; call listRiskEntriesByCampus again to see the new entry in the radar.
 */
export async function predictStudentRisk(
  studentId: string,
  signals: RiskSignalsInput = {},
): Promise<RiskPredictionResult> {
  const dto = await apiRequest<RiskPredictionResponseDto>(`/students/${studentId}/risk`, {
    method: "POST",
    body: signals,
  });
  return {
    studentId: dto.student_id,
    band: dto.band,
    supportProbability: dto.support_probability,
    recommendedInterventions: dto.recommended_interventions ?? [],
    modelVersion: dto.model_version,
  };
}
