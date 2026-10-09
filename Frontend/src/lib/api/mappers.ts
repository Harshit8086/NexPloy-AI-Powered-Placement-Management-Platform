// ============================================================
// BACKEND DTO -> FRONTEND TYPE MAPPERS
//
// The Spring Boot response DTOs and the frontend types in src/types are
// deliberately different shapes: the backend persists a thin projection,
// the frontend types carry richer display fields the prototype invented.
// Each mapper here translates a backend response into the frontend type,
// and is honest about which fields the backend actually provides.
//
// Only map a domain here once the backend DTO can populate the fields the
// UI actually renders. Eligibility maps faithfully (near 1:1); other
// domains still need richer backend DTOs before they can be wired live
// without fabricating data, so they stay in mock mode for now.
// ============================================================

import type {
  Application,
  ApplicationStatus,
  AdminNotification,
  CampusSummary,
  CandidateMatch,
  CandidateRecommendation,
  DeliveryAttempt,
  DocumentItem,
  Drive,
  EligibilityCheck,
  EligibilityResult,
  ExplanationStatus,
  InterventionAction,
  InterventionPlan,
  InterventionStatus,
  InterviewSlot,
  MentorAssignment,
  NotificationChannel,
  NotificationItem,
  NotificationStatus,
  NotificationType,
  Offer,
  OptimizationAlternative,
  OptimizationRun,
  Recruiter,
  Recommendation,
  RecommendationType,
  RiskEntry,
  RiskFactor,
  ScheduleConflict,
  ScheduleConflictType,
  ScheduleEvent,
  SchedulePanel,
  ScheduleVenue,
  Student,
} from "@/types";

/** Backend EligibilityResponse (com.nexploy.campuslink.drive.dto.EligibilityResponse). */
export interface EligibilityResponseDto {
  studentId: string;
  driveId: string;
  eligible: boolean;
  checks: { label: string; passed: boolean; detail: string }[];
  failureReasons: string[];
}

/**
 * Maps the backend eligibility result to the frontend EligibilityResult.
 * The shapes line up field-for-field, so this is a faithful, lossless map.
 */
export function mapEligibility(dto: EligibilityResponseDto): EligibilityResult {
  const checks: EligibilityCheck[] = dto.checks.map((check) => ({
    label: check.label,
    passed: check.passed,
    detail: check.detail,
  }));
  return {
    eligible: dto.eligible,
    checks,
    failureReasons: dto.failureReasons,
  };
}

/** Backend RiskEntryResponse (com.nexploy.campuslink.risk.dto.RiskEntryResponse). */
export interface RiskEntryResponseDto {
  id: string;
  studentId: string;
  placementCycleId: string;
  level: string; // LOW | MEDIUM | HIGH
  supportProbability: number;
  primaryReason: string;
  factors: { name: string; impact: number; direction: string }[];
  recommendedInterventions: string[];
  confidence: number;
  modelVersion: string;
  featureVersion: string;
  generatedAt: string;
}

/**
 * Maps a persisted backend risk entry to the frontend RiskEntry. The two shapes differ:
 * the backend records a model factor as {name, impact, direction}, while the frontend
 * RiskFactor is a display pair {label, detail}. We map the factor name to the label and
 * synthesize a short, honest detail from the impact/direction the model returned (rather
 * than inventing a benchmark sentence the backend does not have). `recommendedInterventions`
 * maps to `recommendedActions`. The backend `level` is already the LOW/MEDIUM/HIGH contract.
 */
/**
 * Human-readable labels for the ML risk model's raw feature keys (risk.py SIGNAL_WEIGHTS).
 * Stored risk entries keep the machine keys verbatim, so the display layer translates them
 * here — history rows written before this mapping existed still render readably.
 */
const RISK_FACTOR_LABELS: Record<string, string> = {
  readiness_score: "Low readiness score",
  backlog_count: "Backlogs pending clearance",
  application_activity: "Low application activity",
  interview_followthrough: "Missed or unconfirmed interviews",
  recency: "No recent placement activity",
  mock_preparation: "Little mock-interview preparation",
};

