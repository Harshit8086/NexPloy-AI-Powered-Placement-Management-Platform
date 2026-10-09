package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A What-If scenario request for the signed-in student: the growth actions they are considering.
 * The list may be empty (the baseline scenario). The projected numbers are NOT accepted from the
 * client — the server computes them authoritatively from the student's stored baseline.
 *
 * @param factors selected What-If factor keys (e.g. "aws", "dsa"); empty = baseline scenario
 */
public record WhatIfRequest(
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 64) String> factors) {}
