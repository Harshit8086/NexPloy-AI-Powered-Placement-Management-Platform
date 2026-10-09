package com.nexploy.campuslink.student.dto;

/**
 * The authoritative, server-computed result of a What-If scenario for the signed-in student.
 * {@code baselineProbability} is the student's stored placement probability (the starting point);
 * {@code probability} is the deterministic projection for the selected actions; {@code readinessImpact}
 * is the projected readiness change; {@code recommendedPath} is deterministic coaching guidance.
 */
public record WhatIfResponse(
    Integer probability,
    Integer readinessImpact,
    String recommendedPath,
    Integer baselineProbability) {}