/** Falls back to title-casing unknown snake_case keys so no raw column name reaches the UI. */
function riskFactorLabel(name: string): string {
  if (RISK_FACTOR_LABELS[name]) return RISK_FACTOR_LABELS[name];
  return name
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function mapRiskEntry(dto: RiskEntryResponseDto): RiskEntry {
  const factors: RiskFactor[] = (dto.factors ?? []).map((factor) => ({
    label: riskFactorLabel(factor.name),
    detail: `${factor.direction === "increases-risk" ? "Increases support need" : "Neutral"}`
      + ` (impact ${factor.impact.toFixed(2)})`,
  }));
  return {
    id: dto.id,
    studentId: dto.studentId,
    level: mapRiskBand(dto.level),
    primaryReason: riskFactorLabel(dto.primaryReason),
    factors,
    recommendedActions: dto.recommendedInterventions ?? [],
  };
}

/**
 * The ML service emits support-oriented bands (LOW / MONITOR / NEEDS_SUPPORT /
 * INSUFFICIENT_DATA); the frontend RiskLevel is HIGH / MEDIUM / LOW. Map them so the UI's
 * tone/order lookups resolve: NEEDS_SUPPORT → HIGH, MONITOR → MEDIUM, everything else → LOW
 * (INSUFFICIENT_DATA is treated as LOW — not enough signal to flag). Already-HIGH/MEDIUM/LOW
 * values (mock/legacy) pass through.
 */
function mapRiskBand(band: string): RiskEntry["level"] {
  switch (band?.toUpperCase()) {
    case "NEEDS_SUPPORT":
    case "HIGH":
      return "HIGH";
    case "MONITOR":
    case "MEDIUM":
      return "MEDIUM";
    default:
      return "LOW";
  }
}

/** Backend InterventionPlanResponse (com.nexploy.campuslink.risk.dto.InterventionPlanResponse). */
export interface InterventionPlanResponseDto {
  id: string;
  studentId: string;
  mentorId: string; // carries the mentor username in the backend response
  riskEntryId: string | null;
  status: string; // PROPOSED | ACTIVE | COMPLETED
  createdOn: string; // ISO-8601 timestamp
  focusAreas: string[];
  actions: { id: string; label: string; done: boolean }[];
  progress: number;
  notes: string | null;
}

/**
 * Maps a backend intervention plan to the frontend InterventionPlan. The shapes line up
 * closely (id, studentId, status, focusAreas, actions, progress). Two honest notes:
 * the backend `mentorId` field actually carries the mentor username (the plan binds to an
 * account, not a mentor entity id), passed through as-is; and `createdOn` is an ISO
 * timestamp here, formatted to the short "dd Mon yyyy" the mock used so the UI renders
 * consistently. `notes` may be null and is normalized to an empty string.
 */
export function mapInterventionPlan(dto: InterventionPlanResponseDto): InterventionPlan {
  const actions: InterventionAction[] = (dto.actions ?? []).map((action) => ({
    id: action.id,
    label: action.label,
    done: action.done,
  }));
  return {
    id: dto.id,
    studentId: dto.studentId,
    mentorId: dto.mentorId,
    status: dto.status as InterventionStatus,
    createdOn: formatCreatedOn(dto.createdOn),
    focusAreas: dto.focusAreas ?? [],
    actions,
    progress: dto.progress,
    notes: dto.notes ?? "",
  };
}

/** ISO timestamp -> "dd Mon yyyy" (the mock's display format); falls back to the raw value. */
function formatCreatedOn(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
}

/** Backend MentorAssignmentResponse (com.nexploy.campuslink.mentor.dto.MentorAssignmentResponse). */
export interface MentorAssignmentResponseDto {
  id: string;
  mentorUsername: string;
  studentId: string;
  campusId: string;
  assignedAt: string;
}

/** Identity passthrough — the assignment binds a mentor username to a student on a campus. */
export function mapMentorAssignment(dto: MentorAssignmentResponseDto): MentorAssignment {
  return {
    id: dto.id,
    mentorUsername: dto.mentorUsername,
    studentId: dto.studentId,
    campusId: dto.campusId,
    assignedAt: dto.assignedAt,
  };
}

/** ISO date ("2026-09-30") -> "30 Sep 2026"; empty for null/blank; raw value if unparseable. */
function formatDate(iso: string | null | undefined): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
}

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

// ============================================================
// DRIVE
// ============================================================

/** Backend DriveResponse (com.nexploy.campuslink.drive.dto.DriveResponse). */
export interface DriveResponseDto {
  id: string;
  campusId: string;
  recruiterId: string;
  companyName: string;
  role: string;
  location: string | null;
  packageLpa: number | null;
  minimumCgpa: number | null;
  maximumBacklogs: number | null;
  graduationYear: number | null;
  deadline: string | null;
  status: string;
  driveType: string | null;
  description: string | null;
  expectedHiring: number | null;
  venue: string | null;
  minimumTenthPercentage: number | null;
  minimumTwelfthPercentage: number | null;
  requiredSkills: string[];
  rounds: { name: string; description: string }[];
  allowedBranches: string[];
}

