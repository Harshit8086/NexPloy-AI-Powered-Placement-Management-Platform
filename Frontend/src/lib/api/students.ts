// ============================================================
// STUDENTS API — live only. Every student figure comes from Spring
// (GET /api/students, /students/{id}, /students/me and its derived
// sub-resources). There is no client-side simulation or mock fallback;
// an empty DB yields empty lists, and a failed fetch propagates so the
// caller can show an error/retry state.
// ============================================================

import { apiRequest, apiUpload } from "@/lib/api/client";
import { mapStudent, type StudentResponseDto } from "@/lib/api/mappers";
import { whatIfFactorLabels } from "@/lib/simulate";
import type { DigitalTwinResult, ResumeIntelligence, WhatIfFactor } from "@/lib/simulate";
import type {
  Student,
  StudentProject,
  StudentExperience,
  ReadinessSummary,
  AssessmentScores,
  PlacementStatus,
} from "@/types";

/**
 * Fields a student may edit on their own profile (PUT /api/students/me). Every field is optional:
 * omit one to leave it unchanged. Mirrors the backend StudentUpdateRequest; derived signals
 * (readiness, breakdown, assessments, risk) and identity/scope fields are intentionally absent.
 * Since V60 the academic/identity fields (cgpa, branch, backlogs, …) are directory-owned and
 * officer-editable only — they are not part of this request.
 */
export interface StudentUpdateInput {
  phone?: string;
  linkedinUrl?: string;
  githubUrl?: string;
  targetRole?: string;
  preferredRoles?: string[];
  preferredDomains?: string[];
  preferredLocations?: string[];
  strengths?: string[];
  projects?: StudentProject[];
  experience?: StudentExperience[];
}

function isNotFound(error: unknown): boolean {
  return error instanceof Error && "status" in error && (error as { status: number }).status === 404;
}

/** GET /api/students */
export async function listStudents(): Promise<Student[]> {
  const dtos = await apiRequest<StudentResponseDto[]>("/students");
  return dtos.map(mapStudent);
}

/** GET /api/students/:id */
export async function getStudent(id: string): Promise<Student | undefined> {
  try {
    return mapStudent(await apiRequest<StudentResponseDto>(`/students/${id}`));
  } catch (error) {
    if (isNotFound(error)) return undefined;
    throw error;
  }
}

/** Students on a campus. No dedicated endpoint yet — filters the campus-scoped list. */
export async function listStudentsByCampus(campusId: string): Promise<Student[]> {
  return (await listStudents()).filter((s) => s.campusId === campusId);
}

/** GET /api/students/me — the signed-in student's own profile. */
export async function getPrimaryStudent(): Promise<Student> {
  return mapStudent(await apiRequest<StudentResponseDto>("/students/me"));
}

/** PUT /api/students/me — the signed-in student updates their own profile. Returns the saved record. */
export async function updatePrimaryStudent(input: StudentUpdateInput): Promise<Student> {
  return mapStudent(
    await apiRequest<StudentResponseDto>("/students/me", { method: "PUT", body: input }),
  );
}

/**
 * PUT /api/students/:id — officer/admin edits any student within campus reach. Same bounded,
 * self-reportable fields as the student's own PUT /me (identity/scope and derived AI signals stay
 * untouched); campus access + officer/admin role are enforced server-side.
 */
export async function adminUpdateStudent(id: string, input: StudentUpdateInput): Promise<Student> {
  return mapStudent(
    await apiRequest<StudentResponseDto>(`/students/${id}`, { method: "PUT", body: input }),
  );
}

/**
 * PATCH /api/students/:id/placement-status — officer/admin sets a student's placement status
 * (UNPLACED / IN PROCESS / OFFERED / PLACED). The backend validates the value (unknown → 400).
 */
export async function changeStudentPlacementStatus(
  id: string,
  placementStatus: PlacementStatus,
): Promise<Student> {
  return mapStudent(
    await apiRequest<StudentResponseDto>(`/students/${id}/placement-status`, {
      method: "PATCH",
      body: { placementStatus },
    }),
  );
}

