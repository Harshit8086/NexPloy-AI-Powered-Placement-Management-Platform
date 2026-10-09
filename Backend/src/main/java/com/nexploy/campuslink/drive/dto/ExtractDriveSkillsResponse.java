package com.nexploy.campuslink.drive.dto;

import java.util.List;

public record ExtractDriveSkillsResponse(
    String driveId,
    String jobRole,
    List<ExtractedSkillItem> extractedSkills,
    String source
) {}