/**
 * Maps a backend drive to the frontend Drive. Most fields line up; three honest notes:
 * the frontend composes its nested `criteria` from the drive's flat eligibility floors +
 * allowedBranches; `package` is formatted from the numeric `packageLpa` into the "₹x LPA"
 * string the UI expects; and `driveDate`/`driveTime` are left empty because those belong to
 * a schedule event, not the drive itself (the backend DriveResponse does not carry them).
 * `logoInitial` is derived from the company name.
 */
export function mapDrive(dto: DriveResponseDto): Drive {
  return {
    id: dto.id,
    companyId: dto.recruiterId,
    companyName: dto.companyName,
    role: dto.role,
    driveType: (dto.driveType ?? "On-Campus") as Drive["driveType"],
    campusId: dto.campusId,
    status: dto.status as Drive["status"],
    description: dto.description ?? "",
    requiredSkills: dto.requiredSkills ?? [],
    criteria: {
      minCgpa: dto.minimumCgpa ?? 0,
      allowedBranches: dto.allowedBranches ?? [],
      maxBacklogs: dto.maximumBacklogs ?? 0,
      graduationYear: dto.graduationYear ?? 0,
      minTenthPercentage: dto.minimumTenthPercentage ?? undefined,
      minTwelfthPercentage: dto.minimumTwelfthPercentage ?? undefined,
    },
    rounds: dto.rounds ?? [],
    location: dto.location ?? "",
    package: dto.packageLpa != null ? `₹${dto.packageLpa} LPA` : "",
    expectedHiring: dto.expectedHiring ?? 0,
    deadline: formatDate(dto.deadline),
    deadlineIso: dto.deadline ?? null,
    // driveDate/driveTime live on the schedule event, not the drive DTO.
    driveDate: "",
    driveTime: "",
    venue: dto.venue ?? "",
    logoInitial: initials(dto.companyName).slice(0, 1) || "?",
  };
}

/**
 * Backend Application entity (com.nexploy.campuslink.application.entity.Application), returned
 * raw by GET /api/applications. Its status is the UPPERCASE enum; the frontend union is the
 * lowercase form, so we down-case it.
 */
export interface ApplicationDto {
  id: string;
  studentId: string;
  driveId: string;
  appliedOn: string | null;
  status: string;
  coverNote?: string | null;
  resumeDocumentId?: string | null;
}

export function mapApplication(dto: ApplicationDto): Application {
  return {
    id: dto.id,
    studentId: dto.studentId,
    driveId: dto.driveId,
    status: dto.status.toLowerCase() as ApplicationStatus,
    appliedOn: formatDate(dto.appliedOn),
  };
}

// ============================================================
// MATCH
// ============================================================

/** Backend MatchResponse (com.nexploy.campuslink.matching.dto.MatchResponse). */
export interface MatchResponseDto {
  id: string;
  studentId: string;
  driveId: string;
  overallFit: number;
  breakdown: {
    eligibility: number;
    skills: number;
    semanticMatch: number;
    projects: number;
    interview: number;
    readiness: number;
    academics: number;
    certificationRelevance: number;
    pastInterviews: number;
  };
  whySelected: string[];
  weakOrMissing: string[];
  recommendation: string;
}

/**
 * Maps a backend match to the frontend CandidateMatch. The shapes line up field-for-field
 * (overallFit, the seven-part breakdown, whySelected/weakOrMissing, recommendation), so this
 * is a faithful, lossless map. Note `breakdown.semanticMatch` is currently a keyword-overlap
 * proxy from the backend (embedding cosine similarity is a later phase), not a fabricated score.
 */
export function mapMatch(dto: MatchResponseDto): CandidateMatch {
  return {
    id: dto.id,
    studentId: dto.studentId,
    driveId: dto.driveId,
    overallFit: dto.overallFit,
    breakdown: { ...dto.breakdown },
    whySelected: dto.whySelected ?? [],
    weakOrMissing: dto.weakOrMissing ?? [],
    recommendation: dto.recommendation,
  };
}

// ============================================================
// RECOMMENDATION (explainable)
// ============================================================

/** Backend RecommendationResponse (com.nexploy.campuslink.recommendation.dto.RecommendationResponse). */
export interface RecommendationResponseDto {
  applicationId: string;
  studentId: string;
  driveId: string;
  recommendationType: RecommendationType;
  eligibilityPassed: boolean;
  eligibilityChecks: { label: string; passed: boolean; detail: string }[];
  overallFit: number | null;
  breakdown: MatchResponseDto["breakdown"] | null;
  whySelected: string[] | null;
  weakOrMissing: string[] | null;
  aiExplanation: string | null;
  improvementAreas: string[] | null;
  explanationStatus: ExplanationStatus | null;
  computedAt: string | null;
}

