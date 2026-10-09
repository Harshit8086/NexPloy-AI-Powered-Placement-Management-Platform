// ============================================================
// APPLICATIONS API — live only. Applications come from GET /api/applications
// (scoped server-side); applying persists via POST /api/drives/:id/apply.
// ============================================================

import { apiRequest } from "@/lib/api/client";
import { mapApplication, type ApplicationDto } from "@/lib/api/mappers";
import type { Application, ApplicationStatus } from "@/types";

/** Optional payload when applying: a short cover note and a chosen resume document. */
export interface ApplyToDriveInput {
  coverNote?: string;
  resumeDocumentId?: string;
}

/** Shape returned by POST /api/drives/{driveId}/apply. */
export interface ApplicationResponseDto {
  id: string;
  studentId: string;
  driveId: string;
  appliedOn: string | null;
  status: string;
  coverNote: string | null;
  resumeDocumentId: string | null;
}

/** GET /api/applications — applications the signed-in user may see (scoped server-side). */
export async function listApplications(): Promise<Application[]> {
  return (await apiRequest<ApplicationDto[]>("/applications")).map(mapApplication);
}

/** GET /api/applications?studentId= — the given student's applications. */
export async function listApplicationsByStudent(studentId: string): Promise<Application[]> {
  return (await apiRequest<ApplicationDto[]>("/applications", { params: { studentId } })).map(
    mapApplication,
  );
}

/** GET /api/applications?driveId= — the given drive's applications. */
export async function listApplicationsByDrive(driveId: string): Promise<Application[]> {
  return (await apiRequest<ApplicationDto[]>("/applications", { params: { driveId } })).map(
    mapApplication,
  );
}

/** The signed-in student's application to a specific drive, if any. */
export async function getApplicationForDrive(
  studentId: string,
  driveId: string,
): Promise<Application | undefined> {
  return (await listApplicationsByStudent(studentId)).find((a) => a.driveId === driveId);
}

/**
 * POST /api/drives/:driveId/apply — the signed-in student applies to a drive. The backend enforces
 * eligibility, the drive being open, and one-application-per-drive, so a rejected apply surfaces as
 * a thrown ApiError with the reason.
 */
export async function applyToDrive(
  driveId: string,
  input: ApplyToDriveInput = {},
): Promise<ApplicationResponseDto> {
  return apiRequest<ApplicationResponseDto>(`/drives/${driveId}/apply`, {
    method: "POST",
    body: input,
  });
}

/**
 * PATCH /api/applications/:id/status — officer/recruiter advances the funnel. Sends the backend
 * UPPERCASE ApplicationStatus enum (the frontend union is lowercase, so it is upper-cased here).
 * The backend validates the transition (illegal jumps → 409).
 */
export async function updateApplicationStatus(
  applicationId: string,
  status: ApplicationStatus,
): Promise<Application> {
  return mapApplication(
    await apiRequest<ApplicationDto>(`/applications/${applicationId}/status`, {
      method: "PATCH",
      body: { status: status.toUpperCase() },
    }),
  );
}

/** PATCH /api/applications/:id/withdraw — the signed-in student withdraws their own application. */
export async function withdrawApplication(applicationId: string): Promise<Application> {
  return mapApplication(
    await apiRequest<ApplicationDto>(`/applications/${applicationId}/withdraw`, { method: "PATCH" }),
  );
}
