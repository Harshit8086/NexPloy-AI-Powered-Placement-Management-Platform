// ============================================================
// MATCHES API — live only. The explainable ranked candidate pool for a
// drive comes from GET /api/drives/:driveId/matches (a real, server-side
// ranking over stored student/drive data). An empty drive yields [].
// ============================================================

import { apiRequest } from "@/lib/api/client";
import { mapMatch, type MatchResponseDto } from "@/lib/api/mappers";
import type { CandidateMatch } from "@/types";

/** GET /api/drives/:driveId/matches — explainable ranked candidate pool. */
export async function listMatchesForDrive(driveId: string): Promise<CandidateMatch[]> {
  const dtos = await apiRequest<MatchResponseDto[]>(`/drives/${driveId}/matches`);
  return dtos.map(mapMatch);
}

/** One student's outcome in a batch auto-shortlist run (mirrors the backend Outcome record). */
export interface AutoShortlistOutcome {
  studentId: string;
  result: "SHORTLISTED" | "SKIPPED_INELIGIBLE" | "SKIPPED_LOW_FIT";
  overallFit: number;
  eligible: boolean;
}

/** Summary of a batch auto-shortlist run (mirrors the backend AutoShortlistResponse). */
export interface AutoShortlistResult {
  driveId: string;
  minFit: number;
  shortlisted: number;
  skippedIneligible: number;
  skippedLowFit: number;
  outcomes: AutoShortlistOutcome[];
}

/**
 * POST /api/drives/:driveId/matches/auto-shortlist — eligibility-gated batch shortlist. The
 * backend re-verifies eligibility server-side (the hard gate) and shortlists eligible applicants
 * whose match fit is at or above `minFit`; ineligible/low-fit applicants are skipped and reported.
 */
export function autoShortlistDrive(driveId: string, minFit: number): Promise<AutoShortlistResult> {
  return apiRequest<AutoShortlistResult>(`/drives/${driveId}/matches/auto-shortlist`, {
    method: "POST",
    body: { minFit },
  });
}