/**
 * GET /api/students/me/readiness — the signed-in student's read-only readiness summary (overall
 * score + eight-dimension breakdown), derived server-side from their stored derived-score columns.
 * This is the sole authoritative source for readiness; a failure propagates so the caller can show
 * an error/retry state.
 */
export async function getReadiness(): Promise<ReadinessSummary> {
  return apiRequest<ReadinessSummary>("/students/me/readiness");
}

/**
 * POST /api/students/:id/readiness/recompute — officer/admin (or the student themselves) recomputes
 * the student's readiness from their EXISTING data (assessments, academics, projects), writes it back
 * to the displayed readiness_score/breakdown columns, and appends a history snapshot. Campus access is
 * enforced server-side. The recomputed ML result is returned; callers typically just reload the roster
 * to pick up the new score.
 */
export async function recomputeReadiness(studentId: string): Promise<void> {
  await apiRequest<unknown>(`/students/${studentId}/readiness/recompute`, { method: "POST" });
}

/**
 * GET /api/students/me/assessments — the signed-in student's read-only assessment baseline (five
 * 0-100 scores), parsed server-side from their stored assessment JSON. Authoritative and never
 * writable through this path; defaults to all-zero when no assessments are recorded yet.
 */
export async function getAssessments(): Promise<AssessmentScores> {
  return apiRequest<AssessmentScores>("/students/me/assessments");
}

/** One entry in a student's backlog timeline. Mirrors the backend BacklogHistoryEntryResponse. */
export interface BacklogHistoryEntry {
  id: string;
  semester: number | null;
  activeBacklogs: number;
  clearedBacklogs: number | null;
  note: string | null;
  recordedBy: string | null;
  recordedAt: string;
}

/**
 * GET /api/students/me/backlog-history — the signed-in student's backlog timeline (newest first).
 * Each entry records the active-backlog count at a point in time; `students.backlogs` stays the
 * authoritative current count.
 */
export async function getMyBacklogHistory(): Promise<BacklogHistoryEntry[]> {
  return apiRequest<BacklogHistoryEntry[]>("/students/me/backlog-history");
}

/** GET /api/students/:id/backlog-history — a specific student's timeline (staff, campus-scoped). */
export async function getStudentBacklogHistory(studentId: string): Promise<BacklogHistoryEntry[]> {
  return apiRequest<BacklogHistoryEntry[]>(`/students/${studentId}/backlog-history`);
}

// ============================================================
// DIRECTORY (V60) — the officer-managed campus student directory.
// Records are created by the officer (manually or CSV import), live in
// the same students table with source=DIRECTORY, and wait unclaimed until
// the student registers with the matching email. All four entry points are
// officer/admin-only server-side; a student/mentor/recruiter gets 403.
// ============================================================

/**
 * Fields for POST /api/students (officer adds one directory record). Mirrors the backend
 * StudentDirectoryCreateRequest; only name/email/campusId are required server-side.
 */
export interface StudentDirectoryCreateInput {
  name: string;
  email: string;
  rollNumber?: string;
  campusId: string;
  branch?: string;
  branchCode?: string;
  department?: string;
  batch?: string;
  semester?: number;
  graduationYear?: number;
  yearLabel?: string;
  cgpa?: number;
  tenthPercentage?: number;
  twelfthPercentage?: number;
  backlogs?: number;
  phone?: string;
}

/**
 * Fields for PUT /api/students/:id/directory (officer edits directory-owned fields). Every
 * field is optional: omit one to leave it unchanged. The email is immutable once the record
 * is claimed (a rename attempt is a 409).
 */
export interface StudentDirectoryUpdateInput {
  name?: string;
  email?: string;
  rollNumber?: string;
  branch?: string;
  branchCode?: string;
  department?: string;
  batch?: string;
  semester?: number;
  graduationYear?: number;
  yearLabel?: string;
  cgpa?: number;
  tenthPercentage?: number;
  twelfthPercentage?: number;
  backlogs?: number;
  phone?: string;
}

