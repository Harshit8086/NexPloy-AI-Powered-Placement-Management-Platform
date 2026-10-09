// ============================================================
// CAMPUSES API — live only. Campus IDENTITY (id/name/city/state) from
// GET /api/campuses, /campuses/{id}. Per-campus METRICS (student counts,
// placement rate, CTC, offers, recruiters) are analytics aggregations and
// live behind GET /api/campuses/{id}/analytics/dashboard (see analytics.ts),
// never here, so nothing on this path is fabricated.
// ============================================================

import { apiRequest, ApiError } from "@/lib/api/client";
import { mapCampus, type CampusResponseDto } from "@/lib/api/mappers";
import type { CampusSummary, InstituteTotals } from "@/types";

/** Institute display label — a UI constant, not fabricated business data. */
export const INSTITUTE_NAME = "NEXPLOY Institute of Technology";

/**
 * No campus is active until one is resolved from the authenticated user (useAppStore seeds
 * activeCampusId from the signed-in user's campusId). There is deliberately no hardcoded default
 * campus id — an empty value yields empty state, never a fabricated campus.
 */
export const DEFAULT_CAMPUS_ID = "";

/** GET /api/campuses — campuses the signed-in user may see (OFFICER/ADMIN: all; else own). */
export async function listCampuses(): Promise<CampusSummary[]> {
  const dtos = await apiRequest<CampusResponseDto[]>("/campuses");
  return dtos.map(mapCampus);
}

/** GET /api/campuses/:id — a single campus's identity (404 → undefined). */
export async function getCampus(id: string): Promise<CampusSummary | undefined> {
  // An empty id would produce /campuses/, which matches no backend route; reject early instead.
  // Every caller already .catch()s and degrades to an empty state.
  if (!id) return Promise.reject(new Error("No active campus yet"));
  try {
    return mapCampus(await apiRequest<CampusResponseDto>(`/campuses/${id}`));
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return undefined;
    throw error;
  }
}

/**
 * Institute-wide rollup across every campus the caller may access, from GET /api/analytics/institute
 * (scoped server-side). Returns the totals block only; the per-campus comparison rows live on
 * getInstituteAnalytics() in analytics.ts.
 */
export async function getInstituteTotals(): Promise<InstituteTotals> {
  const institute = await apiRequest<{ totals: InstituteTotals }>(`/analytics/institute`);
  return institute.totals;
}

/** Request body for POST /api/campuses — mirrors the backend CreateCampusRequest (admin only). */
export interface CreateCampusRequest {
  id: string;
  name: string;
  city?: string | null;
  state?: string | null;
}

/** Request body for PUT /api/campuses/:id — the editable identity fields (admin only). */
export interface UpdateCampusRequest {
  name: string;
  city?: string | null;
  state?: string | null;
}

/** POST /api/campuses — admin creates a campus (id is client-supplied per the varchar-PK rule). */
export async function createCampus(request: CreateCampusRequest): Promise<CampusSummary> {
  return mapCampus(await apiRequest<CampusResponseDto>("/campuses", { method: "POST", body: request }));
}

/** PUT /api/campuses/:id — admin replaces a campus's editable fields. */
export async function updateCampus(id: string, request: UpdateCampusRequest): Promise<CampusSummary> {
  return mapCampus(
    await apiRequest<CampusResponseDto>(`/campuses/${id}`, { method: "PUT", body: request }),
  );
}

/** PATCH /api/campuses/:id/active — admin (de)activates a campus (soft-delete via active flag). */
export async function setCampusActive(id: string, active: boolean): Promise<CampusSummary> {
  return mapCampus(
    await apiRequest<CampusResponseDto>(`/campuses/${id}/active`, {
      method: "PATCH",
      body: { active },
    }),
  );
}
