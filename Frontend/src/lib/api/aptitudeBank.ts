/**
 * Officer-facing aptitude question bank API — add, list, and deactivate bank questions.
 * Mirrors the backend DTOs in com.nexploy.campuslink.aptitude.dto verbatim.
 */
import { apiRequest } from "@/lib/api/client";

// ─── Response types ───────────────────────────────────────────────────────────

export interface BankQuestionResponse {
  id: string;
  category: string;
  difficulty: string | null;
  prompt: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption: string;
  explanation: string | null;
  source: "SEEDED" | "OFFICER";
  active: boolean;
  createdAt: string;
}

// ─── Request types ────────────────────────────────────────────────────────────

export interface AddBankQuestionRequest {
  category: string;
  difficulty: string | null;
  prompt: string;
  optionA: string;
  optionB: string;
  optionC: string;
  optionD: string;
  correctOption: string;
  explanation: string | null;
}

// ─── API functions ────────────────────────────────────────────────────────────

/** List bank questions (SEEDED + OFFICER only, never student-owned AI questions). */
export function listBankQuestions(category?: string): Promise<BankQuestionResponse[]> {
  return apiRequest<BankQuestionResponse[]>("/aptitude/bank", {
    params: category ? { category } : undefined,
  });
}

/** Officer adds a new question to the bank. */
export function addBankQuestion(body: AddBankQuestionRequest): Promise<BankQuestionResponse> {
  return apiRequest<BankQuestionResponse>("/aptitude/bank", {
    method: "POST",
    body,
  });
}

/** Soft-activate/deactivate a bank question (deactivated ones leave official draws). */
export function setQuestionActive(
  questionId: string,
  active: boolean,
): Promise<BankQuestionResponse> {
  return apiRequest<BankQuestionResponse>(`/aptitude/bank/${questionId}/active`, {
    method: "PATCH",
    body: { active },
  });
}
