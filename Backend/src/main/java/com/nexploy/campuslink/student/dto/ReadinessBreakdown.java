package com.nexploy.campuslink.student.dto;

/** The five readiness factors (0-100). Maps 1:1 to the frontend ReadinessBreakdown. */
public record ReadinessBreakdown(
    Integer academic,
    Integer aptitude,
    Integer communication,
    Integer projects,
    Integer interview
) {
}
