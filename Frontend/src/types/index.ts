// ============================================================
// NEXPLOY PROTOTYPE — SHARED TYPES
// These interfaces define the shape of every mock entity. Production
// will later populate these same shapes from real APIs/DB — component
// code should never need to change when that swap happens.
// ============================================================

export type Role = "student" | "recruiter" | "officer" | "mentor" | "admin";

/** Placement cycle status vocabulary — mirrors the backend PLANNING→ACTIVE→CLOSED→ARCHIVED lifecycle. */
export type PlacementCycleStatus = "PLANNING" | "ACTIVE" | "CLOSED" | "ARCHIVED";

/** A placement cycle groups a campus's drives/history for an academic year (backend V34). */
export interface PlacementCycle {
  id: string;
  campusId: string;
  name: string;
  academicYear: string;
  startsOn: string | null;
  endsOn: string | null;
  status: PlacementCycleStatus;
}

export type RiskLevel = "HIGH" | "MEDIUM" | "LOW";

export type ApplicationStatus =
  | "eligible"
  | "applied"
  | "shortlisted"
  | "assessment"
  | "interview"
  | "selected"
  | "offer"
  | "accepted"
  | "docs"
  | "joined"
  | "rejected"
  | "withdrawn";

export type DocumentStatus = "VERIFIED" | "UNDER REVIEW" | "PENDING" | "UPLOADED" | "NEEDS UPDATE";

export type PlacementStatus = "UNPLACED" | "IN PROCESS" | "OFFERED" | "PLACED";

// ---- Campus ----------------------------------------------------------

export interface Campus {
  id: string;
  name: string;
  city: string;
  totalStudents: number;
  placementReady: number;
  placed: number;
  atRisk: number;
  placementRate: number;
  averageCtc: string;
  highestCtc: string;
  activeDrives: number;
  offers: number;
  recruiters: number;
}

/**
 * Authoritative campus identity as served by GET /api/campuses (CampusResponse). The heavy
 * metric fields on {@link Campus} (student counts, placement rate, CTC, offers, recruiters)
 * are NOT campus-entity columns — they are analytics aggregations served per campus by
 * /api/campuses/{id}/analytics/dashboard. Identity consumers (selectors, name labels) use
 * this type so nothing gets fabricated.
 */
export interface CampusSummary {
  id: string;
  name: string;
  city: string;
  state: string;
  /** Soft-delete flag — a deactivated campus is hidden from scheduling but keeps its history. */
  active: boolean;
}

// ---- Student ---------------------------------------------------------

/** The five readiness factors shown on Readiness + Digital Twin. */
export interface ReadinessBreakdown {
  academic: number;
  aptitude: number;
  communication: number;
  projects: number;
  interview: number;
}

/** Per-dimension breakdown as served by GET /api/students/me/readiness. Unlike the
 * always-populated {@link ReadinessBreakdown} used on the Digital Twin, any dimension the
 * ML service had no data for is null (excluded + renormalised, never a fabricated 0), so the
 * UI can show "Not Available" for it. */
export interface ReadinessSummaryBreakdown {
  academic: number | null;
  aptitude: number | null;
  communication: number | null;
  projects: number | null;
  interview: number | null;
}

/** Read-only readiness summary from GET /api/students/me/readiness — the authoritative
 * overall score plus its five-factor breakdown. `breakdown` is null when unscored. */
export interface ReadinessSummary {
  score: number | null;
  breakdown: ReadinessSummaryBreakdown | null;
}

export interface AssessmentScores {
  aptitude: number;
  coding: number;
  technical: number;
  communication: number;
  mockInterview: number;
}

export interface SkillGap {
  skill: string;
  currentLevel?: number | null; // null when NO_EVIDENCE / UNKNOWN
  targetLevel?: number | null;  // null when presence-only matching
  /** Human-readable level, e.g. "Beginner", "None", "No Evidence", or a raw score. */
  currentLabel: string;
  targetLabel: string;
  priority: "HIGH" | "MEDIUM" | "LOW";
  whyItMatters: string;
  recommendedAction: string;
  /** Verification status of the stored skill (VERIFIED / EVIDENCED / SELF_DECLARED), or absent. */
  verificationStatus?: string | null;
  /** Evidence status: VERIFIED, EVIDENCED, SELF_DECLARED, NO_EVIDENCE, UNKNOWN */
  evidenceStatus?: "VERIFIED" | "EVIDENCED" | "SELF_DECLARED" | "NO_EVIDENCE" | "UNKNOWN" | null;
}

