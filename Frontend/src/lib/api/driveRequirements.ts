import { apiRequest } from "@/lib/api/client";

export interface DriveSkillRequirement {
  id: string;
  driveId: string;
  skill: string;
  requiredLevel: number;
  weight: number;
  mandatory: boolean;
  source: "OFFICER" | "AI_EXTRACTED" | "RECRUITER";
  confidence?: number | null;
  approved: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpsertDriveSkillRequirementRequest {
  skill: string;
  requiredLevel: number;
  weight?: number;
  mandatory?: boolean;
  source?: string;
  confidence?: number;
  approved?: boolean;
}

export interface ExtractedSkillItem {
  skill: string;
  requiredLevel: number;
  weight: number;
  mandatory: boolean;
  confidence: number;
  justification: string;
}

export interface ExtractDriveSkillsResponse {
  driveId: string;
  jobRole: string;
  extractedSkills: ExtractedSkillItem[];
  source: string;
}

export function getDriveSkillRequirements(
  driveId: string,
  approvedOnly = false,
): Promise<DriveSkillRequirement[]> {
  return apiRequest<DriveSkillRequirement[]>(
    `/drives/${driveId}/skill-requirements?approvedOnly=${approvedOnly}`,
  );
}

export function createDriveSkillRequirement(
  driveId: string,
  body: UpsertDriveSkillRequirementRequest,
): Promise<DriveSkillRequirement> {
  return apiRequest<DriveSkillRequirement>(`/drives/${driveId}/skill-requirements`, {
    method: "POST",
    body,
  });
}

export function updateDriveSkillRequirement(
  driveId: string,
  id: string,
  body: UpsertDriveSkillRequirementRequest,
): Promise<DriveSkillRequirement> {
  return apiRequest<DriveSkillRequirement>(`/drives/${driveId}/skill-requirements/${id}`, {
    method: "PUT",
    body,
  });
}

export function deleteDriveSkillRequirement(
  driveId: string,
  id: string,
): Promise<void> {
  return apiRequest<void>(`/drives/${driveId}/skill-requirements/${id}`, {
    method: "DELETE",
  });
}

export function approveDriveSkillRequirement(
  driveId: string,
  id: string,
): Promise<DriveSkillRequirement> {
  return apiRequest<DriveSkillRequirement>(
    `/drives/${driveId}/skill-requirements/${id}/approve`,
    { method: "POST" },
  );
}

export function approveAllDriveSkillRequirements(
  driveId: string,
): Promise<number> {
  return apiRequest<number>(
    `/drives/${driveId}/skill-requirements/approve-all`,
    { method: "POST" },
  );
}

export function bulkUpsertDriveSkillRequirements(
  driveId: string,
  requirements: UpsertDriveSkillRequirementRequest[],
): Promise<DriveSkillRequirement[]> {
  return apiRequest<DriveSkillRequirement[]>(
    `/drives/${driveId}/skill-requirements/bulk`,
    {
      method: "POST",
      body: { requirements },
    },
  );
}

export function extractDriveSkillsWithAi(
  driveId: string,
): Promise<ExtractDriveSkillsResponse> {
  return apiRequest<ExtractDriveSkillsResponse>(
    `/drives/${driveId}/skill-requirements/extract-ai`,
    { method: "POST" },
  );
}