/** POST /api/students/import result — per-row outcomes, never an all-or-nothing abort. */
export interface CsvImportResult {
  imported: number;
  skipped: number;
  errors: { row: number; reason: string }[];
}

/** POST /api/students — officer/admin adds one DIRECTORY record (no account yet). 201. */
export async function createStudent(input: StudentDirectoryCreateInput): Promise<Student> {
  return mapStudent(
    await apiRequest<StudentResponseDto>("/students", { method: "POST", body: input }),
  );
}

/** PUT /api/students/:id/directory — officer/admin edits the directory-owned fields. */
export async function updateStudentDirectory(
  id: string,
  input: StudentDirectoryUpdateInput,
): Promise<Student> {
  return mapStudent(
    await apiRequest<StudentResponseDto>(`/students/${id}/directory`, {
      method: "PUT",
      body: input,
    }),
  );
}

/**
 * PATCH /api/students/:id/active — soft deactivate/reactivate. Deactivation also disables the
 * linked account (login → 401); reactivation restores both.
 */
export async function setStudentActive(id: string, active: boolean): Promise<Student> {
  return mapStudent(
    await apiRequest<StudentResponseDto>(`/students/${id}/active`, {
      method: "PATCH",
      body: { active },
    }),
  );
}

/**
 * POST /api/students/import — bulk directory import from an officer-uploaded CSV. The campusId
 * rides as a form field alongside the file part (Spring's @RequestParam accepts both). Bad or
 * duplicate rows are skipped with a reason, never aborting the rest.
 */
export async function importStudentsCsv(file: File, campusId: string): Promise<CsvImportResult> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("campusId", campusId);
  return apiUpload<CsvImportResult>("/students/import", formData);
}

// ============================================================
// SKILLS — the signed-in student's current skill set with proficiency
// (GET/PUT/DELETE /api/students/me/skills). This is the editable record
// skill-gap analysis and semantic matching read as present ability.
// ============================================================

/** One skill the student claims, with a self-rated proficiency 0-100. Mirrors StudentSkillResponse. */
export interface StudentSkill {
  id: string;
  skill: string;
  proficiency: number;
  source: string;
  /** Verification level: SELF_DECLARED -> EVIDENCED (proof link) -> VERIFIED (passed a skill viva). */
  verificationStatus: string;
  evidenceUrl: string | null;
  evidenceNote: string | null;
  updatedAt: string;
}

/** Body for adding/updating a skill. Upsert is keyed on `skill` (case-insensitive). */
export interface SkillUpsertInput {
  skill: string;
  proficiency: number;
  source?: string;
}

/** GET /api/students/me/skills */
export async function listSkills(): Promise<StudentSkill[]> {
  return apiRequest<StudentSkill[]>("/students/me/skills");
}

/**
 * GET /api/students/:studentId/skills — a specific student's recorded skills, for placement staff
 * (officer/admin/mentor within campus reach). Powers the officer Student Directory drawer so the
 * Skills section reflects the student's actual skill records, not just resume-derived strengths.
 */
export async function listSkillsForStudent(studentId: string): Promise<StudentSkill[]> {
  return apiRequest<StudentSkill[]>(`/students/${studentId}/skills`);
}

/** PUT /api/students/me/skills — adds a skill or updates an existing one (by skill label). */
export async function upsertSkill(input: SkillUpsertInput): Promise<StudentSkill> {
  return apiRequest<StudentSkill>("/students/me/skills", { method: "PUT", body: input });
}

/** DELETE /api/students/me/skills/:id */
export async function deleteSkill(skillId: string): Promise<void> {
  await apiRequest<void>(`/students/me/skills/${skillId}`, { method: "DELETE" });
}

/**
 * PUT /api/students/me/skills/:id/level — nudges a VERIFIED skill's level without re-testing.
 * Only small corrections (±15 points) are accepted; anything larger is refused and needs a fresh
 * skill viva so the verified badge keeps its meaning.
 */
export async function adjustSkillLevel(skillId: string, level: number): Promise<StudentSkill> {
  return apiRequest<StudentSkill>(`/students/me/skills/${skillId}/level`, {
    method: "PUT",
    body: { level },
  });
}

