// ============================================================
// RECRUITERS API — live only. GET /api/recruiters, /recruiters/{id},
// /recruiters/me (campus-scoped server-side); an empty DB yields [].
// ============================================================

import { apiRequest, ApiError } from "@/lib/api/client";
import { mapRecruiter, type RecruiterResponseDto } from "@/lib/api/mappers";
import type { Recruiter, RecruiterSettings } from "@/types";

/** GET /api/recruiters */
export async function listRecruiters(): Promise<Recruiter[]> {
  const dtos = await apiRequest<RecruiterResponseDto[]>("/recruiters");
  return dtos.map(mapRecruiter);
}

/** GET /api/recruiters/:id */
export async function getRecruiter(id: string): Promise<Recruiter | undefined> {
  try {
    return mapRecruiter(await apiRequest<RecruiterResponseDto>(`/recruiters/${id}`));
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return undefined;
    throw error;
  }
}

/**
 * GET /api/recruiters/me — the signed-in recruiter's own company profile (resolved server-side
 * from the auth principal, username == email).
 */
export async function getActiveRecruiter(): Promise<Recruiter> {
  return mapRecruiter(await apiRequest<RecruiterResponseDto>("/recruiters/me"));
}

/**
 * Editable recruiter profile fields (PUT /api/recruiters/me). Every field is optional — an
 * omitted/undefined field means "leave unchanged", mirroring the backend's partial-patch DTO.
 * Identity/scope fields (id, email, campusId, active) are intentionally not editable.
 */
export interface RecruiterUpdateInput {
  name?: string;
  phone?: string;
  designation?: string;
  logoInitial?: string;
  companyName?: string;
  companyIndustry?: string;
  companyWebsite?: string;
  companyLocation?: string;
  companyDescription?: string;
  companySize?: string;
  companyLinkedin?: string;
  companyTwitter?: string;
  companyGithub?: string;
}

/** PUT /api/recruiters/me — updates the signed-in recruiter's own profile and returns it. */
export async function updateActiveRecruiter(input: RecruiterUpdateInput): Promise<Recruiter> {
  const dto = await apiRequest<RecruiterResponseDto>("/recruiters/me", { method: "PUT", body: input });
  return mapRecruiter(dto);
}

/**
 * PUT /api/recruiters/:id — officer/admin edits any recruiter within campus reach (same bounded
 * self-service fields as PUT /me). Campus access + officer/admin role are enforced server-side.
 */
export async function adminUpdateRecruiter(
  id: string,
  input: RecruiterUpdateInput,
): Promise<Recruiter> {
  return mapRecruiter(
    await apiRequest<RecruiterResponseDto>(`/recruiters/${id}`, { method: "PUT", body: input }),
  );
}

/** PATCH /api/recruiters/:id/active — officer/admin (de)activates a recruiter (soft-delete). */
export async function setRecruiterActive(id: string, active: boolean): Promise<Recruiter> {
  return mapRecruiter(
    await apiRequest<RecruiterResponseDto>(`/recruiters/${id}/active`, {
      method: "PATCH",
      body: { active },
    }),
  );
}

/** Partial-patch settings input — an omitted field leaves that setting unchanged. */
export type RecruiterSettingsInput = Partial<RecruiterSettings>;

/** GET /api/recruiters/me/settings — the signed-in recruiter's persisted settings (lazily created). */
export async function getRecruiterSettings(): Promise<RecruiterSettings> {
  return apiRequest<RecruiterSettings>("/recruiters/me/settings");
}

/** PUT /api/recruiters/me/settings — persists a partial settings patch and returns the full settings. */
export async function updateRecruiterSettings(input: RecruiterSettingsInput): Promise<RecruiterSettings> {
  return apiRequest<RecruiterSettings>("/recruiters/me/settings", { method: "PUT", body: input });
}
