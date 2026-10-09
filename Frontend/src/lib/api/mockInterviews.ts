// ============================================================
// MOCK INTERVIEWS API — live only. The signed-in student's self-service AI
// practice interviews under /api/students/me/mock-interviews. Questions and
// scoring come from the backend (Spring AI when configured, deterministic
// rule-based fallback otherwise); this module never fabricates content.
// ============================================================

import { apiRequest } from "@/lib/api/client";

/** Interview flavour, mirrors the backend MockInterviewType enum. */
export type MockInterviewType = "TECHNICAL" | "BEHAVIOURAL" | "CODING" | "MIXED";

/** Lifecycle, mirrors MockInterviewStatus. */
export type MockInterviewStatus = "IN_PROGRESS" | "COMPLETED";

/** Per-answer scoring detail (the nine 0-100 metrics + notes). Mirrors AnswerFeedback. */
export interface AnswerFeedback {
  score: number;
  correctness: number;
  clarity: number;
  relevance: number;
  detail: number;
  efficiency: number;
  communication: number;
  problemSolving: number;
  creativity: number;
  feedback: string;
  improvements: string[];
}

/** One question in a mock interview; `score`/`feedback` are null until answered. */
export interface MockInterviewQuestion {
  id: string;
  ordinal: number;
  prompt: string;
  difficulty: string | null;
  timerSeconds: number | null;
  coding: boolean;
  language: string | null;
  studentAnswer: string | null;
  score: number | null;
  feedback: AnswerFeedback | null;
}

/** Full detail of one practice interview (start / get-by-id / complete shape). */
export interface MockInterview {
  id: string;
  type: MockInterviewType;
  role: string | null;
  useResume: boolean;
  status: MockInterviewStatus;
  currentQuestion: number;
  overallScore: number | null;
  strengths: string[];
  weaknesses: string[];
  recommendations: string[];
  summary: string | null;
  createdAt: string;
  updatedAt: string;
  questions: MockInterviewQuestion[];
}

/** History row (list shape). Mirrors MockInterviewSummaryResponse. */
export interface MockInterviewSummary {
  id: string;
  type: MockInterviewType;
  role: string | null;
  status: MockInterviewStatus;
  overallScore: number | null;
  createdAt: string;
}

/** Result of submitting one answer: its score plus the interview's new position. */
export interface SubmitAnswerResponse {
  ordinal: number;
  feedback: AnswerFeedback;
  status: MockInterviewStatus;
  currentQuestion: number;
  totalQuestions: number;
  completed: boolean;
}

/** Body for POST /api/students/me/mock-interviews. */
export interface StartMockInterviewRequest {
  type: MockInterviewType;
  role?: string | null;
  useResume: boolean;
}

/** POST /api/students/me/mock-interviews — starts a run and returns it with generated questions. */
export async function startMockInterview(request: StartMockInterviewRequest): Promise<MockInterview> {
  return apiRequest<MockInterview>("/students/me/mock-interviews", { method: "POST", body: request });
}

/** GET /api/students/me/mock-interviews — the student's practice history, newest first. */
export async function listMockInterviews(): Promise<MockInterviewSummary[]> {
  return apiRequest<MockInterviewSummary[]>("/students/me/mock-interviews");
}

/** GET /api/students/me/mock-interviews/:id — one run in full (owner only; 404 otherwise). */
export async function getMockInterview(id: string): Promise<MockInterview> {
  return apiRequest<MockInterview>(`/students/me/mock-interviews/${id}`);
}

/** POST /api/students/me/mock-interviews/:id/answers — scores the current answer and advances. */
export async function submitMockInterviewAnswer(
  id: string,
  answer: string,
): Promise<SubmitAnswerResponse> {
  return apiRequest<SubmitAnswerResponse>(`/students/me/mock-interviews/${id}/answers`, {
    method: "POST",
    body: { answer },
  });
}

/** POST /api/students/me/mock-interviews/:id/complete — finalises and returns the full report. */
export async function completeMockInterview(id: string): Promise<MockInterview> {
  return apiRequest<MockInterview>(`/students/me/mock-interviews/${id}/complete`, { method: "POST" });
}