/**
 * Maps the backend recommendation to the frontend Recommendation. Deterministic evidence
 * (type, eligibility, fit, breakdown, reason lists) is passed through faithfully; the AI overlay
 * (`aiExplanation`/`improvementAreas`/`explanationStatus`) may be null when AI is disabled or the
 * ranker degraded to eligibility-only.
 */
export function mapRecommendation(dto: RecommendationResponseDto): Recommendation {
  return {
    applicationId: dto.applicationId,
    studentId: dto.studentId,
    driveId: dto.driveId,
    recommendationType: dto.recommendationType,
    eligibilityPassed: dto.eligibilityPassed,
    eligibilityChecks: (dto.eligibilityChecks ?? []).map((check) => ({
      label: check.label,
      passed: check.passed,
      detail: check.detail,
    })),
    overallFit: dto.overallFit,
    breakdown: dto.breakdown ? { ...dto.breakdown } : null,
    whySelected: dto.whySelected ?? [],
    weakOrMissing: dto.weakOrMissing ?? [],
    aiExplanation: dto.aiExplanation,
    improvementAreas: dto.improvementAreas ?? [],
    explanationStatus: dto.explanationStatus,
    computedAt: dto.computedAt,
  };
}

/** Backend CandidateRecommendationResponse. */
export interface CandidateRecommendationResponseDto {
  studentId: string;
  overallFit: number;
  recommendationType: RecommendationType;
  eligibilityPassed: boolean;
  whySelected: string[] | null;
  weakOrMissing: string[] | null;
  aiExplanation: string | null;
  explanationStatus: ExplanationStatus | null;
}

export function mapCandidateRecommendation(
  dto: CandidateRecommendationResponseDto,
): CandidateRecommendation {
  return {
    studentId: dto.studentId,
    overallFit: dto.overallFit,
    recommendationType: dto.recommendationType,
    eligibilityPassed: dto.eligibilityPassed,
    whySelected: dto.whySelected ?? [],
    weakOrMissing: dto.weakOrMissing ?? [],
    aiExplanation: dto.aiExplanation,
    explanationStatus: dto.explanationStatus,
  };
}

// ============================================================
// STUDENT
// ============================================================

/** Backend StudentResponse (com.nexploy.campuslink.student.dto.StudentResponse). */
export interface StudentResponseDto {
  id: string;
  campusId: string;
  name: string;
  email: string;
  phone: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  rollNumber: string;
  department: string | null;
  branch: string;
  branchCode: string;
  batch: string | null;
  semester: number | null;
  graduationYear: number;
  year: string | null;
  cgpa: number;
  tenthPercentage: number | null;
  twelfthPercentage: number | null;
  backlogs: number;
  backlogHistory: number | null;
  readinessScore: number | null;
  placementProbability: number | null;
  resumeScore: number | null;
  targetRole: string | null;
  riskLevel: string | null;
  riskReason: string | null;
  placementStatus: string;
  strengths: string[];
  criticalGaps: string[];
  certifications: string[];
  projects: { name: string; stack: string[]; summary: string; githubUrl?: string; liveUrl?: string }[];
  experience?: { company: string; role: string; duration: string; summary: string }[];
  assessments: {
    aptitude: number;
    coding: number;
    technical: number;
    communication: number;
    mockInterview: number;
  } | null;
  breakdown: {
    academic: number;
    aptitude: number;
    communication: number;
    projects: number;
    interview: number;
  } | null;
  readinessTrend: number[];
  preferredRoles: string[];
  preferredDomains: string[];
  preferredLocations: string[];
  /** Directory provenance (V60): "DIRECTORY" | "SELF_REGISTERED". */
  source?: string;
  /** Soft-active flag (V60); absent on older responses means active. */
  active?: boolean;
}

const ZERO_ASSESSMENTS = { aptitude: 0, coding: 0, technical: 0, communication: 0, mockInterview: 0 };
const ZERO_BREAKDOWN = {
  academic: 0, aptitude: 0, communication: 0, projects: 0, interview: 0,
};

/**
 * Maps a backend student to the frontend Student. The backend now carries the rich profile,
 * so most fields are a direct copy. Honest notes on what the backend does NOT provide, defaulted
 * here rather than fabricated: `skillGaps` (served by POST /students/{id}/skill-gap), `roadmap`
 * (not modelled), and `activeDrives`/`applicationsCount` (aggregate counts) default to empty/zero;
 * `mentorId` is left undefined; `avatarInitials` is derived from the name. `readiness` maps from
 * the backend `readinessScore`.
 */
