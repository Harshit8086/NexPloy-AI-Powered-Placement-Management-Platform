// ============================================================
// RECOMMENDATIONS API — live only. The explainable recommendation layer over
// the deterministic matching + eligibility engines.
//
//   GET  /api/applications/:applicationId/recommendation            — read (serves persisted AI text)
//   POST /api/applications/:applicationId/recommendation/regenerate — force a fresh AI generation
//   GET  /api/drives/:driveId/candidate-recommendations             — ranked list, persisted text only
//
// All scores, eligibility, and the recommendation type are deterministic on the
// backend; the LLM only writes the human-readable explanation, and any AI failure
// degrades to a deterministic fallback — these calls never fabricate evidence.
// ============================================================

import { apiRequest } from "@/lib/api/client";
import {
  mapCandidateRecommendation,
  mapRecommendation,
  type CandidateRecommendationResponseDto,
  type RecommendationResponseDto,
} from "@/lib/api/mappers";
import type { CandidateRecommendation, Recommendation } from "@/types";

/** GET the explainable recommendation for one application (owning student or a drive manager). */
export async function getApplicationRecommendation(
  applicationId: string,
): Promise<Recommendation> {
  const dto = await apiRequest<RecommendationResponseDto>(
    `/applications/${applicationId}/recommendation`,
  );
  return mapRecommendation(dto);
}

/** Force a fresh AI generation for one application, then return the updated recommendation. */
export async function regenerateApplicationRecommendation(
  applicationId: string,
): Promise<Recommendation> {
  const dto = await apiRequest<RecommendationResponseDto>(
    `/applications/${applicationId}/recommendation/regenerate`,
    { method: "POST" },
  );
  return mapRecommendation(dto);
}

/** GET the ranked candidate recommendations for a drive (managers only; persisted text only). */
export async function listCandidateRecommendations(
  driveId: string,
): Promise<CandidateRecommendation[]> {
  const dtos = await apiRequest<CandidateRecommendationResponseDto[]>(
    `/drives/${driveId}/candidate-recommendations`,
  );
  return dtos.map(mapCandidateRecommendation);
}
