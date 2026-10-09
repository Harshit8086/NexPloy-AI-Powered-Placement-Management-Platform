package com.nexploy.campuslink.skillviva.dto;

import java.util.List;

/**
 * One generated MCQ returned to the client. Options arrive already shuffled so their positions
 * reveal nothing; the correct option stays on the server, which scores each answer by comparing
 * the option text the student picked against it.
 */
public record VivaQuestionResponse(int ordinal, String prompt, List<String> options, int timerSeconds) {
}