// ============================================================
// SCHEDULES API
//
// Live: the schedule module under /api/schedule. Events, venues, panels and
// detected conflicts are read-only GETs (server-scoped: officers/admins see
// all/campus, others their own campus). Optimization is a real OR-Tools run
// (POST /schedule/optimize) an officer/admin then approves or rejects.
//
// There is no mock branch for business data here: with no seeded schedule the
// endpoints return empty lists and the UI shows empty states. Only the staged
// runner's step labels (UI-only copy) are retained.
// ============================================================

import { apiRequest } from "@/lib/api/client";
import {
  mapOptimizationRun,
  mapPanel,
  mapScheduleConflict,
  mapScheduleEvent,
  mapVenue,
  type OptimizationResponseDto,
  type PanelResponseDto,
  type ScheduleConflictResponseDto,
  type ScheduleEventResponseDto,
  type VenueResponseDto,
} from "@/lib/api/mappers";
import { scheduleOptimizationSteps } from "@/lib/simulate";
import type {
  OptimizationRun,
  ScheduleConflict,
  ScheduleEvent,
  SchedulePanel,
  ScheduleVenue,
} from "@/types";

/** GET /api/schedule/events — venue/panel names resolved server-side. */
export async function listScheduleEvents(): Promise<ScheduleEvent[]> {
  const dtos = await apiRequest<ScheduleEventResponseDto[]>("/schedule/events");
  return dtos.map(mapScheduleEvent);
}

/** GET /api/schedule/conflicts — freshly detected conflicts (optional campus scope). */
export async function listScheduleConflicts(campusId?: string): Promise<ScheduleConflict[]> {
  const dtos = await apiRequest<ScheduleConflictResponseDto[]>("/schedule/conflicts", {
    params: campusId ? { campusId } : undefined,
  });
  return dtos.map(mapScheduleConflict);
}

/** GET /api/schedule/venues */
export async function listVenues(): Promise<ScheduleVenue[]> {
  const dtos = await apiRequest<VenueResponseDto[]>("/schedule/venues");
  return dtos.map(mapVenue);
}

/** GET /api/schedule/panels */
export async function listPanels(): Promise<SchedulePanel[]> {
  const dtos = await apiRequest<PanelResponseDto[]>("/schedule/panels");
  return dtos.map(mapPanel);
}

// ---- Venue CRUD (officer/admin — soft-delete via the active flag) ----

/** Body for POST /api/schedule/venues. campusId optional (defaults to caller's campus). */
export interface CreateVenueRequest {
  name: string;
  capacity?: number | null;
  campusId?: string | null;
}

/** Body for PUT /api/schedule/venues/:id (active moves via PATCH /active). */
export interface UpdateVenueRequest {
  name: string;
  capacity?: number | null;
}

/** POST /api/schedule/venues — officer/admin creates a venue (starts active). */
export async function createVenue(request: CreateVenueRequest): Promise<ScheduleVenue> {
  return mapVenue(
    await apiRequest<VenueResponseDto>("/schedule/venues", { method: "POST", body: request }),
  );
}

/** PUT /api/schedule/venues/:id — replace a venue's editable fields. */
export async function updateVenue(
  venueId: string,
  request: UpdateVenueRequest,
): Promise<ScheduleVenue> {
  return mapVenue(
    await apiRequest<VenueResponseDto>(`/schedule/venues/${venueId}`, {
      method: "PUT",
      body: request,
    }),
  );
}

/** PATCH /api/schedule/venues/:id/active — (de)activate a venue (soft-delete). */
export async function setVenueActive(venueId: string, active: boolean): Promise<ScheduleVenue> {
  return mapVenue(
    await apiRequest<VenueResponseDto>(`/schedule/venues/${venueId}/active`, {
      method: "PATCH",
      body: { active },
    }),
  );
}

// ---- Panel CRUD (officer/admin — soft-delete via the active flag) ----

/** A panel member on create/update — server assigns member ids. */
export interface PanelMemberInput {
  name: string;
  email?: string | null;
}

/** Body for POST /api/schedule/panels. campusId optional; members optional roster. */
export interface CreatePanelRequest {
  name: string;
  campusId?: string | null;
  members?: PanelMemberInput[] | null;
}

/** Body for PUT /api/schedule/panels/:id. A non-null members list replaces the roster wholesale. */
export interface UpdatePanelRequest {
  name: string;
  members?: PanelMemberInput[] | null;
}

/** POST /api/schedule/panels — officer/admin creates a panel with an optional roster. */
export async function createPanel(request: CreatePanelRequest): Promise<SchedulePanel> {
  return mapPanel(
    await apiRequest<PanelResponseDto>("/schedule/panels", { method: "POST", body: request }),
  );
}

