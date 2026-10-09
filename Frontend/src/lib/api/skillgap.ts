// ============================================================
// SKILL-GAP API — live only. POST /api/students/:id/skill-gap.
// The analysis is deterministic on the backend. `currentLevel` is
// optional per skill: when omitted, the backend sources it from the
// student's stored skills (My Skills), so a student's recorded
// proficiency drives the gap. Required levels are the role's targets,
// supplied by the caller (there is no role-requirements table yet).
// ============================================================

import { apiRequest } from "@/lib/api/client";
import type { SkillGap } from "@/types";

/** One skill's target level. Omit `currentLevel` to let stored skills fill it (else it overrides). */
export interface SkillGapSkillInput {
  skill: string;
  requiredLevel: number;
  currentLevel?: number;
}

export interface SkillGapAnalyzeInput {
  targetRole: string;
  skills: SkillGapSkillInput[];
}

/** Backend SkillGapResponse. Its items map 1:1 onto the frontend SkillGap type. */
interface SkillGapResponseDto {
  studentId: string;
  targetRole: string;
  skillGaps: SkillGap[];
}

/**
 * Runs the skill-gap analysis for a student. The backend fills each omitted `currentLevel` from the
 * student's stored skills, so the result is grounded in recorded proficiency — never fabricated.
 */
export async function analyzeSkillGap(
  studentId: string,
  input: SkillGapAnalyzeInput,
): Promise<SkillGap[]> {
  const dto = await apiRequest<SkillGapResponseDto>(`/students/${studentId}/skill-gap`, {
    method: "POST",
    body: input,
  });
  return dto.skillGaps;
}

/** A skill the student already meets or exceeds for the role — surfaced as a strength. */
export interface SkillMatch {
  skill: string;
  currentLevel: number;
  requiredLevel: number;
  /** Verification status of the student's stored skill (VERIFIED / EVIDENCED / SELF_DECLARED). */
  verificationStatus?: string | null;
}

/**
 * Server-resolved analysis (GET). The backend resolves required levels from the role-requirements
 * catalog and current levels from the student's stored skills — the client supplies nothing. The
 * three flags let the UI pick a precise empty state instead of inferring one from list lengths.
 * When a drive's role has no catalog entry but the drive lists its own required skills,
 * {@code driveSkillsFallback} is true and the fit is a presence-based match against those skills
 * (no level targets), while {@code roleRequirementsAvailable} stays false.
 */
export interface RoleSkillGap {
  studentId: string;
  targetRole: string;
  hasTargetRole: boolean;
  hasSkills: boolean;
  roleRequirementsAvailable: boolean;
  driveSkillsFallback: boolean;
  overallMatch: number; // 0-100, weighted level fit or percentage presence match
  strengths: SkillMatch[];
  gaps: SkillGap[];
  driveId?: string | null;
  companyName?: string | null;
  coveredSkillsCount?: number | null;
  totalSkillsCount?: number | null;
  analysisType?: "LEVEL_BASED" | "PRESENCE_ONLY" | "EMPTY" | string;
}

/** Runs the skill-gap analysis for the student, optionally scoped to a specific drive. */
export function getRoleSkillGap(studentId: string, driveId?: string): Promise<RoleSkillGap> {
  const query = driveId ? `?driveId=${encodeURIComponent(driveId)}` : "";
  return apiRequest<RoleSkillGap>(`/students/${studentId}/skill-gap${query}`);
}

/**
 * Skill fit for a specific drive (match info only — never gates applying; eligibility is separate).
 */
export function getDriveSkillFit(studentId: string, driveId: string): Promise<RoleSkillGap> {
  return apiRequest<RoleSkillGap>(`/students/${studentId}/skill-gap/drives/${driveId}`);
}
