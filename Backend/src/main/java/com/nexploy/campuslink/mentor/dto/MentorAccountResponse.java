package com.nexploy.campuslink.mentor.dto;

public record MentorAccountResponse(
    String username,
    String campusId,
    int activeAssignmentsCount
) {}
