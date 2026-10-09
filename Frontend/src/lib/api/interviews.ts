// ============================================================
// INTERVIEWS API — live only. Interview slots come from the campus-scoped
// GET /api/interviews (or the per-student endpoint); an empty DB yields [].
// ============================================================

import { apiRequest } from "@/lib/api/client";
import { mapInterview, type InterviewResponseDto } from "@/lib/api/mappers";
import type { InterviewSlot } from "@/types";

/** GET /api/interviews — slots the signed-in user may see (campus-scoped server-side). */
export async function listInterviews(campusId?: string): Promise<InterviewSlot[]> {
  const dtos = await apiRequest<InterviewResponseDto[]>("/interviews", {
    params: campusId ? { campusId } : undefined,
  });
  return dtos.map(mapInterview);
}

/** GET /api/students/:studentId/interviews */
export async function listInterviewsByStudent(studentId: string): Promise<InterviewSlot[]> {
  const dtos = await apiRequest<InterviewResponseDto[]>(`/students/${studentId}/interviews`);
  return dtos.map(mapInterview);
}

/**
 * Interview rounds for a given drive. The backend has no by-drive endpoint, but GET /api/interviews
 * is campus-scoped for a recruiter (their own campus), so we fetch that and filter by driveId —
 * never fabricated.
 */
export async function listInterviewsByDrive(driveId: string): Promise<InterviewSlot[]> {
  const dtos = await apiRequest<InterviewResponseDto[]>("/interviews");
  return dtos.map(mapInterview).filter((i) => i.driveId === driveId);
}

/**
 * Request body for POST /api/interviews — mirrors the backend ScheduleInterviewRequest (camelCase).
 * `mode` is the backend enum (ON_CAMPUS | VIRTUAL); `scheduledAt` is an ISO-8601 offset timestamp.
 */
export interface ScheduleInterviewRequest {
  studentId: string;
  driveId: string;
  mode: "ON_CAMPUS" | "VIRTUAL";
  scheduledAt: string;
  round?: string | null;
  venue?: string | null;
  panel?: string | null;
  scheduleEventId?: string | null;
  companyName?: string | null;
}

/** Partial reschedule body for PATCH /api/interviews/:id (all fields optional). */
export interface UpdateInterviewRequest {
  scheduledAt?: string | null;
  mode?: "ON_CAMPUS" | "VIRTUAL" | null;
  round?: string | null;
  venue?: string | null;
  panel?: string | null;
}

/** POST /api/interviews — officer/recruiter schedules a new slot (starts SCHEDULED). */
export async function scheduleInterview(request: ScheduleInterviewRequest): Promise<InterviewSlot> {
  return mapInterview(
    await apiRequest<InterviewResponseDto>("/interviews", { method: "POST", body: request }),
  );
}

/** PATCH /api/interviews/:id — reschedules a SCHEDULED interview (partial). */
export async function rescheduleInterview(
  interviewId: string,
  request: UpdateInterviewRequest,
): Promise<InterviewSlot> {
  return mapInterview(
    await apiRequest<InterviewResponseDto>(`/interviews/${interviewId}`, {
      method: "PATCH",
      body: request,
    }),
  );
}

/** PATCH /api/interviews/:id/cancel — cancels a SCHEDULED interview (soft — row preserved). */
export async function cancelInterview(interviewId: string): Promise<InterviewSlot> {
  return mapInterview(
    await apiRequest<InterviewResponseDto>(`/interviews/${interviewId}/cancel`, { method: "PATCH" }),
  );
}

/**
 * Request body for POST /api/interviews/:id/result — mirrors the backend RecordResultRequest.
 * `result` is the backend InterviewResult enum; `feedback` is optional panel feedback.
 */
export interface RecordInterviewResultRequest {
  result: "SELECTED" | "REJECTED" | "ON_HOLD" | "PENDING";
  feedback?: string | null;
}

/** POST /api/interviews/:id/result — officer records a round outcome (interview becomes COMPLETED). */
export async function recordInterviewResult(
  interviewId: string,
  request: RecordInterviewResultRequest,
): Promise<InterviewSlot> {
  return mapInterview(
    await apiRequest<InterviewResponseDto>(`/interviews/${interviewId}/result`, {
      method: "POST",
      body: request,
    }),
  );
}
