package com.nexploy.campuslink.interview.ai;

/**
 * One generated practice question, before it is persisted as a {@link
 * com.nexploy.campuslink.interview.entity.MockInterviewQuestion}. The AI (or the offline fallback)
 * produces these; the service assigns ordinals, ids and timestamps.
 */
public record QuestionDraft(
    String question,
    String difficulty,
    Integer timerSeconds,
    boolean coding,
    String language
) {
}
