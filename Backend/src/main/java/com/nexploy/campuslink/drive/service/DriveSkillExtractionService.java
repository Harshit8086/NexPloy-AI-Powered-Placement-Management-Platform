package com.nexploy.campuslink.drive.service;

import com.nexploy.campuslink.drive.dto.ExtractedSkillItem;
import java.util.List;

/**
 * Service that extracts required skills, recommended proficiency levels (0-100), importance weights (1-10),
 * and mandatory/preferred flags from a drive's job role and description.
 */
public interface DriveSkillExtractionService {
    List<ExtractedSkillItem> extractSkills(String driveId, String jobRole, String jobDescription, List<String> existingSkillNames);
}