export interface StudentProject {
  name: string;
  stack: string[];
  summary: string;
  githubUrl?: string;
  liveUrl?: string;
}

export interface StudentExperience {
  company: string;
  role: string;
  duration: string;
  summary: string;
}

export interface Student {
  id: string;
  name: string;
  rollNumber: string;
  email: string;
  phone: string;
  /** Social links (V69): optional, shown on the profile hero only when set. */
  linkedinUrl?: string;
  githubUrl?: string;
  avatarInitials: string;

  // Academic
  campusId: string;
  department: string;
  branch: string;
  branchCode: string;
  batch: string;
  semester: number;
  graduationYear: number;
  year: string;
  cgpa: number;
  tenthPercentage: number;
  twelfthPercentage: number;
  backlogs: number;
  backlogHistory: number;

  // Professional
  strengths: string[];
  criticalGaps: string[];
  projects: StudentProject[];
  experience: StudentExperience[];
  certifications: string[];
  resumeScore: number;

  // Assessment
  assessments: AssessmentScores;

  // Preferences
  preferredRoles: string[];
  preferredDomains: string[];
  preferredLocations: string[];

  // Intelligence
  readiness: number; // 0-100
  placementProbability: number; // 0-100
  readinessTrend: number[];
  breakdown: ReadinessBreakdown;
  targetRole: string;
  skillGaps: SkillGap[];
  riskLevel: RiskLevel;
  riskReason: string;

  // Placement
  placementStatus: PlacementStatus;
  mentorId?: string;
  activeDrives: number;
  applicationsCount: number;

  // Directory provenance (V60): DIRECTORY rows are officer-managed and claimed on
  // registration; SELF_REGISTERED is the legacy default. `active` is the soft-delete
  // flag — deactivation disables the linked account too.
  source: "SELF_REGISTERED" | "DIRECTORY";
  active: boolean;
}

// ---- Drives & eligibility -------------------------------------------

export type DriveStatus = "DRAFT" | "ACTIVE" | "CLOSED" | "COMPLETED" | "CANCELLED";
export type DriveType = "On-Campus" | "Off-Campus" | "Virtual" | "Pooled";

/** Structured, machine-checkable criteria. src/lib/eligibility.ts
 * evaluates these against any Student — deterministic, explainable, and
 * deliberately kept separate from the Match Score. */
export interface EligibilityCriteria {
  minCgpa: number;
  allowedBranches: string[]; // branch codes, e.g. ["CSE", "IT"]
  maxBacklogs: number;
  graduationYear: number;
  minTenthPercentage?: number;
  minTwelfthPercentage?: number;
}

export interface EligibilityCheck {
  label: string;
  passed: boolean;
  /** Deterministic, human-readable proof, e.g. "8.59 >= 7.0". */
  detail: string;
}

export interface EligibilityResult {
  eligible: boolean;
  checks: EligibilityCheck[];
  failureReasons: string[];
}

export interface SelectionRound {
  name: string;
  description: string;
}

export interface Drive {
  id: string;
  companyId: string;
  companyName: string;
  role: string;
  driveType: DriveType;
  campusId: string;
  status: DriveStatus;
  description: string;
  requiredSkills: string[];
  criteria: EligibilityCriteria;
  rounds: SelectionRound[];
  location: string;
  package: string;
  expectedHiring: number;
  deadline: string;
  /** Raw backend deadline (YYYY-MM-DD, date-only) for round-tripping into a date input on edit.
   * `deadline` above is the display-formatted form; this is the unformatted source value. */
  deadlineIso: string | null;
  driveDate: string;
  driveTime: string;
  venue: string;
  logoInitial: string;
}

export interface Recruiter {
  id: string;
  companyName: string;
  logoInitial: string;
  industry: string;
  aboutText: string;
  activeDrives: string[]; // drive ids
  website: string;
  hqLocation: string;
  employees: string;
  hiringSince: number;
  studentsHired: number;
  // Self-service profile fields (backend V32). Present on GET /recruiters/me; may be empty.
  name: string; // recruiter contact person's name
  email: string; // login username
  phone: string;
  designation: string;
  companyDescription: string;
  companySize: string;
  companyLinkedin: string;
  companyTwitter: string;
  companyGithub: string;
  /** Soft-delete flag — a deactivated recruiter loses access but keeps drives/history. */
  active: boolean;
}

