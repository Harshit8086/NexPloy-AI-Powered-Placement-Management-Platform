package com.nexploy.campuslink.student.dto;

/**
 * A dynamic factor presented to the student in the What-If Simulator (Phase 3c).
 * Unlike hardcoded constants, this can represent actual pending tasks from the student's
 * Career Roadmap, high-priority unmet drive skill requirements, or standard practice levers.
 */
public record WhatIfFactorResponse(
    String id,
    String key,
    String label,
    String category,
    Integer impactWeight,
    String detail,
    boolean isCurrentGap) {}