/**
 * PUT /api/students/me/skills/:id/evidence — attaches an https proof link (project/repo/certificate)
 * to a skill and promotes it to EVIDENCED (unless already VERIFIED). Returns the updated skill.
 */
export async function attachSkillEvidence(
  skillId: string,
  url: string,
  note?: string,
): Promise<StudentSkill> {
  return apiRequest<StudentSkill>(`/students/me/skills/${skillId}/evidence`, {
    method: "PUT",
    body: { url, note: note ?? null },
  });
}

// ============================================================
// GOALS — the signed-in student's applied growth roadmap
// (GET/PUT /api/students/me/goals). Persists the What-If scenario a
// student commits to via "Apply This Roadmap" so it survives refresh/login.
// Self-reported intent only — never feeds readiness/skill-gap/digital-twin.
// ============================================================

/** The student's currently applied roadmap. Mirrors StudentGoalResponse. */
export interface StudentGoal {
  id: string;
  /** Selected What-If factor keys, e.g. ["aws", "dsa"]. */
  factors: string[];
  targetRole: string | null;
  baselineProbability: number | null;
  projectedProbability: number | null;
  readinessImpact: number | null;
  recommendedPath: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

/** Body for applying/re-applying the roadmap. The scenario is computed server-side via What-If. */
export interface ApplyRoadmapInput {
  factors: string[];
  projectedProbability?: number;
  baselineProbability?: number;
  readinessImpact?: number;
  recommendedPath?: string;
}

/** GET /api/students/me/goals — the student's applied roadmap, or null if none applied yet. */
export async function getMyGoal(): Promise<StudentGoal | null> {
  // 204 (no goal yet) resolves to undefined via apiRequest.
  return (await apiRequest<StudentGoal | undefined>("/students/me/goals")) ?? null;
}

/** PUT /api/students/me/goals — applies (or re-applies) the roadmap; returns the saved goal. */
export async function applyRoadmap(input: ApplyRoadmapInput): Promise<StudentGoal> {
  return apiRequest<StudentGoal>("/students/me/goals", { method: "PUT", body: input });
}

/**
 * GET /api/students/me/digital-twin — the signed-in student's read-only digital-twin summary
 * (headline readiness score + deterministic status label), derived server-side from their stored
 * readiness score. Authoritative; never recomputed client-side.
 */
export async function refreshDigitalTwin(): Promise<DigitalTwinResult> {
  return apiRequest<DigitalTwinResult>("/students/me/digital-twin");
}

/** The authoritative What-If projection: baseline is the student's own stored probability. */
export interface WhatIfResult {
  probability: number;
  readinessImpact: number;
  recommendedPath: string;
  baselineProbability: number;
}

export interface WhatIfFactorItem {
  id: string;
  key: string;
  label: string;
  category: string;
  impactWeight: number;
  detail: string;
  isCurrentGap: boolean;
}

/**
 * GET /api/students/me/what-if/factors — returns dynamic simulation factors derived from the
 * student's own Career Roadmap, high-impact drive requirements, and practice levers.
 */
export async function getWhatIfFactors(): Promise<WhatIfFactorItem[]> {
  return apiRequest<WhatIfFactorItem[]>("/students/me/what-if/factors");
}

/**
 * POST /api/students/me/what-if — the authoritative, server-computed What-If projection for the
 * signed-in student. The baseline is the student's own stored placement probability (returned as
 * {@code baselineProbability}); the projection is deterministic and never client-invented.
 */
export async function runWhatIfScenario(selectedFactors: string[]): Promise<WhatIfResult> {
  return apiRequest<WhatIfResult>("/students/me/what-if", {
    method: "POST",
    body: { factors: selectedFactors },
  });
}

export { whatIfFactorLabels };
export type { WhatIfFactor };

/**
 * GET /api/students/me/resume-intelligence — the signed-in student's read-only resume summary
 * (score + strengths/weaknesses/missing-keywords/suggestions/recommendation), derived server-side
 * from their stored resume score, strengths and critical gaps.
 */
export async function getResumeIntelligence(): Promise<ResumeIntelligence> {
  return apiRequest<ResumeIntelligence>("/students/me/resume-intelligence");
}

/**
 * The rich, structured per-resume analysis returned by POST /me/resume-intelligence/analyze.
 * Every field is grounded server-side: `overallScore` and `missingKeywords` are authoritative;
 * each metric carries the `basis` it was derived from (a labeled heuristic, never an unexplained
 * number); prose fields never invent facts. Mirrors the backend ResumeAnalysisResponse.
 */
export interface ResumeMetric {
  key: string;
  label: string;
  value: number;
  basis: string;
}

export interface ResumeWeakness {
  gap: string;
  evidence: string;
  whyItMatters: string;
  severity: "HIGH" | "MEDIUM" | "LOW";
  recommendedAction: string;
}

export interface ResumeProjectSuggestion {
  name: string;
  description: string;
  addressesGap: string;
  skillsDemonstrated: string[];
  difficulty: string;
  outcome: string;
}

export interface ResumeSkillMatch {
  requiredSkill: string;
  resumeEvidence: string;
  status: "DEMONSTRATED" | "PARTIAL" | "NOT_DEMONSTRATED";
}

export interface ResumeExtractedCategory {
  category: string;
  items: { value: string; evidence: string }[];
}

export interface ResumeActionPlan {
  now: string[];
  next: string[];
  later: string[];
}

/**
 * §10 Resume Quality Analysis — a grounded, dimension-by-dimension diagnosis of the uploaded resume
 * (NOT another score). Mirrors the backend ResumeQualityAnalysis nested inside ResumeAnalysis. Every
 * finding cites the resume as evidence or states plainly what is absent; a skill that is not shown is
 * reported as "not demonstrated in the resume", never as the student not knowing it.
 */
export type ResumeQualitySeverity = "INFO" | "LOW" | "MEDIUM" | "HIGH";
export type ResumeQualityStatus = "GOOD" | "NEEDS_ATTENTION" | "NOT_ENOUGH_EVIDENCE";

export interface ResumeQualityFinding {
  category: string;
  issue: string;
  evidence: string;
  recommendation: string;
  severity: ResumeQualitySeverity;
}

export interface ResumeQualityDimension {
  status: ResumeQualityStatus;
  findings: ResumeQualityFinding[];
}

export interface ResumeQualityAnalysis {
  structure: ResumeQualityDimension;
  contentQuality: ResumeQualityDimension;
  projectQuality: ResumeQualityDimension;
  experienceQuality: ResumeQualityDimension;
  quantification: ResumeQualityDimension;
  skillsEvidence: ResumeQualityDimension;
  targetRoleRelevance: ResumeQualityDimension;
  readability: ResumeQualityDimension;
  topImprovements: ResumeQualityFinding[];
}

export interface ResumeAnalysis {
  overallScore: number;
  metrics: ResumeMetric[];
  strengths: string[];
  weaknesses: ResumeWeakness[];
  missingKeywords: string[];
  projectSuggestions: ResumeProjectSuggestion[];
  skillMatch: ResumeSkillMatch[];
  extracted: ResumeExtractedCategory[];
  recommendation: string;
  actionPlan: ResumeActionPlan;
  // §10 — nested quality diagnosis. Null for older stored analyses created before this field existed.
  resumeQuality?: ResumeQualityAnalysis | null;
}

/**
 * POST /api/students/me/resume-intelligence/analyze (multipart) — analyzes an uploaded resume file
 * and returns the structured, grounded analysis. The score, metrics and missing keywords are
 * computed deterministically server-side; the prose is derived from the resume's actual content
 * (enriched by the grounded LLM when enabled). The analysis result (never the raw file) is stored
 * as one history entry for the signed-in student.
 */
export async function analyzeResumeFile(file: File): Promise<ResumeAnalysis> {
  const formData = new FormData();
  formData.append("file", file);
  return apiUpload<ResumeAnalysis>("/students/me/resume-intelligence/analyze", formData);
}

/**
 * One entry in the signed-in student's resume Analysis History. Mirrors the backend
 * ResumeAnalysisSummary — enough to list a past run and plot score-over-time; the full analysis is
 * fetched on demand via {@link getResumeAnalysisDetail}.
 */
export interface ResumeAnalysisSummary {
  id: string;
  resumeName: string | null;
  overallScore: number;
  createdAt: string;
}

/** A restored past analysis: the run's metadata plus the exact structured analysis it produced. */
export interface ResumeAnalysisDetail {
  id: string;
  resumeName: string | null;
  createdAt: string;
  analysis: ResumeAnalysis;
}

/**
 * GET /api/students/me/resume-analysis/history — the signed-in student's prior analyze runs, newest
 * first. Server-derived from stored runs only; no runs yet resolves to an empty list, never a
 * fabricated history.
 */
export async function listResumeAnalysisHistory(): Promise<ResumeAnalysisSummary[]> {
  return apiRequest<ResumeAnalysisSummary[]>("/students/me/resume-analysis/history");
}

/**
 * GET /api/students/me/resume-analysis/history/:id — one past analysis, restored in full so the UI
 * can re-render every section without recomputing. Scoped to the owner server-side.
 */
export async function getResumeAnalysisDetail(id: string): Promise<ResumeAnalysisDetail> {
  return apiRequest<ResumeAnalysisDetail>(`/students/me/resume-analysis/history/${id}`);
}

// ============================================================
// CAREER ROADMAP — the signed-in student's gap-driven preparation plan
// (GET /me/roadmap, PUT /me/roadmap/tasks/:id, POST /me/roadmap/projects).
// Structure comes from a curated server-side catalog chosen by target role;
// tasks whose skill is already demonstrated start DONE; progress is computed
// from real task status. Nothing here is fabricated — an empty/unknown role
// still yields a grounded plan, never invented percentages.
// ============================================================

/** One roadmap task. `kind` is TASK (from the catalog) or PROJECT (student-added); status PLANNED/DONE. */
export interface RoadmapTask {
  id: string;
  title: string;
  detail: string | null;
  skill: string | null;
  kind: "TASK" | "PROJECT";
  source: string;
  status: "PLANNED" | "DONE";
  addressesGap: string | null;
}

/** A roadmap phase with its computed status and progress. */
export interface RoadmapPhase {
  key: string;
  title: string;
  rationale: string | null;
  status: "DONE" | "CURRENT" | "UPCOMING";
  progress: number;
  tasks: RoadmapTask[];
}

/** The phase the student should focus on now (the first not-yet-complete phase), or null if all done. */
export interface RoadmapCurrentFocus {
  phaseKey: string;
  title: string;
  rationale: string | null;
  doneTasks: number;
  totalTasks: number;
}

/** A project the student could add, grounded in a real pending gap; `alreadyAdded` disables the button. */
export interface RoadmapRecommendedProject {
  id: string;
  name: string;
  description: string;
  addressesGap: string;
  skillsDemonstrated: string[];
  difficulty: string;
  alreadyAdded: boolean;
}

/** The assembled roadmap. Progress/counts are computed server-side from real task status. */
export interface Roadmap {
  targetRole: string | null;
  overallProgress: number;
  phaseCount: number;
  taskCount: number;
  completedTaskCount: number;
  projectCount: number;
  currentFocus: RoadmapCurrentFocus | null;
  phases: RoadmapPhase[];
  recommendedProjects: RoadmapRecommendedProject[];
}

/** Body for adding a portfolio project to the roadmap (idempotent by title, server-side). */
export interface RoadmapProjectInput {
  name: string;
  description?: string;
  addressesGap?: string;
  skillsDemonstrated?: string[];
  difficulty?: string;
}

/**
 * GET /api/students/me/roadmap — the signed-in student's personalized roadmap, materialized from the
 * curated catalog on first view and seeded against already-demonstrated skills. Server-derived only.
 */
export async function getRoadmap(): Promise<Roadmap> {
  return apiRequest<Roadmap>("/students/me/roadmap");
}

/** PUT /api/students/me/roadmap/tasks/:id — sets one task PLANNED/DONE; returns the recomputed roadmap. */
export async function toggleRoadmapTask(taskId: string, status: "PLANNED" | "DONE"): Promise<Roadmap> {
  return apiRequest<Roadmap>(`/students/me/roadmap/tasks/${taskId}`, { method: "PUT", body: { status } });
}

/** POST /api/students/me/roadmap/projects — adds a portfolio project (idempotent); returns the roadmap. */
export async function addRoadmapProject(input: RoadmapProjectInput): Promise<Roadmap> {
  return apiRequest<Roadmap>("/students/me/roadmap/projects", { method: "POST", body: input });
}

/**
 * POST /api/students/me/roadmap/regenerate — rebuilds the roadmap from the current catalog and the
 * student's latest demonstrated skills, preserving completed tasks and portfolio projects; returns
 * the recomputed roadmap. Flips gaps to done when a newly-recorded skill now covers them.
 */
export async function regenerateRoadmap(): Promise<Roadmap> {
  return apiRequest<Roadmap>("/students/me/roadmap/regenerate", { method: "POST" });
}

/** A single curated learning resource for a gap. URLs come from a version-controlled catalog only. */
export interface LearningResource {
  title: string;
  provider: string;
  url: string;
  type: string;
  difficulty: string;
  description: string;
  estimatedEffort: string | null;
}

/** The roadmap task/phase a recommendation is tied to, so the UI can link gap → milestone. */
export interface LearningRelatedMilestone {
  phaseKey: string;
  phaseTitle: string;
  taskId: string;
  taskTitle: string;
}

/** A project the student can add to the roadmap to close the gap (posted via addRoadmapProject). */
export interface LearningProjectSuggestion {
  name: string;
  description: string;
  addressesGap: string;
  skillsDemonstrated: string[];
  difficulty: string;
}

/**
 * A real YouTube tutorial surfaced for the gap (never fabricated). Empty when no YouTube key is
 * configured server-side or none were found — kept separate from the curated `resources`.
 */
export interface TutorialVideo {
  title: string;
  channel: string;
  url: string;
}

/**
 * One Learning Recommendation for a still-pending roadmap gap. `resources` is empty and
 * `hasCuratedResource` false when nothing curated exists — the UI says so rather than inventing one.
 * `tutorialVideos` is optional real YouTube enrichment, empty by default.
 */
export interface LearningRecommendation {
  skill: string;
  gap: string;
  whyLearn: string;
  learningSequence: string[];
  resources: LearningResource[];
  practiceTask: string;
  relatedMilestone: LearningRelatedMilestone;
  addToRoadmap: LearningProjectSuggestion | null;
  alreadyAdded: boolean;
  hasCuratedResource: boolean;
  tutorialVideos: TutorialVideo[];
}

/**
 * GET /api/students/me/learning-recommendations — one recommendation per still-pending skill gap on
 * the student's Career Roadmap, each with curated resources and its related roadmap milestone.
 */
export async function getLearningRecommendations(): Promise<LearningRecommendation[]> {
  return apiRequest<LearningRecommendation[]>("/students/me/learning-recommendations");
}

/** One server-ranked open drive recommended for the signed-in student. Mirrors CareerRecommendationResponse. */
export interface CareerRecommendation {
  driveId: string;
  companyName: string;
  role: string;
  location: string;
  packageLpa: number | null;
  /** Deterministic 0-100 fit score (skills + eligibility + role alignment). */
  matchScore: number;
  eligible: boolean;
  matchedSkills: string[];
  missingSkills: string[];
  reasons: string[];
}

/**
 * GET /api/students/me/career-recommendations — the signed-in student's own campus's open drives,
 * ranked deterministically by skill/eligibility/role fit. Server-derived from real data only; an
 * empty campus (no open drives) resolves to an empty array, never fabricated matches.
 */
export async function getCareerRecommendations(): Promise<CareerRecommendation[]> {
  return apiRequest<CareerRecommendation[]>("/students/me/career-recommendations");
}
