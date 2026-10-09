// ============================================================
// SKILL VIVA API — a short, skill-scoped AI verification. start() returns AI-generated MCQs for
// one skill (options shuffled server-side) plus a vivaId naming the server's short-lived copy of
// that exact set; submit() sends the vivaId and the picked option texts back so the score is taken
// against the questions actually shown (the LLM adapter never regenerates the same set), and the
// score recalibrates the skill's level, marking it VERIFIED. Mirrors the backend SkillViva DTOs
// under /api/students/me/skills/{skillId}/viva.
// ============================================================

import { apiRequest } from "@/lib/api/client";

/** One generated viva MCQ: the prompt, its 4 shuffled options, and an answer-time budget (seconds). */
export interface VivaQuestion {
  ordinal: number;
  prompt: string;
  options: string[];
  timerSeconds: number;
}

/** Response of POST .../viva/start — the skill under test, its questions, and the vivaId to echo back on submit. */
export interface StartVivaResponse {
  vivaId: string;
  skillId: string;
  skill: string;
  targetLevel: number;
  questions: VivaQuestion[];
}

/** Per-question score in the viva result, with the correct option text disclosed afterwards. */
export interface VivaAnswerFeedback {
  question: string;
  score: number;
  correctOption: string | null;
}

/** Response of POST .../viva/submit — the recalibrated level, what it was before, and the new status. */
export interface SubmitVivaResponse {
  achievedLevel: number;
  claimedLevel: number;
  verificationStatus: string;
  feedback: VivaAnswerFeedback[];
}

/** POST .../viva/start — begins a viva for one owned skill and returns its MCQs. */
export async function startSkillViva(skillId: string): Promise<StartVivaResponse> {
  return apiRequest<StartVivaResponse>(`/students/me/skills/${skillId}/viva/start`, {
    method: "POST",
  });
}

/**
 * POST .../viva/submit — scores the picked options against the vivaId's stored question set
 * (matched by prompt + option text) and recalibrates the skill to the measured level, VERIFIED.
 */
export async function submitSkillViva(
  skillId: string,
  vivaId: string,
  answers: { question: string; option: string }[],
): Promise<SubmitVivaResponse> {
  return apiRequest<SubmitVivaResponse>(`/students/me/skills/${skillId}/viva/submit`, {
    method: "POST",
    body: { vivaId, answers },
  });
}