/** PUT /api/schedule/panels/:id — replace a panel's fields; non-null members replaces the roster. */
export async function updatePanel(
  panelId: string,
  request: UpdatePanelRequest,
): Promise<SchedulePanel> {
  return mapPanel(
    await apiRequest<PanelResponseDto>(`/schedule/panels/${panelId}`, {
      method: "PUT",
      body: request,
    }),
  );
}

/** PATCH /api/schedule/panels/:id/active — (de)activate a panel (soft-delete). */
export async function setPanelActive(panelId: string, active: boolean): Promise<SchedulePanel> {
  return mapPanel(
    await apiRequest<PanelResponseDto>(`/schedule/panels/${panelId}/active`, {
      method: "PATCH",
      body: { active },
    }),
  );
}

/**
 * POST /api/schedule/optimize — runs the OR-Tools optimizer over the (optionally
 * campus-scoped) schedule and returns a PENDING run with alternatives. The active
 * schedule is unchanged until the run is approved. OFFICER/ADMIN only server-side.
 */
export async function optimizeSchedule(campusId?: string): Promise<OptimizationRun> {
  const dto = await apiRequest<OptimizationResponseDto>("/schedule/optimize", {
    method: "POST",
    params: campusId ? { campusId } : undefined,
  });
  return mapOptimizationRun(dto);
}

/** GET /api/schedule/optimization-runs/{runId} — re-read a persisted run. */
export async function getOptimizationRun(runId: string): Promise<OptimizationRun> {
  return mapOptimizationRun(
    await apiRequest<OptimizationResponseDto>(`/schedule/optimization-runs/${runId}`),
  );
}

/** POST /api/schedule/optimization-runs/{runId}/approve — applies the proposal (OFFICER/ADMIN). */
export async function approveOptimizationRun(runId: string): Promise<OptimizationRun> {
  return mapOptimizationRun(
    await apiRequest<OptimizationResponseDto>(`/schedule/optimization-runs/${runId}/approve`, {
      method: "POST",
    }),
  );
}

/** POST /api/schedule/optimization-runs/{runId}/reject — records a reason, leaves schedule as-is. */
export async function rejectOptimizationRun(runId: string, reason: string): Promise<OptimizationRun> {
  return mapOptimizationRun(
    await apiRequest<OptimizationResponseDto>(`/schedule/optimization-runs/${runId}/reject`, {
      method: "POST",
      body: { reason },
    }),
  );
}

export { scheduleOptimizationSteps };

// ---- Presentational helpers (derive display strings from real ISO timestamps) ----
// These never invent a time — an unparseable/empty value renders as an em dash.

function parse(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** "2:00 PM" from an ISO timestamp. */
export function formatEventTime(iso: string | null | undefined): string {
  const d = parse(iso);
  return d ? d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" }) : "—";
}

/** "2:00 PM – 3:30 PM" from a start/end pair. */
export function formatEventTimeRange(start: string, end: string): string {
  return `${formatEventTime(start)} – ${formatEventTime(end)}`;
}

/** Whole-minute duration between two ISO timestamps (0 if either is invalid). */
export function eventDurationMins(start: string, end: string): number {
  const a = parse(start);
  const b = parse(end);
  if (!a || !b) return 0;
  return Math.max(0, Math.round((b.getTime() - a.getTime()) / 60000));
}

export interface ScheduleDayColumn {
  /** yyyy-mm-dd key for grouping/sorting. */
  key: string;
  /** e.g. "Monday". */
  day: string;
  /** e.g. "21 Sep". */
  date: string;
  events: ScheduleEvent[];
}

/**
 * Groups events into calendar-day columns derived from their real start dates, ordered
 * chronologically. Replaces the old hardcoded Mon–Fri week — the columns are whatever days the
 * data actually spans.
 */
export function groupEventsByDay(events: ScheduleEvent[]): ScheduleDayColumn[] {
  const byKey = new Map<string, ScheduleDayColumn>();
  for (const event of events) {
    const d = parse(event.startsAt);
    if (!d) continue;
    const key = d.toISOString().slice(0, 10);
    let col = byKey.get(key);
    if (!col) {
      col = {
        key,
        day: d.toLocaleDateString(undefined, { weekday: "long" }),
        date: d.toLocaleDateString(undefined, { day: "numeric", month: "short" }),
        events: [],
      };
      byKey.set(key, col);
    }
    col.events.push(event);
  }
  return Array.from(byKey.values())
    .sort((a, b) => (a.key < b.key ? -1 : 1))
    .map((col) => ({
      ...col,
      events: col.events.sort((a, b) => (a.startsAt < b.startsAt ? -1 : 1)),
    }));
}
