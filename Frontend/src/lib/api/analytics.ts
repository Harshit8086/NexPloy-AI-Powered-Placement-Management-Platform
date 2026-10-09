// ============================================================
// ANALYTICS API — live only. Every figure is a server-side aggregation
// over real students/drives/offers; there is no mock fallback. An empty
// database yields empty series, never fabricated statistics.
// ============================================================

import { apiRequest } from "@/lib/api/client";
import { listCampuses } from "@/lib/api/campuses";
import type {
  CampusAnalytics,
  InstituteAnalytics,
  SkillDemand,
  SkillSupply,
} from "@/types";

/**
 * GET /api/campuses/:campusId/analytics/dashboard. The backend DashboardAnalyticsResponse is
 * shaped 1:1 to CampusAnalytics (snapshot + the chart series), so the live payload is consumed
 * directly. Every figure is a live aggregation over the campus's students/drives/offers/applications.
 */
export async function getAnalyticsForCampus(campusId: string): Promise<CampusAnalytics> {
  // activeCampusId seeds to "" until the signed-in user's campus resolves; an empty segment
  // would produce /campuses//analytics/dashboard, which fails CORS preflight before reaching
  // the server. Reject early instead — every caller already .catch()s and degrades to empty state.
  if (!campusId) return Promise.reject(new Error("No active campus yet"));
  return apiRequest<CampusAnalytics>(`/campuses/${campusId}/analytics/dashboard`);
}

/**
 * Every accessible campus's full analytics bundle, keyed by campus id. Fans out over the campuses
 * the caller may see (GET /api/campuses) and fetches each dashboard; used by cross-campus views
 * that need the per-campus chart series, not just the institute rollup.
 */
export async function listAllCampusAnalytics(): Promise<Record<string, CampusAnalytics>> {
  const campuses = await listCampuses();
  const entries = await Promise.all(
    campuses.map(async (c) => [c.id, await getAnalyticsForCampus(c.id)] as const),
  );
  return Object.fromEntries(entries);
}

export interface SkillAnalytics {
  demand: SkillDemand[];
  supply: SkillSupply[];
}

/**
 * GET /api/campuses/:campusId/analytics/skills — skill demand (active-drive requirements) paired
 * with cohort coverage. Both are live aggregations; an empty campus yields empty lists.
 */
export async function getSkillAnalytics(campusId: string): Promise<SkillAnalytics> {
  // Same empty-campus guard as getAnalyticsForCampus above.
  if (!campusId) return Promise.reject(new Error("No active campus yet"));
  return apiRequest<SkillAnalytics>(`/campuses/${campusId}/analytics/skills`);
}

/**
 * GET /api/analytics/institute — institute totals + per-campus comparison rows, scoped server-side
 * to the campuses the caller may access (all for OFFICER/ADMIN, own campus otherwise).
 */
export async function getInstituteAnalytics(): Promise<InstituteAnalytics> {
  return apiRequest<InstituteAnalytics>(`/analytics/institute`);
}

export type { CampusAnalytics };
