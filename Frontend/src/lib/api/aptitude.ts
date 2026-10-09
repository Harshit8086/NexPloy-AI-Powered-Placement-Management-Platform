/**
 * Student-facing aptitude test API — the two-lane module where OFFICIAL bank-tests feed
 * placement readiness and AI-generated PRACTICE never does. Mirrors the backend DTOs in
 * com.nexploy.campuslink.aptitude.dto verbatim.
 */
import { apiRequest } from "@/lib/api/client";

// ─── Response types ───────────────────────────────────────────────────────────

export interface AptitudeTestResponse {
  testId: string;
  title: string;
  category: string;
  questionCount: number;
  durationSeconds: number;
  description: string;
  available: boolean;
  unavailableReason: string | null;
  lastScore: number | null;
}

export interface AttemptQuestionResponse {
  attemptQuestionId: string;
  ordinal: number;
  category: string;
  prompt: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  selectedOption: string | null;
  marked: boolean;
  /** null until the attempt is COMPLETED */
  correct: boolean | null;
  /** null until the attempt is COMPLETED */
  correctOption: string | null;
  /** null until the attempt is COMPLETED */
  explanation: string | null;
}

export interface SectionResult {
  category: string;
  total: number;
  correct: number;
  percent: number;
}

export interface AttemptResponse {
  attemptId: string;
  testId: string;
  testTitle: string;
  mode: "OFFICIAL" | "PRACTICE";
  category: string | null;
  status: "IN_PROGRESS" | "COMPLETED";
  totalQuestions: number;
  durationLimitSeconds: number;
  timeSpentSeconds: number | null;
  score: number | null;
  correctCount: number | null;
  aptitudeBefore: number | null;
  startedAt: string;
  submittedAt: string | null;
  questions: AttemptQuestionResponse[];
  sections: SectionResult[];
}

export interface AttemptSummaryResponse {
  attemptId: string;
  testId: string;
  testTitle: string;
  mode: "OFFICIAL" | "PRACTICE";
  category: string | null;
  status: "IN_PROGRESS" | "COMPLETED";
  totalQuestions: number;
  correctCount: number | null;
  score: number | null;
  startedAt: string;
  submittedAt: string | null;
}

export interface AptitudeAnalysisResponse {
  attemptId: string;
  testTitle: string;
  score: number;
  summary: string;
  strengths: string[];
  weaknesses: string[];
  tips: string[];
  aptitudeBefore: number;
  aptitudeAfter: number;
}

export interface StudyPlanResource {
  title: string;
  provider: string;
  url: string;
  type: string;
}

export interface StudyPlanRecommendation {
  category: string;
  percent: number;
  priority: "HIGH" | "MEDIUM";
  topics: string[];
  resources: StudyPlanResource[];
  practiceTask: string;
}

export interface StudyPlanResponse {
  attemptId: string;
  recommendations: StudyPlanRecommendation[];
}

// ─── API functions ────────────────────────────────────────────────────────────

/** Catalog of 5 fixed tests with bank-driven availability and the student's last score. */
export function listAptitudeTests(): Promise<AptitudeTestResponse[]> {
  return apiRequest<AptitudeTestResponse[]>("/students/me/aptitude/tests");
}

/** Completed and in-progress attempts, most recent first. */
export function getAptitudeHistory(): Promise<AttemptSummaryResponse[]> {
  return apiRequest<AttemptSummaryResponse[]>("/students/me/aptitude/history");
}

/** Start (or resume) an OFFICIAL attempt for the given catalog test. */
export function startAttempt(testId: string): Promise<AttemptResponse> {
  return apiRequest<AttemptResponse>("/students/me/aptitude/attempts", {
    method: "POST",
    body: { testId },
  });
}

/** Fetch a single attempt (must belong to the caller). */
export function getAttempt(attemptId: string): Promise<AttemptResponse> {
  return apiRequest<AttemptResponse>(`/students/me/aptitude/attempts/${attemptId}`);
}

/** Persist an answer or mark-for-review toggle. selectedOption=null clears the answer. */
export function saveAnswer(
  attemptId: string,
  attemptQuestionId: string,
  selectedOption: string | null,
  marked: boolean,
): Promise<AttemptResponse> {
  return apiRequest<AttemptResponse>(`/students/me/aptitude/attempts/${attemptId}/answers`, {
    method: "PUT",
    body: { attemptQuestionId, selectedOption, marked },
  });
}

/** Submit the attempt with client-measured time (server clamps to limit + 60s grace). */
export function submitAttempt(
  attemptId: string,
  timeSpentSeconds: number,
): Promise<AttemptResponse> {
  return apiRequest<AttemptResponse>(`/students/me/aptitude/attempts/${attemptId}/submit`, {
    method: "POST",
    body: { timeSpentSeconds },
  });
}

/** Start an AI-generated PRACTICE attempt (never feeds readiness). */
export function startPractice(category: string, count: number): Promise<AttemptResponse> {
  return apiRequest<AttemptResponse>("/students/me/aptitude/practice", {
    method: "POST",
    body: { category, count },
  });
}

/** AI performance analysis for a COMPLETED attempt. */
export function getAnalysis(attemptId: string): Promise<AptitudeAnalysisResponse> {
  return apiRequest<AptitudeAnalysisResponse>(`/students/me/aptitude/attempts/${attemptId}/analysis`);
}

/** Curated study plan for a COMPLETED attempt's weak sections (deterministic, no AI). */
export function getStudyPlan(attemptId: string): Promise<StudyPlanResponse> {
  return apiRequest<StudyPlanResponse>(`/students/me/aptitude/attempts/${attemptId}/study-plan`);
}

/** Start (or resume) a practice-lane retry of a COMPLETED attempt's missed questions. */
export function startRetry(attemptId: string): Promise<AttemptResponse> {
  return apiRequest<AttemptResponse>(`/students/me/aptitude/attempts/${attemptId}/retry`, {
    method: "POST",
  });
}
