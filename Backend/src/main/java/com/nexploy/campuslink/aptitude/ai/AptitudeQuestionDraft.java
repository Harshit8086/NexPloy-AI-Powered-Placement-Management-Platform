package com.nexploy.campuslink.aptitude.ai;

/** One AI-generated practice question before it is persisted to the question bank. */
public record AptitudeQuestionDraft(
    String prompt,
    String optionA,
    String optionB,
    String optionC,
    String optionD,
    String correctOption,
    String explanation
) {
}
