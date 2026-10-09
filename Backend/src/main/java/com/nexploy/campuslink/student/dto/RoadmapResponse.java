package com.nexploy.campuslink.student.dto;

import java.util.List;

/**
 * The assembled Career Roadmap returned by {@code GET /students/me/roadmap} (and after each mutation).
 * The durable structure (phases and tasks with status) is persisted; everything derived here —
 * {@code overallProgress}, the counts, each phase's {@code status}/{@code progress}, the
 * {@code currentFocus} and the {@code recommendedProjects} — is COMPUTED from that structure and the
 * curated catalog, never a fabricated figure.
 *
 * <p>Grounding: a task is DONE only because its skill was already demonstrated or the student marked
 * it complete; a phase is DONE only when all its tasks are; progress is {@code completed/total}. The
 * roadmap describes a recommended path, never a guaranteed outcome.
 */
public record RoadmapResponse(
    String targetRole,
    Integer overallProgress,
    Integer phaseCount,
    Integer taskCount,
    Integer completedTaskCount,
    Integer projectCount,
    CurrentFocus currentFocus,
    List<Phase> phases,
    List<RecommendedProject> recommendedProjects) {

    /** The first phase with unfinished work — where the student should focus next. Null when all done. */
    public record CurrentFocus(
        String phaseKey, String title, String rationale, Integer doneTasks, Integer totalTasks) {}

    /** One roadmap phase. {@code status} is DONE/CURRENT/UPCOMING; {@code progress} is 0-100. */
    public record Phase(
        String key,
        String title,
        String rationale,
        String status,
        Integer progress,
        List<Task> tasks) {}

    /**
     * One roadmap task. {@code kind} is TASK (a catalog learning step) or PROJECT (a student-added
     * portfolio project); {@code source} records how it got here (CATALOG / RESUME_SUGGESTION /
     * STUDENT); {@code status} is PLANNED or DONE; {@code skill}/{@code addressesGap} name the gap it
     * closes.
     */
    public record Task(
        String id,
        String title,
        String detail,
        String skill,
        String kind,
        String source,
        String status,
        String addressesGap) {}

    /**
     * A project the student could add to close a still-pending gap. {@code alreadyAdded} is true when a
     * PROJECT task of the same title is already on the roadmap, so the UI can disable the button.
     */
    public record RecommendedProject(
        String id,
        String name,
        String description,
        String addressesGap,
        List<String> skillsDemonstrated,
        String difficulty,
        Boolean alreadyAdded) {}
}
