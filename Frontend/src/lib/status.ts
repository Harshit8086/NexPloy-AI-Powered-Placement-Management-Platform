import type {
  ApplicationStatus,
  DocumentStatus,
  DriveStatus,
  InterviewStatus,
  OfferStatus,
  PlacementStatus,
  RiskLevel,
} from "@/types";

// ============================================================
// One place that maps every status enum to its badge variant, label and
// timeline position. Components import from here instead of declaring a
// competing Record<Status, ...> map per page — that is what caused the
// same status to render three different colours before.
// ============================================================

export type BadgeTone = "default" | "success" | "warning" | "risk" | "muted" | "outline";

// ---- Placement lifecycle --------------------------------------------

/** The full lifecycle a student moves through, as shown on the Placement
 * Journey timeline. Index positions are what `applicationStageIndex`
 * resolves to. */
export const PLACEMENT_JOURNEY_STAGES = [
  "APPLIED",
  "SHORTLISTED",
  "ASSESSMENT",
  "INTERVIEW",
  "SELECTED",
  "OFFER",
  "ACCEPTED",
  "DOCS",
  "JOINED",
] as const;

export const PLACEMENT_JOURNEY_FULL_LABELS: Record<string, string> = {
  APPLIED: "Applied",
  SHORTLISTED: "Shortlisted",
  ASSESSMENT: "Assessment",
  INTERVIEW: "Interview",
  SELECTED: "Selected",
  OFFER: "Offer Received",
  ACCEPTED: "Offer Accepted",
  DOCS: "Document Verification",
  JOINED: "Joined",
};

export const applicationStageIndex: Record<ApplicationStatus, number> = {
  eligible: -1,
  applied: 0,
  shortlisted: 1,
  assessment: 2,
  interview: 3,
  selected: 4,
  offer: 5,
  accepted: 6,
  docs: 7,
  joined: 8,
  rejected: 0,
  withdrawn: -1,
};

export const applicationStatusLabel: Record<ApplicationStatus, string> = {
  eligible: "ELIGIBLE",
  applied: "APPLIED",
  shortlisted: "SHORTLISTED",
  assessment: "ASSESSMENT",
  interview: "INTERVIEW",
  selected: "SELECTED",
  offer: "OFFER",
  accepted: "OFFER ACCEPTED",
  docs: "DOCS VERIFIED",
  joined: "JOINED",
  rejected: "NOT SELECTED",
  withdrawn: "WITHDRAWN",
};

export const applicationStatusTone: Record<ApplicationStatus, BadgeTone> = {
  eligible: "muted",
  applied: "default",
  shortlisted: "default",
  assessment: "warning",
  interview: "warning",
  selected: "success",
  offer: "success",
  accepted: "success",
  docs: "success",
  joined: "success",
  rejected: "risk",
  withdrawn: "muted",
};

/**
 * Legal manual next statuses an officer/recruiter can move an application to from its
 * current stage. Mirrors the backend `ALLOWED_TRANSITIONS` map in `ApplicationService`
 * (which enforces this authoritatively — an illegal target returns 409). The UI only
 * offers these so the control never presents a move the server will reject.
 *
 * Note: OFFER and everything after it (ACCEPTED → DOCS → JOINED) are driven by the Offers
 * module (extend / accept / verify documents / mark joined), which creates the backing
 * Offer entity and advances the application. So from SELECTED onward the only manual move
 * exposed here is REJECTED — the forward moves happen through the Offers surface, not a
 * raw status PATCH that would leave the funnel without an Offer row.
 */
export const applicationManualNext: Record<ApplicationStatus, ApplicationStatus[]> = {
  eligible: [],
  applied: ["shortlisted", "rejected"],
  shortlisted: ["assessment", "interview", "rejected"],
  assessment: ["interview", "rejected"],
  interview: ["selected", "rejected"],
  selected: ["rejected"],
  offer: ["rejected"],
  accepted: ["rejected"],
  docs: ["rejected"],
  joined: [],
  rejected: [],
  withdrawn: [],
};

/** Legal manual next statuses for an application at `current` (empty if terminal). */
export function nextApplicationStatuses(current: ApplicationStatus): ApplicationStatus[] {
  return applicationManualNext[current] ?? [];
}

// ---- Documents -------------------------------------------------------

export const documentStatusTone: Record<DocumentStatus, BadgeTone> = {
  VERIFIED: "success",
  "UNDER REVIEW": "warning",
  PENDING: "muted",
  UPLOADED: "default",
  "NEEDS UPDATE": "risk",
};

// ---- Offers ----------------------------------------------------------

export const offerStatusTone: Record<OfferStatus, BadgeTone> = {
  "OFFER RECEIVED": "default",
  "PENDING ACCEPTANCE": "warning",
  ACCEPTED: "success",
  DECLINED: "risk",
};

// ---- Drives ----------------------------------------------------------

export const driveStatusTone: Record<DriveStatus, BadgeTone> = {
  DRAFT: "muted",
  ACTIVE: "success",
  CLOSED: "warning",
  COMPLETED: "default",
  CANCELLED: "risk",
};

// ---- Interviews ------------------------------------------------------

export const interviewStatusTone: Record<InterviewStatus, BadgeTone> = {
  SCHEDULED: "default",
  COMPLETED: "success",
  CANCELLED: "risk",
};

// ---- Placement status (student) --------------------------------------

export const placementStatusTone: Record<PlacementStatus, BadgeTone> = {
  UNPLACED: "muted",
  "IN PROCESS": "warning",
  OFFERED: "default",
  PLACED: "success",
};

// ---- Risk ------------------------------------------------------------

export const riskTone: Record<RiskLevel, BadgeTone> = {
  HIGH: "risk",
  MEDIUM: "warning",
  LOW: "success",
};

export const riskOrder: Record<RiskLevel, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };

/** Readiness bands used for colour coding and cohort grouping. */
export function readinessBand(value: number): { label: string; tone: BadgeTone } {
  if (value >= 80) return { label: "Placement Ready", tone: "success" };
  if (value >= 65) return { label: "Nearly Ready", tone: "default" };
  if (value >= 50) return { label: "Developing", tone: "warning" };
  return { label: "Needs Intervention", tone: "risk" };
}

/**
 * Employability tier (problem-statement Section C): Not Ready → Developing → Ready →
 * Highly Employable. A pure presentation mapping of the student-level readiness score, kept
 * deliberately separate from {@link readinessBand} (which drives cohort colour coding) and from
 * the drive-level eligibility verdict — the tier never gates eligibility. `scored` is false when
 * the student has no readiness score yet (null/0), so the UI can present it as unscored rather
 * than implying a real low score.
 */
export function employabilityTier(
  value: number | null | undefined,
): { label: string; tone: BadgeTone; scored: boolean } {
  if (value == null || value <= 0) return { label: "Not Ready", tone: "risk", scored: false };
  if (value >= 80) return { label: "Highly Employable", tone: "success", scored: true };
  if (value >= 65) return { label: "Ready", tone: "default", scored: true };
  if (value >= 45) return { label: "Developing", tone: "warning", scored: true };
  return { label: "Not Ready", tone: "risk", scored: true };
}
