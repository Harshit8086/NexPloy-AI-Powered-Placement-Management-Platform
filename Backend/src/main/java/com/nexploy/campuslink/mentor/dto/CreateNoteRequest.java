package com.nexploy.campuslink.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
    @NotBlank @Size(max = 4000) String note
) {
}
