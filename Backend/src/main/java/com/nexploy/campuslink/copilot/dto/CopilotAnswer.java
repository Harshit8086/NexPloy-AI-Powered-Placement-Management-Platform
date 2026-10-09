package com.nexploy.campuslink.copilot.dto;

import java.util.List;

/**
 * The Copilot's grounded answer to a question. {@code answer} is always populated (a deterministic,
 * data-grounded response); {@code suggestions} are role-appropriate follow-up questions for the UI's
 * empty state; {@code mode} is {@code "rule-based"} or {@code "llm"} so the client can surface how the
 * text was produced. Every number in the answer comes from the caller's own stored data — never
 * fabricated, and never from the LLM.
 */
public record CopilotAnswer(
    String answer,
    List<String> suggestions,
    String mode
) {
}