/** The signed-in recruiter's persisted settings (backend recruiter_settings, V32). */
export interface RecruiterSettings {
  notifyNewApplication: boolean;
  notifyShortlisted: boolean;
  notifyInterview: boolean;
  notifyOffer: boolean;
  notifyDrive: boolean;
  emailNotifications: boolean;
  inAppNotifications: boolean;
  theme: string;
  language: string;
  timezone: string;
  dateFormat: string;
  profileVisibility: string;
  contactVisibility: string;
  allowDataAnalytics: boolean;
  allowMarketingEmail: boolean;
}

export interface Application {
  id: string;
  studentId: string;
  driveId: string;
  status: ApplicationStatus;
  appliedOn: string;
}

// ---- Matching --------------------------------------------------------

export interface MatchBreakdown {
  eligibility: number;
  skills: number;
  semanticMatch: number;
  projects: number;
  interview: number;
  readiness: number;
  academics: number;
  /** Share of the drive's required skills backed by the student's certifications (0-100). */
  certificationRelevance: number;
  /** Pass rate over the student's completed past interviews (0-100), current drive excluded. */
  pastInterviews: number;
}

export interface CandidateMatch {
  id: string;
  studentId: string;
  driveId: string;
  overallFit: number;
  breakdown: MatchBreakdown;
  whySelected: string[];
  weakOrMissing: string[];
  recommendation: string;
}

// ---- Explainable recommendation -------------------------------------

/**
 * The derived recommendation state. Computed deterministically on the backend from the eligibility
 * verdict and the latest officer shortlist decision — never by the LLM, and never tied to the
 * official application lifecycle status.
 */
export type RecommendationType =
  | "NOT_ELIGIBLE"
  | "ELIGIBLE"
  | "SHORTLISTED"
  | "NOT_SHORTLISTED";

/** OK = LLM-written prose; FALLBACK = deterministic template (AI disabled/unavailable). */
export type ExplanationStatus = "OK" | "FALLBACK";

/**
 * Explainable recommendation for one application. Everything except `aiExplanation`/`improvementAreas`
 * is deterministic, verified evidence. When the ranker was unreachable the response degrades to
 * eligibility-only: `overallFit`/`breakdown`/`computedAt` are null and the skill lists are empty.
 */
export interface Recommendation {
  applicationId: string;
  studentId: string;
  driveId: string;
  recommendationType: RecommendationType;
  eligibilityPassed: boolean;
  eligibilityChecks: EligibilityCheck[];
  overallFit: number | null;
  breakdown: MatchBreakdown | null;
  whySelected: string[];
  weakOrMissing: string[];
  aiExplanation: string | null;
  improvementAreas: string[];
  explanationStatus: ExplanationStatus | null;
  computedAt: string | null;
}

/** One ranked candidate in a drive's recommendation list; order is the deterministic fit ranking. */
export interface CandidateRecommendation {
  studentId: string;
  overallFit: number;
  recommendationType: RecommendationType;
  eligibilityPassed: boolean;
  whySelected: string[];
  weakOrMissing: string[];
  aiExplanation: string | null;
  explanationStatus: ExplanationStatus | null;
}

// ---- Interviews & assessments ---------------------------------------

export type InterviewStatus = "SCHEDULED" | "COMPLETED" | "CANCELLED";

export interface InterviewSlot {
  id: string;
  studentId: string;
  driveId: string;
  companyName: string;
  round: string;
  date: string;
  time: string;
  mode: "In-Person" | "Virtual";
  venue: string;
  panel: string;
  status: InterviewStatus;
  result: "CLEARED" | "NOT CLEARED" | "AWAITED";
  feedback?: string | null;
}

// ---- Offers & documents ---------------------------------------------

export type OfferStatus = "OFFER RECEIVED" | "ACCEPTED" | "DECLINED" | "PENDING ACCEPTANCE";

export type OfferType = "Full-Time" | "Internship" | "PPO" | "Internship + PPO";

export interface Offer {
  id: string;
  studentId: string;
  driveId: string;
  companyName: string;
  role: string;
  package: string;
  offerType: OfferType;
  location: string;
  offerDate: string;
  acceptanceDeadline: string;
  joiningDate: string;
  status: OfferStatus;
  /** PPO tracking — set when the offer originated from an internship. */
  fromInternship?: string;
  documentsVerified: boolean;
  joined: boolean;
}

export interface DocumentItem {
  id: string;
  studentId: string;
  name: string;
  status: DocumentStatus;
  updatedOn: string;
  note?: string;
  /** MIME type of the stored file (application/pdf | image/png | image/jpeg). */
  contentType: string;
  /** False for legacy metadata-only rows that never received file bytes. */
  hasFile: boolean;
}

