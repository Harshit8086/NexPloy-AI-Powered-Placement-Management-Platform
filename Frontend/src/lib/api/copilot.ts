import { apiRequest } from "@/lib/api/client";

/**
 * NEXPLOY Copilot Q&A (POST /api/copilot/ask). The answer is generated server-side and grounded
 * strictly in the authenticated caller's own real data — the backend resolves identity from the auth
 * token, never fabricates numbers, and screens the question for prompt injection. {@code mode} is
 * {@code "rule-based"} (deterministic default) or {@code "llm"} when the optional model rephrased it.
 * Live-only — there is no client-side simulation to fall back to.
 */
export interface CopilotAnswer {
  answer: string;
  suggestions: string[];
  mode: string;
}

export async function askCopilot(question: string): Promise<CopilotAnswer> {
  return apiRequest<CopilotAnswer>("/copilot/ask", { method: "POST", body: { question } });
}