export function mapStudent(dto: StudentResponseDto): Student {
  return {
    id: dto.id,
    name: dto.name,
    rollNumber: dto.rollNumber,
    email: dto.email,
    phone: dto.phone ?? "",
    linkedinUrl: dto.linkedinUrl ?? undefined,
    githubUrl: dto.githubUrl ?? undefined,
    avatarInitials: initials(dto.name),
    campusId: dto.campusId,
    department: dto.department ?? "",
    branch: dto.branch,
    branchCode: dto.branchCode,
    batch: dto.batch ?? "",
    semester: dto.semester ?? 0,
    graduationYear: dto.graduationYear,
    year: dto.year ?? "",
    cgpa: dto.cgpa,
    tenthPercentage: dto.tenthPercentage ?? 0,
    twelfthPercentage: dto.twelfthPercentage ?? 0,
    backlogs: dto.backlogs,
    backlogHistory: dto.backlogHistory ?? 0,
    strengths: dto.strengths ?? [],
    criticalGaps: dto.criticalGaps ?? [],
    projects: dto.projects ?? [],
    experience: dto.experience ?? [],
    certifications: dto.certifications ?? [],
    resumeScore: dto.resumeScore ?? 0,
    assessments: dto.assessments ?? ZERO_ASSESSMENTS,
    preferredRoles: dto.preferredRoles ?? [],
    preferredDomains: dto.preferredDomains ?? [],
    preferredLocations: dto.preferredLocations ?? [],
    readiness: dto.readinessScore ?? 0,
    placementProbability: dto.placementProbability ?? 0,
    readinessTrend: dto.readinessTrend ?? [],
    breakdown: dto.breakdown ?? ZERO_BREAKDOWN,
    targetRole: dto.targetRole ?? "",
    skillGaps: [], // served by POST /students/{id}/skill-gap
    riskLevel: (dto.riskLevel ?? "LOW") as Student["riskLevel"],
    riskReason: dto.riskReason ?? "",
    placementStatus: dto.placementStatus as Student["placementStatus"],
    activeDrives: 0,
    applicationsCount: 0,
    source: (dto.source ?? "SELF_REGISTERED") as Student["source"],
    active: dto.active ?? true,
  };
}

// ============================================================
// RECRUITER
// ============================================================

/** Backend RecruiterResponse (com.nexploy.campuslink.recruiter.dto.RecruiterResponse). */
export interface RecruiterResponseDto {
  id: string;
  campusId: string;
  name: string;
  companyName: string;
  email: string;
  active: boolean;
  phone: string | null;
  companyWebsite: string | null;
  companyIndustry: string | null;
  companyLocation: string | null;
  designation: string | null;
  logoInitial: string | null;
  companyDescription: string | null;
  companySize: string | null;
  companyLinkedin: string | null;
  companyTwitter: string | null;
  companyGithub: string | null;
  activeDriveIds: string[];
  studentsHired: number;
}

/**
 * Maps a backend recruiter to the frontend Recruiter. Company columns (industry, website,
 * location, description, size, social links) come straight from the row; `activeDrives` and
 * `studentsHired` are computed server-side from the recruiter's real drives/offers. `logoInitial`
 * is the saved initial, falling back to the first letter of the company name. `aboutText`,
 * `employees` and `hiringSince` have no backend column and stay empty/zero rather than fabricated
 * — the UI hides them when blank.
 */
export function mapRecruiter(dto: RecruiterResponseDto): Recruiter {
  return {
    id: dto.id,
    companyName: dto.companyName,
    logoInitial: dto.logoInitial || initials(dto.companyName).slice(0, 1) || "?",
    industry: dto.companyIndustry ?? "",
    aboutText: "",
    activeDrives: dto.activeDriveIds ?? [],
    website: dto.companyWebsite ?? "",
    hqLocation: dto.companyLocation ?? "",
    employees: "",
    hiringSince: 0,
    studentsHired: dto.studentsHired ?? 0,
    name: dto.name ?? "",
    email: dto.email ?? "",
    phone: dto.phone ?? "",
    designation: dto.designation ?? "",
    companyDescription: dto.companyDescription ?? "",
    companySize: dto.companySize ?? "",
    companyLinkedin: dto.companyLinkedin ?? "",
    companyTwitter: dto.companyTwitter ?? "",
    companyGithub: dto.companyGithub ?? "",
    active: dto.active,
  };
}

// ============================================================
// CAMPUS (identity only)
// ============================================================

/** Backend CampusResponse (com.nexploy.campuslink.campus.dto.CampusResponse). */
export interface CampusResponseDto {
  id: string;
  name: string;
  city: string;
  state: string;
  active: boolean;
}

/**
 * Maps a backend campus to the frontend CampusSummary — pure identity. The backend Campus
 * entity has NO metric columns; per-campus figures (students, placement rate, CTC, offers,
 * recruiters) come from the analytics dashboard endpoint, not from here, so this mapper never
 * fabricates them.
 */