// ---- Notifications ---------------------------------------------------

/** Delivery channel — a real backend field on the Notification entity. */
export type NotificationChannel = "EMAIL" | "SMS" | "IN_APP";

/** Delivery/read lifecycle status — a real backend field. */
export type NotificationStatus = "PENDING" | "SENT" | "FAILED" | "READ";

/**
 * A notification as the backend actually stores it. There is deliberately no semantic
 * `type` (drive/interview/offer/…) or `audience[]` — the delivery system does not classify
 * content by topic, and visibility is scoped server-side by `recipientUsername` (a recipient
 * sees their own; officers/admins see their campus). A topic taxonomy would be a Phase 3/4
 * backend addition, not something to fabricate here.
 */
export interface NotificationItem {
  id: string;
  title: string;
  message: string;
  /** ISO-8601 creation timestamp from the server (createdAt). */
  timestamp: string;
  read: boolean;
  channel: NotificationChannel;
  status: NotificationStatus;
  recipientUsername: string;
}

/**
 * Semantic notification type — mirrors the backend `NotificationType` enum. Used only by the
 * preferences UI (opt-out per type); the notification list itself carries no `type` (see above).
 */
export type NotificationType =
  | "OFFER_EXTENDED"
  | "OFFER_ACCEPTED"
  | "OFFER_DECLINED"
  | "OFFER_REVOKED"
  | "APPLICATION_ADVANCED"
  | "SHORTLISTED"
  | "SHORTLIST_REJECTED"
  | "INTERVIEW_SCHEDULED"
  | "INTERVIEW_RESCHEDULED"
  | "INTERVIEW_CANCELLED"
  | "INTERVIEW_REMINDER"
  | "OFFER_DEADLINE"
  | "DOCUMENT_APPROVED"
  | "DOCUMENT_REJECTED"
  | "DRIVE_PUBLISHED"
  | "RISK_DETECTED"
  | "INTERVENTION_ASSIGNED";

/**
 * A per-user, per-type delivery preference. Opt-out model: absence of a stored row means enabled,
 * so the API always returns one entry per type with the resolved `enabled` value.
 */
export interface NotificationPreference {
  type: NotificationType;
  enabled: boolean;
}

/**
 * A notification row as the admin console needs it — the full operational shape, unlike the
 * recipient-facing {@link NotificationItem}. Carries the semantic `type`, the outbound
 * `attemptCount`/`lastAttemptAt`, and `campusId` so an operator can find and act on stuck
 * email/SMS rows across the campus.
 */
