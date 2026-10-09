// ============================================================
// MENTORS / INTERVENTIONS API
//
// Live against the backend mentor + intervention modules. There is no mock
// branch: with no seeded data these endpoints return empty lists and the UI
// shows empty states.
//
// What the backend actually models (and what it does NOT):
//   • A mentor is a UserAccount (role MENTOR), addressed by username. There is
//     NO mentor-profile entity, so there is no display name / department /
//     roster endpoint — a mentor's caseload is its assignments, nothing more.
//   • Assignments bind a mentorUsername ↔ studentId on a campus.
//   • Intervention plans are created by OFFICER/ADMIN (starting PROPOSED); a
//     MENTOR can only activate / complete / toggle actions on their own plans.
//     The old "mentor creates a plan" flow is therefore gone — it never had a
//     backend and the server rejects it.
// ============================================================

import { apiRequest, ApiError } from "@/lib/api/client";
import {
  mapInterventionPlan,
  mapMentorAssignment,
  type InterventionPlanResponseDto,
  type MentorAssignmentResponseDto,
} from "@/lib/api/mappers";
import type { InterventionPlan, MentorAssignment } from "@/types";

/**
 * GET /api/mentor/assignments — the signed-in mentor's own caseload. The backend forces a
 * MENTOR to their own assignments regardless of query params, so no argument is needed here.
 */
export async function listMyAssignments(): Promise<MentorAssignment[]> {
  const dtos = await apiRequest<MentorAssignmentResponseDto[]>("/mentor/assignments");
  return dtos.map(mapMentorAssignment);
}

/**
 * GET /api/mentor/assignments?mentorUsername= — assignments for a specific mentor. For
 * OFFICER/ADMIN looking up an existing mentor's coverage.
 */
export async function listAssignmentsForMentor(mentorUsername: string): Promise<MentorAssignment[]> {
  const dtos = await apiRequest<MentorAssignmentResponseDto[]>("/mentor/assignments", {
    params: { mentorUsername },
  });
  return dtos.map(mapMentorAssignment);
}

export interface MentorAccount {
  username: string;
  campusId: string;
  activeAssignmentsCount: number;
}

export interface CreateMentorInput {
  email: string;
  password: string;
  campusId?: string;
}

/**
 * GET /api/mentor/accounts — list active mentor accounts for the campus. OFFICER/ADMIN only.
 */
export async function listMentors(campusId?: string): Promise<MentorAccount[]> {
  return await apiRequest<MentorAccount[]>("/mentor/accounts", {
    params: campusId ? { campusId } : undefined,
  });
}

/**
 * POST /api/mentor/accounts — create a new mentor account for the campus. OFFICER/ADMIN only.
 */
export async function createMentorAccount(input: CreateMentorInput): Promise<MentorAccount> {
  return await apiRequest<MentorAccount>("/mentor/accounts", {
    method: "POST",
    body: input,
  });
}

/**
 * POST /api/mentor/assignments — assign a mentor (by username) to a student. OFFICER/ADMIN only
 * server-side. Surfaces the backend's validation (unknown username/student, cross-campus) as a
 * thrown ApiError.
 */
export async function createAssignment(
  mentorUsername: string,
  studentId: string,
): Promise<MentorAssignment> {
  const dto = await apiRequest<MentorAssignmentResponseDto>("/mentor/assignments", {
    method: "POST",
    body: { mentorUsername, studentId },
  });
  return mapMentorAssignment(dto);
}

/**
 * GET /api/students/:studentId/interventions — every intervention plan for a student, newest
 * first. A MENTOR sees only plans they own (server-scoped). An unknown student (404) or a
 * student with no plans surfaces as an empty list.
 */
export async function listStudentInterventions(studentId: string): Promise<InterventionPlan[]> {
  try {
    const dtos = await apiRequest<InterventionPlanResponseDto[]>(
      `/students/${studentId}/interventions`,
    );
    return (dtos ?? []).map(mapInterventionPlan);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return [];
    throw error;
  }
}

/** Fields for a new intervention plan (OFFICER/ADMIN). Actions are created from `actionLabels`. */
export interface CreateInterventionInput {
  mentorUsername: string;
  focusAreas: string[];
  actionLabels: string[];
  riskEntryId?: string;
  notes?: string;
}

/**
 * POST /api/students/:studentId/interventions — the placement office proposes a plan for a
 * student and assigns it to a mentor. OFFICER/ADMIN only; the plan starts PROPOSED and the
 * mentor activates it. Returns the created plan.
 */
export async function createInterventionPlan(
  studentId: string,
  input: CreateInterventionInput,
): Promise<InterventionPlan> {
  const dto = await apiRequest<InterventionPlanResponseDto>(
    `/students/${studentId}/interventions`,
    { method: "POST", body: input },
  );
  return mapInterventionPlan(dto);
}

/** POST /api/interventions/:planId/activate — the named mentor starts tracking a proposed plan. */
export async function activateInterventionPlan(planId: string): Promise<InterventionPlan> {
  return mapInterventionPlan(
    await apiRequest<InterventionPlanResponseDto>(`/interventions/${planId}/activate`, {
      method: "POST",
    }),
  );
}

/** POST /api/interventions/:planId/complete — closes out a plan. */
export async function completeInterventionPlan(planId: string): Promise<InterventionPlan> {
  return mapInterventionPlan(
    await apiRequest<InterventionPlanResponseDto>(`/interventions/${planId}/complete`, {
      method: "POST",
    }),
  );
}

/**
 * POST /api/interventions/:planId/actions/toggle — mark a single checklist action done/undone.
 * Progress is recomputed server-side and returned on the updated plan.
 */
export async function toggleInterventionAction(
  planId: string,
  actionId: string,
  done: boolean,
): Promise<InterventionPlan> {
  return mapInterventionPlan(
    await apiRequest<InterventionPlanResponseDto>(`/interventions/${planId}/actions/toggle`, {
      method: "POST",
      body: { actionId, done },
    }),
  );
}

/**
 * The default checklist the placement office starts an intervention from. This is UI copy (the
 * office edits it before creating a plan), not fetched business data — the real actions live on
 * the created plan and come back from the API.
 */
export const RECOMMENDED_PLAN_TEMPLATE = [
  "DSA practice plan",
  "Communication sessions",
  "Resume review",
  "Mock interview",
];