export function mapCampus(dto: CampusResponseDto): CampusSummary {
  return { id: dto.id, name: dto.name, city: dto.city, state: dto.state, active: dto.active };
}

// ============================================================
// NOTIFICATION
// ============================================================

/** Backend NotificationResponse (com.nexploy.campuslink.notification.dto.NotificationResponse). */
export interface NotificationResponseDto {
  id: string;
  campusId: string;
  recipientUsername: string;
  channel: NotificationChannel;
  type: NotificationType | null;
  referenceId: string | null;
  subject: string;
  body: string;
  status: NotificationStatus;
  attemptCount: number;
  createdAt: string;
  lastAttemptAt: string | null;
  readAt: string | null;
}

/**
 * Maps a backend notification to the frontend NotificationItem. Every field is real: `title`
 * is the subject, `message` the body, `read` is derived from the authoritative status, and
 * `timestamp` is the server createdAt. There is no semantic topic type to map — the backend
 * does not classify notifications by subject matter.
 */
export function mapNotification(dto: NotificationResponseDto): NotificationItem {
  return {
    id: dto.id,
    title: dto.subject,
    message: dto.body,
    timestamp: dto.createdAt,
    read: dto.status === "READ",
    channel: dto.channel,
    status: dto.status,
    recipientUsername: dto.recipientUsername,
  };
}

/**
 * Maps a backend notification to the richer {@link AdminNotification} the admin console renders.
 * Unlike {@link mapNotification} this keeps the operational fields — semantic `type`, `referenceId`,
 * `attemptCount`, `lastAttemptAt`, `campusId` — so an operator can triage stuck outbound rows.
 */
export function mapAdminNotification(dto: NotificationResponseDto): AdminNotification {
  return {
    id: dto.id,
    campusId: dto.campusId,
    recipientUsername: dto.recipientUsername,
    channel: dto.channel,
    type: dto.type ?? null,
    referenceId: dto.referenceId ?? null,
    subject: dto.subject,
    body: dto.body,
    status: dto.status,
    attemptCount: dto.attemptCount,
    createdAt: dto.createdAt,
    lastAttemptAt: dto.lastAttemptAt ?? null,
    readAt: dto.readAt ?? null,
  };
}

/** Backend DeliveryAttemptResponse (com.nexploy.campuslink.notification.dto.DeliveryAttemptResponse). */
export interface DeliveryAttemptResponseDto {
  id: string;
  notificationId: string;
  attemptNumber: number;
  outcome: NotificationStatus;
  detail: string | null;
  attemptedAt: string;
}

/** Identity passthrough — the attempt trail is already the shape the UI renders. */
export function mapDeliveryAttempt(dto: DeliveryAttemptResponseDto): DeliveryAttempt {
  return {
    id: dto.id,
    notificationId: dto.notificationId,
    attemptNumber: dto.attemptNumber,
    outcome: dto.outcome,
    detail: dto.detail ?? null,
    attemptedAt: dto.attemptedAt,
  };
}

// ============================================================
// OFFER
// ============================================================

/** Backend OfferResponse (com.nexploy.campuslink.offer.dto.OfferResponse). */
export interface OfferResponseDto {
  id: string;
  campusId: string;
  studentId: string;
  driveId: string;
  recruiterId: string;
  companyName: string | null;
  role: string;
  packageLpa: number | null;
  offerType: string | null;
  location: string | null;
  joiningDate: string | null;
  acceptanceDeadline: string | null;
  documentsVerified: boolean;
  joined: boolean;
  fromInternship: string | null;
  status: string; // EXTENDED | ACCEPTED | DECLINED | REVOKED
  extendedAt: string | null;
  decidedAt: string | null;
  decidedBy: string | null;
}

const OFFER_STATUS: Record<string, Offer["status"]> = {
  EXTENDED: "OFFER RECEIVED",
  ACCEPTED: "ACCEPTED",
  DECLINED: "DECLINED",
  REVOKED: "DECLINED", // recruiter-withdrawn; the frontend has no distinct revoked state
};

/**
 * Maps a backend offer to the frontend Offer. `package` is formatted from the numeric
 * `packageLpa`; `offerDate` comes from `extendedAt`; the backend EXTENDED/ACCEPTED/DECLINED/
 * REVOKED status maps to the frontend's OFFER RECEIVED / ACCEPTED / DECLINED vocabulary
 * (REVOKED → DECLINED, since the UI has no revoked state).
 */
