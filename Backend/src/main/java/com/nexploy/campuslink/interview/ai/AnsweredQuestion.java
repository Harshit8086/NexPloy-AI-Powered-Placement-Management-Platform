package com.nexploy.campuslink.interview.ai;

/**
 * A question/answer/score triple fed into {@link MockInterviewAi#summarize}, so the summariser can
 * reason over the whole session without re-reading persistence entities.
 */
public record AnsweredQuestion(
    String question,
    String answer,
    int score
) {
}
