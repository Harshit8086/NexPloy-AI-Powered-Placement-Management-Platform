package com.nexploy.campuslink.aptitude.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of POST /api/aptitude/bank — officer adds a curated question to their campus's bank. */
public record AddBankQuestionRequest(
    @NotBlank String category,
    String difficulty,
    @NotBlank String prompt,
    @NotBlank String optionA,
    @NotBlank String optionB,
    @NotBlank String optionC,
    @NotBlank String optionD,
    @NotBlank @Pattern(regexp = "[A-D]") String correctOption,
    String explanation
) {
}