export interface AdminNotification {
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

/** One row of a notification's delivery-attempt trail (admin console drill-down). */
export interface DeliveryAttempt {
  id: string;
  notificationId: string;
  attemptNumber: number;
  outcome: NotificationStatus;
  detail: string | null;
  attemptedAt: string;
}

/** Stable pagination envelope returned by paged list endpoints (mirrors the backend PageResponse). */
export interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---- Risk & mentoring ------------------------------------------------

export interface RiskFactor {
  label: string;
  detail: string;
}

export interface RiskEntry {
  /** Backend risk-entry id — used to link an intervention plan to the entry it was raised from. */
  id: string;
  studentId: string;
  level: RiskLevel;
  primaryReason: string;
  factors: RiskFactor[];
  recommendedActions: string[];
}

export interface Mentor {
  id: string;
  name: string;
  avatarInitials: string;
  department: string;
  designation: string;
  email: string;
  campusId: string;
  assignedStudentIds: string[];
}

/**
 * The authenticated caller's own identity, from GET /api/auth/me. A user account carries only
 * the login username, role and home campus — there is no display name or email on it (those
 * exist only on a Student profile), so nothing here is fabricated.
 */
export interface CurrentUser {
  username: string;
  role: Role;
  campusId: string;
}

/** A mentor↔student assignment, from GET/POST /api/mentor/assignments. */
export interface MentorAssignment {
  id: string;
  mentorUsername: string;
  studentId: string;
  campusId: string;
  assignedAt: string;
}

export type InterventionStatus = "PROPOSED" | "ACTIVE" | "COMPLETED";

export interface InterventionAction {
  id: string;
  label: string;
  done: boolean;
}

export interface InterventionPlan {
  id: string;
  studentId: string;
  mentorId: string;
  status: InterventionStatus;
  createdOn: string;
  focusAreas: string[];
  actions: InterventionAction[];
  progress: number; // 0-100
  notes: string;
}

// ---- Scheduling ------------------------------------------------------

// ---- Schedule (Drive Orchestrator) ----------------------------------
// Shapes mirror the backend schedule module exactly. Events carry ids + real
// ISO timestamps (day/date/time are derived presentationally, never stored);
// venue/panel names are resolved server-side (null when unresolved, never faked).

export type ScheduleConflictType =
  | "VENUE_OVERLAP"
  | "PANEL_OVERLAP"
  | "PANEL_MEMBER_OVERLAP"
  | "VENUE_CAPACITY"
  | "CAMPUS_BLOCK";

export interface ScheduleEvent {
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

/** A detected scheduling conflict (ConflictDetectionService). No recommendation
 * fields — resolutions come from an optimization run, not the conflict record. */
export interface ScheduleConflict {
  type: ScheduleConflictType;
  eventId: string;
  conflictingEventId: string | null;
  resourceId: string | null;
  overlapStart: string | null;
  overlapEnd: string | null;
  message: string;
}

export interface ScheduleVenue {
  id: string;
  campusId: string;
  name: string;
  capacity: number | null;
  active: boolean;
}

export interface SchedulePanelMember {
  id: string;
  name: string;
  email: string;
}

export interface SchedulePanel {
  id: string;
  campusId: string;
  name: string;
  active: boolean;
  members: SchedulePanelMember[];
}

/** One proposed reassignment from the OR-Tools optimizer. */
export interface OptimizationAlternative {
  rank: number;
  eventId: string;
  startsAt: string;
  endsAt: string;
  venueId: string;
  panelId: string;
  hardViolations: number;
  softScore: number;
  reasonCodes: string[];
  evidence: string[];
}

export interface OptimizationRun {
  runId: string;
  status: string;
  scheduleVersion: string;
  hardViolations: number;
  softScore: number;
  alternatives: OptimizationAlternative[];
  selectedAssignments: OptimizationAlternative[];
}

// ---- Analytics -------------------------------------------------------

export interface AnalyticsSnapshot {
  totalStudents: number;
  placementReady: number;
  atRisk: number;
  activeDrives: number;
  applications: number;
  offersCount: number;
  studentsPlaced: number;
  placementRate: number;
  recruitersCount: number;
  averageCtc: string;
  highestCtc: string;
}

export interface BranchStat {
  branch: string;
  branchCode: string;
  placementRate: number;
  studentsPlaced: number;
  totalStudents: number;
}

export interface SkillDemand {
  skill: string;
  demandScore: number;
}

export interface FunnelStage {
  stage: string;
  count: number;
}

export interface Alert {
  id: string;
  severity: RiskLevel;
  message: string;
}

/**
 * The full campus analytics bundle returned by GET /api/campuses/:id/analytics/dashboard,
 * shaped 1:1 to the backend DashboardAnalyticsResponse. Every series is a live aggregation
 * over the campus's students/drives/offers/applications.
 */
export interface CampusAnalytics {
  snapshot: AnalyticsSnapshot;
  branchStats: BranchStat[];
  funnel: FunnelStage[];
  offerPipeline: { month: string; offers: number }[];
  salaryTrend: { month: string; averageLpa: number; highestLpa: number }[];
  readinessDistribution: { band: string; count: number }[];
  riskDistribution: { level: string; count: number }[];
  recruiterEngagement: { company: string; drives: number; offers: number }[];
  alerts: Alert[];
}

/** One skill's demand paired with the share of the cohort that claims it (0-100). */
export interface SkillSupply {
  skill: string;
  demand: number;
  cohortCoverage: number;
}

/** Institute-wide totals rolled up across every campus the caller may access. */
export interface InstituteTotals {
  totalStudents: number;
  placementReady: number;
  placed: number;
  atRisk: number;
  offers: number;
  activeDrives: number;
  recruiters: number;
  placementRate: number;
}

/** One campus's row in the cross-campus comparison table. */
export interface CampusComparison {
  campusId: string;
  campus: string;
  city: string;
  students: number;
  ready: number;
  placed: number;
  placementRate: number;
  averageCtc: string;
}

/** GET /api/analytics/institute — institute totals + per-campus comparison rows. */
export interface InstituteAnalytics {
  totals: InstituteTotals;
  campuses: CampusComparison[];
}

export interface CopilotMessage {
  id: string;
  role: "user" | "assistant";
  text: string;
}
