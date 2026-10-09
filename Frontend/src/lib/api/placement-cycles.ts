// ============================================================
// PLACEMENT CYCLES API — live only. Cycles come from GET /api/placement-cycles
// (campus-scoped server-side). Officers/admins own create/edit/status; a cycle
// is retired via an ARCHIVED status transition, never hard-deleted.
// ============================================================

import { apiRequest, ApiError } from "@/lib/api/client";
import type { PlacementCycle, PlacementCycleStatus } from "@/types";

/** Backend PlacementCycleResponse (com.nexploy.campuslink.placement.dto.PlacementCycleResponse). */
export interface PlacementCycleResponseDto {
  id: string;
  campusId: string;
  name: string;
  academicYear: string;
  startsOn: string | null;
  endsOn: string | null;
  status: string;
}

function mapCycle(dto: PlacementCycleResponseDto): PlacementCycle {
  return {
    id: dto.id,
    campusId: dto.campusId,
    name: dto.name,
    academicYear: dto.academicYear,
    startsOn: dto.startsOn,
    endsOn: dto.endsOn,
    status: dto.status as PlacementCycleStatus,
  };
}

/** Request body for POST /api/placement-cycles — mirrors the backend CreatePlacementCycleRequest. */
export interface CreatePlacementCycleRequest {
  name: string;
  academicYear: string;
  campusId?: string | null;
  startsOn?: string | null;
  endsOn?: string | null;
}

/** Request body for PUT /api/placement-cycles/:id — the editable fields. */
export interface UpdatePlacementCycleRequest {
  name: string;
  academicYear: string;
  startsOn?: string | null;
  endsOn?: string | null;
}

/** GET /api/placement-cycles(?campusId=) — cycles the signed-in user may see. */
export async function listPlacementCycles(campusId?: string): Promise<PlacementCycle[]> {
  const dtos = await apiRequest<PlacementCycleResponseDto[]>("/placement-cycles", {
    params: campusId ? { campusId } : undefined,
  });
  return dtos.map(mapCycle);
}

/** GET /api/placement-cycles/:id — a single cycle (404 → undefined). */
export async function getPlacementCycle(id: string): Promise<PlacementCycle | undefined> {
  try {
    return mapCycle(await apiRequest<PlacementCycleResponseDto>(`/placement-cycles/${id}`));
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return undefined;
    throw error;
  }
}

/** POST /api/placement-cycles — officer/admin creates a cycle (starts PLANNING). */
export async function createPlacementCycle(
  request: CreatePlacementCycleRequest,
): Promise<PlacementCycle> {
  return mapCycle(
    await apiRequest<PlacementCycleResponseDto>("/placement-cycles", {
      method: "POST",
      body: request,
    }),
  );
}

/** PUT /api/placement-cycles/:id — officer/admin replaces a cycle's editable fields. */
export async function updatePlacementCycle(
  id: string,
  request: UpdatePlacementCycleRequest,
): Promise<PlacementCycle> {
  return mapCycle(
    await apiRequest<PlacementCycleResponseDto>(`/placement-cycles/${id}`, {
      method: "PUT",
      body: request,
    }),
  );
}

/** PATCH /api/placement-cycles/:id/status — officer/admin moves the cycle's lifecycle state. */
export async function updatePlacementCycleStatus(
  id: string,
  status: PlacementCycleStatus,
): Promise<PlacementCycle> {
  return mapCycle(
    await apiRequest<PlacementCycleResponseDto>(`/placement-cycles/${id}/status`, {
      method: "PATCH",
      body: { status },
    }),
  );
}
