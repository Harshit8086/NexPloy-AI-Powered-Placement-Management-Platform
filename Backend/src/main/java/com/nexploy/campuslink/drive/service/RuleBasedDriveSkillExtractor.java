package com.nexploy.campuslink.drive.service;

import com.nexploy.campuslink.drive.dto.ExtractedSkillItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Deterministic, offline fallback implementation of {@link DriveSkillExtractionService}.
 * Extracts technical skills from existing skill names and text patterns in the role and description.
 */
@Service
@ConditionalOnProperty(name = "nexploy.ai.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedDriveSkillExtractor implements DriveSkillExtractionService {

    private static final List<String> COMMON_TECH_CATALOG = List.of(
        "Java", "Spring Boot", "React", "Node.js", "Python", "JavaScript", "TypeScript",
        "SQL", "PostgreSQL", "MongoDB", "Docker", "Kubernetes", "AWS", "Git",
        "Data Structures", "Algorithms", "System Design", "REST APIs", "Microservices",
        "HTML", "CSS", "C++", "C#", "Linux", "Machine Learning", "FastAPI"
    );

    @Override
    public List<ExtractedSkillItem> extractSkills(
        String driveId,
        String jobRole,
        String jobDescription,
        List<String> existingSkillNames
    ) {
        Set<String> discovered = new LinkedHashSet<>();

        // 1. Include already known skill names from drive (if any)
        if (existingSkillNames != null) {
            for (String s : existingSkillNames) {
                if (s != null && !s.isBlank()) {
                    discovered.add(s.trim());
                }
            }
        }

        // 2. Scan combined text for common tech keywords
        String combined = ((jobRole != null ? jobRole : "") + " " + (jobDescription != null ? jobDescription : "")).toLowerCase(Locale.ROOT);
        for (String tech : COMMON_TECH_CATALOG) {
            if (combined.contains(tech.toLowerCase(Locale.ROOT))) {
                discovered.add(tech);
            }
        }

        // 3. If still empty, fall back to default stack based on role keywords
        if (discovered.isEmpty()) {
            // Checked first: "database engineer" also contains "engineer" (and "data").
            if (combined.contains("database") || combined.contains("dba")) {
                discovered.addAll(List.of("SQL", "PostgreSQL", "Database Design", "Query Optimisation", "Git"));
            } else if (combined.contains("frontend") || combined.contains("ui") || combined.contains("web")) {
                discovered.addAll(List.of("React", "JavaScript", "CSS", "HTML", "REST APIs"));
            } else if (combined.contains("backend") || combined.contains("engineer") || combined.contains("developer")) {
                discovered.addAll(List.of("Java", "Spring Boot", "SQL", "REST APIs", "Git"));
            } else if (combined.contains("data") || combined.contains("ai") || combined.contains("ml")) {
                discovered.addAll(List.of("Python", "SQL", "Machine Learning", "Git"));
            } else {
                discovered.addAll(List.of("Data Structures", "Java", "SQL", "Git"));
            }
        }

        List<ExtractedSkillItem> result = new ArrayList<>();
        int index = 0;
        for (String skill : discovered) {
            // First 2 skills get higher weight and required level
            int level = (index < 2) ? 75 : (index < 4 ? 65 : 55);
            int weight = (index < 2) ? 3 : (index < 4 ? 2 : 1);
            boolean mandatory = index < 3;
            int confidence = 85;
            String justification = "Identified as a core requirement for " + (jobRole != null ? jobRole : "this role");
            result.add(new ExtractedSkillItem(skill, level, weight, mandatory, confidence, justification));
            index++;
        }

        return result;
    }
}
