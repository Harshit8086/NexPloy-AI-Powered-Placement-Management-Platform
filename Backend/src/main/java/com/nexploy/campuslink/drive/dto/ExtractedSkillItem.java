package com.nexploy.campuslink.drive.dto;

public record ExtractedSkillItem(
    String skill,
    int requiredLevel,
    int weight,
    boolean mandatory,
    int confidence,
    String justification
) {}
