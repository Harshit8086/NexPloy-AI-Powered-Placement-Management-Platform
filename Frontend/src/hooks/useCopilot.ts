"use client";

import { useCallback, useState } from "react";
import { useAppStore } from "@/hooks/useAppStore";
import { askCopilot } from "@/lib/api/copilot";
import type { Role, CopilotMessage } from "@/types";

let msgCounter = 0;
const nextId = () => `msg_${++msgCounter}`;

/**
 * Initial empty-state prompts shown before the first question. These are UI copy (no business data);
 * once the server answers, its own {@code suggestions} — grounded in what it can actually answer for
 * the signed-in user — replace them. Answers themselves always come from POST /api/copilot/ask; there
 * is no client-side simulation.
 */
const DEFAULT_SUGGESTIONS: Record<Role, string[]> = {
  student: [
    "Why is my readiness score what it is?",
    "How can I improve my placement probability?",
    "What skills am I missing?",
    "What are my best-matched drives?",
    "How can I improve my resume?",
  ],
  recruiter: ["What can you help me with?", "How is placement data organised here?"],
  officer: ["What can you help me with?", "How is placement data organised here?"],
  mentor: ["What can you help me with?", "How is placement data organised here?"],
  admin: ["What can you help me with?", "How is placement data organised here?"],
};

export function useCopilot(roleContext: Role) {
  const copilotOpen = useAppStore((s) => s.copilotOpen);
  const setCopilotOpen = useAppStore((s) => s.setCopilotOpen);
  const toggleCopilot = useAppStore((s) => s.toggleCopilot);

  const [messages, setMessages] = useState<CopilotMessage[]>([]);
  const [thinking, setThinking] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>(DEFAULT_SUGGESTIONS[roleContext]);

  const ask = useCallback(async (question: string) => {
    if (!question.trim()) return;
    setMessages((m) => [...m, { id: nextId(), role: "user", text: question }]);
    setThinking(true);
    try {
      const result = await askCopilot(question);
      setMessages((m) => [...m, { id: nextId(), role: "assistant", text: result.answer }]);
      if (result.suggestions.length > 0) setSuggestions(result.suggestions);
    } catch {
      // No fake fallback — surface an honest error and let the user retry.
      setMessages((m) => [
        ...m,
        {
          id: nextId(),
          role: "assistant",
          text: "I couldn't reach the assistant just now. Please try again in a moment.",
        },
      ]);
    } finally {
      setThinking(false);
    }
  }, []);

  const reset = useCallback(() => setMessages([]), []);

  return { copilotOpen, setCopilotOpen, toggleCopilot, messages, thinking, suggestions, ask, reset };
}
