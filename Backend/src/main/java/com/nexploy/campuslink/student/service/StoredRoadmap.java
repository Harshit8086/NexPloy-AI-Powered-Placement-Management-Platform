package com.nexploy.campuslink.student.service;

import java.util.List;

/**
 * The durable, persisted shape of a student's roadmap — exactly what is serialized into
 * {@code career_roadmap.roadmap_json}. It holds only structure and per-task status; all derived
 * figures (progress, phase status, recommended projects) are computed by {@link RoadmapService} at
 * read time and are never stored. Kept separate from the API {@code RoadmapResponse} so the wire
 * shape and the storage shape can evolve independently.
 */
public record StoredRoadmap(String targetRole, List<StoredPhase> phases) {

    public record StoredPhase(String key, String title, String rationale, List<StoredTask> tasks) {}

    /**
     * One persisted task. {@code status} is PLANNED or DONE; {@code kind} is TASK or PROJECT;
     * {@code source} is CATALOG, RESUME_SUGGESTION or STUDENT.
     */
    public record StoredTask(
        String id,
        String title,
        String detail,
        String skill,
        String kind,
        String source,
        String status,
        String addressesGap) {}
}
