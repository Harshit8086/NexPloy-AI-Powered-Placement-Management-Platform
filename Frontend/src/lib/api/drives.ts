// ============================================================
// DRIVES API — live only. Drives come from GET /api/drives (scoped
// server-side); eligibility from GET /api/drives/:id/eligibility/:sid,
// which runs the same deterministic checks. An empty DB yields [].
// ============================================================

import { apiRequest } from "@/lib/api/client";
import {
  mapDrive,
  mapEligibility,
  type DriveResponseDto,
  type EligibilityResponseDto,
} from "@/lib/api/mappers";
import type { Drive, DriveStatus, EligibilityResult } from "@/types";

/**
 * Request body for POST/PUT /api/drives — mirrors the backend Create/UpdateDriveRequest
 * (camelCase, exact field names). FK ids (recruiterId, cycleId) are plain strings validated
 * server-side; packageLpa is numeric (the "₹x LPA" formatting is a display-only concern).
 */
export interface CreateDriveRequest {
  recruiterId: string;
  companyName: string;
  role: string;
  location?: string | null;
  packageLpa: number;
  cycleId?: string | null;
  minimumCgpa?: number | null;
  maximumBacklogs?: number | null;
  graduationYear?: number | null;
  deadline?: string | null;
  driveType?: string | null;
  description?: string | null;
  expectedHiring?: number | null;
  venue?: string | null;
  minimumTenthPercentage?: number | null;
  minimumTwelfthPercentage?: number | null;
  requiredSkills?: string[];
  rounds?: { name: string; description: string }[];
  allowedBranches?: string[];
}

function isNotFound(error: unknown): boolean {
  return error instanceof Error && "status" in error && (error as { status: number }).status === 404;
}

/** GET /api/drives */
export async function listDrives(): Promise<Drive[]> {
  const dtos = await apiRequest<DriveResponseDto[]>("/drives");
  return dtos.map(mapDrive);
}

/** GET /api/drives/:id */
export async function getDrive(id: string): Promise<Drive | undefined> {
  try {
    return mapDrive(await apiRequest<DriveResponseDto>(`/drives/${id}`));
  } catch (error) {
    if (isNotFound(error)) return undefined;
    throw error;
  }
}

/** GET /api/drives, then keep ACTIVE (no status-filtered list endpoint yet, so filtered here). */
export async function listActiveDrives(): Promise<Drive[]> {
  return (await listDrives()).filter((d) => d.status === "ACTIVE");
}

/** Drives on a campus. No dedicated endpoint yet — filters the campus-scoped list. */
export async function listDrivesByCampus(campusId: string): Promise<Drive[]> {
  return (await listDrives()).filter((d) => d.campusId === campusId);
}

/** A recruiter's drives. No dedicated endpoint yet — filters by companyId (recruiterId). */
export async function listDrivesByCompany(companyId: string): Promise<Drive[]> {
  return (await listDrives()).filter((d) => d.companyId === companyId);
}

/**
 * GET /api/drives/:driveId/eligibility/:studentId — the backend runs the same deterministic checks
 * and returns a shape that maps 1:1 to EligibilityResult. A 404 (unknown student/drive) → undefined.
 */
export async function checkEligibility(
  studentId: string,
  driveId: string,
): Promise<EligibilityResult | undefined> {
  try {
    const dto = await apiRequest<EligibilityResponseDto>(
      `/drives/${driveId}/eligibility/${studentId}`,
    );
    return mapEligibility(dto);
  } catch (error) {
    if (isNotFound(error)) return undefined;
    throw error;
  }
}

/** POST /api/drives — officer/recruiter creates a drive (starts DRAFT). Returns the created drive. */
export async function createDrive(request: CreateDriveRequest): Promise<Drive> {
  return mapDrive(await apiRequest<DriveResponseDto>("/drives", { method: "POST", body: request }));
}

/** PUT /api/drives/:id — replaces a drive's editable fields. Returns the updated drive. */
export async function updateDrive(id: string, request: CreateDriveRequest): Promise<Drive> {
  return mapDrive(
    await apiRequest<DriveResponseDto>(`/drives/${id}`, { method: "PUT", body: request }),
  );
}

/** PATCH /api/drives/:id/status — moves the drive's lifecycle state. Returns the updated drive. */
export async function updateDriveStatus(id: string, status: DriveStatus): Promise<Drive> {
  return mapDrive(
    await apiRequest<DriveResponseDto>(`/drives/${id}/status`, {
      method: "PATCH",
      body: { status },
    }),
  );
}