export function mapOffer(dto: OfferResponseDto): Offer {
  return {
    id: dto.id,
    studentId: dto.studentId,
    driveId: dto.driveId,
    companyName: dto.companyName ?? "",
    role: dto.role,
    package: dto.packageLpa != null ? `₹${dto.packageLpa} LPA` : "",
    offerType: (dto.offerType ?? "Full-Time") as Offer["offerType"],
    location: dto.location ?? "",
    offerDate: formatDate(dto.extendedAt),
    acceptanceDeadline: formatDate(dto.acceptanceDeadline),
    joiningDate: formatDate(dto.joiningDate),
    status: OFFER_STATUS[dto.status] ?? "OFFER RECEIVED",
    fromInternship: dto.fromInternship ?? undefined,
    documentsVerified: dto.documentsVerified,
    joined: dto.joined,
  };
}

// ============================================================
// DOCUMENT
// ============================================================

/** Backend DocumentResponse (com.nexploy.campuslink.document.dto.DocumentResponse). */
export interface DocumentResponseDto {
  id: string;
  campusId: string;
  studentId: string;
  type: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  scanStatus: string; // PENDING | CLEAN
  reviewStatus: string; // PENDING | APPROVED | REJECTED
  reviewedBy: string | null;
  reviewedAt: string | null;
  uploadedAt: string | null;
  /** False for legacy metadata-only rows that never received file bytes (V66). */
  hasFile: boolean;
}

/**
 * Maps a backend document to the frontend DocumentItem. The backend splits scan vs review
 * status; the frontend shows a single status, derived here: APPROVED → VERIFIED,
 * REJECTED → NEEDS UPDATE (the student must re-upload), else a CLEAN scan → UNDER REVIEW,
 * else PENDING. `name` is the file name (falling back to the type). `contentType` and
 * `hasFile` pass through so the officer preview dialog knows what to render (and whether
 * there is anything to render at all).
 */
export function mapDocument(dto: DocumentResponseDto): DocumentItem {
  const status: DocumentItem["status"] =
    dto.reviewStatus === "APPROVED" ? "VERIFIED"
      : dto.reviewStatus === "REJECTED" ? "NEEDS UPDATE"
      : dto.scanStatus === "CLEAN" ? "UNDER REVIEW"
      : "PENDING";
  return {
    id: dto.id,
    studentId: dto.studentId,
    name: dto.fileName || dto.type,
    status,
    updatedOn: formatDate(dto.reviewedAt ?? dto.uploadedAt),
    contentType: dto.contentType,
    hasFile: dto.hasFile,
  };
}

// ============================================================
// INTERVIEW
// ============================================================

/** Backend InterviewResponse (com.nexploy.campuslink.interview.dto.InterviewResponse). */
export interface InterviewResponseDto {
  id: string;
  campusId: string;
  studentId: string;
  driveId: string;
  scheduleEventId: string | null;
  companyName: string | null;
  round: string | null;
  venue: string | null;
  panel: string | null;
  mode: string; // ON_CAMPUS | VIRTUAL
  scheduledAt: string | null;
  status: string; // SCHEDULED | COMPLETED | CANCELLED
  result: string; // PENDING | SELECTED | REJECTED
  feedback: string | null;
}

const INTERVIEW_RESULT: Record<string, InterviewSlot["result"]> = {
  PENDING: "AWAITED",
  SELECTED: "CLEARED",
  REJECTED: "NOT CLEARED",
};

/**
 * Maps a backend interview to the frontend InterviewSlot. Date/time are split out of
 * `scheduledAt`; mode ON_CAMPUS/VIRTUAL → In-Person/Virtual; result PENDING/SELECTED/REJECTED →
 * AWAITED/CLEARED/NOT CLEARED. `companyName`/`round`/`venue`/`panel` come from the backend
 * (V21 enrichment), defaulting to empty when absent.
 */
export function mapInterview(dto: InterviewResponseDto): InterviewSlot {
  const when = dto.scheduledAt ? new Date(dto.scheduledAt) : null;
  const validWhen = when && !Number.isNaN(when.getTime()) ? when : null;
  return {
    id: dto.id,
    studentId: dto.studentId,
    driveId: dto.driveId,
    companyName: dto.companyName ?? "",
    round: dto.round ?? "",
    date: validWhen ? validWhen.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" }) : "",
    time: validWhen ? validWhen.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" }) : "",
    mode: dto.mode === "VIRTUAL" ? "Virtual" : "In-Person",
    venue: dto.venue ?? "",
    panel: dto.panel ?? "",
    status: dto.status as InterviewSlot["status"],
    result: INTERVIEW_RESULT[dto.result] ?? "AWAITED",
    feedback: dto.feedback ?? null,
  };
}

// ---- Schedule (Drive Orchestrator) ----------------------------------

/** Backend ScheduleEventResponse (com.nexploy.campuslink.schedule.dto.ScheduleEventResponse). */
export interface ScheduleEventResponseDto {
  id: string;
  driveId: string;
  campusId: string;
  venueId: string | null;
  venueName: string | null;
  panelId: string | null;
  panelName: string | null;
  startsAt: string;
  endsAt: string;
  expectedParticipants: number | null;
  venueCapacity: number | null;
  conflict: boolean;
  activityType?: string | null;
  participantMode?: string | null;
}

export function mapScheduleEvent(dto: ScheduleEventResponseDto): ScheduleEvent {
  return {
    id: dto.id,
    driveId: dto.driveId,
    campusId: dto.campusId,
    venueId: dto.venueId,
    venueName: dto.venueName,
    panelId: dto.panelId,
    panelName: dto.panelName,
    startsAt: dto.startsAt,
    endsAt: dto.endsAt,
    expectedParticipants: dto.expectedParticipants,
    venueCapacity: dto.venueCapacity,
    conflict: dto.conflict,
    activityType: dto.activityType ?? null,
    participantMode: dto.participantMode ?? null,
  };
}

/** Backend ScheduleConflictResponse (com.nexploy.campuslink.schedule.dto.ScheduleConflictResponse). */
export interface ScheduleConflictResponseDto {
  type: ScheduleConflictType;
  eventId: string;
  conflictingEventId: string | null;
  resourceId: string | null;
  overlapStart: string | null;
  overlapEnd: string | null;
  message: string;
}

export function mapScheduleConflict(dto: ScheduleConflictResponseDto): ScheduleConflict {
  return {
    type: dto.type,
    eventId: dto.eventId,
    conflictingEventId: dto.conflictingEventId,
    resourceId: dto.resourceId,
    overlapStart: dto.overlapStart,
    overlapEnd: dto.overlapEnd,
    message: dto.message,
  };
}

/** Backend VenueResponse (com.nexploy.campuslink.schedule.dto.VenueResponse). */
export interface VenueResponseDto {
  id: string;
  campusId: string;
  name: string;
  capacity: number | null;
  active: boolean;
}

export function mapVenue(dto: VenueResponseDto): ScheduleVenue {
  return {
    id: dto.id,
    campusId: dto.campusId,
    name: dto.name,
    capacity: dto.capacity,
    active: dto.active,
  };
}

/** Backend PanelResponse (com.nexploy.campuslink.schedule.dto.PanelResponse). */
export interface PanelResponseDto {
  id: string;
  campusId: string;
  name: string;
  active: boolean;
  members: { id: string; name: string; email: string }[];
}

export function mapPanel(dto: PanelResponseDto): SchedulePanel {
  return {
    id: dto.id,
    campusId: dto.campusId,
    name: dto.name,
    active: dto.active,
    members: (dto.members ?? []).map((m) => ({ id: m.id, name: m.name, email: m.email })),
  };
}

/**
 * Backend OptimizationResponse. The outer record is camelCase, but the inner
 * OptimizationAlternative is serialized snake_case (@JsonNaming SnakeCaseStrategy),
 * so the alternative DTO is mapped field-by-field.
 */
export interface OptimizationAlternativeDto {
  rank: number;
  event_id: string;
  starts_at: string;
  ends_at: string;
  venue_id: string;
  panel_id: string;
  hard_violations: number;
  soft_score: number;
  reason_codes: string[] | null;
  evidence: string[] | null;
}

export interface OptimizationResponseDto {
  runId: string;
  status: string;
  scheduleVersion: string;
  hardViolations: number;
  softScore: number;
  alternatives: OptimizationAlternativeDto[] | null;
  selectedAssignments: OptimizationAlternativeDto[] | null;
}

function mapOptimizationAlternative(dto: OptimizationAlternativeDto): OptimizationAlternative {
  return {
    rank: dto.rank,
    eventId: dto.event_id,
    startsAt: dto.starts_at,
    endsAt: dto.ends_at,
    venueId: dto.venue_id,
    panelId: dto.panel_id,
    hardViolations: dto.hard_violations,
    softScore: dto.soft_score,
    reasonCodes: dto.reason_codes ?? [],
    evidence: dto.evidence ?? [],
  };
}

export function mapOptimizationRun(dto: OptimizationResponseDto): OptimizationRun {
  return {
    runId: dto.runId,
    status: dto.status,
    scheduleVersion: dto.scheduleVersion,
    hardViolations: dto.hardViolations,
    softScore: dto.softScore,
    alternatives: (dto.alternatives ?? []).map(mapOptimizationAlternative),
    selectedAssignments: (dto.selectedAssignments ?? []).map(mapOptimizationAlternative),
  };
}
