package com.nexploy.campuslink.student.dto;

import java.util.List;

/**
 * One Learning Recommendation for a still-pending skill gap on the signed-in student's Career
 * Roadmap. Everything here is grounded: the {@code skill}/{@code gap} and {@code relatedMilestone}
 * come from a real, still-PLANNED catalog task on the student's roadmap, and {@code resources} are
 * drawn only from the curated {@code LearningResourceCatalog} — never AI-invented. When no resource
 * is curated for the gap yet, {@code resources} is empty and {@code hasCuratedResource} is false so
 * the UI can say so instead of fabricating a link.
 *
 * <p>{@code addToRoadmap} reuses the existing roadmap project flow (the same shape the recommended
 * projects use), so acting on a recommendation adds a portfolio project to the one Career Roadmap
 * rather than creating a second, parallel plan; {@code alreadyAdded} reflects whether that project
 * is already present.
 *
 * <p>{@code tutorialVideos} is optional, real YouTube tutorial enrichment for the gap (via {@code
 * YouTubeClient}). It is empty by default — when no YouTube key is configured, or none are found —
 * and is kept separate from the curated {@code resources} so {@code hasCuratedResource} still
 * reflects the version-controlled catalog only.
 */
public record LearningRecommendationResponse(
    String skill,
    String gap,
    String whyLearn,
    List<String> learningSequence,
    List<Resource> resources,
    String practiceTask,
    RelatedMilestone relatedMilestone,
    ProjectSuggestion addToRoadmap,
    Boolean alreadyAdded,
    Boolean hasCuratedResource,
    List<TutorialVideo> tutorialVideos) {

    /** A curated resource surfaced for the gap. Mirrors {@code LearningResourceCatalog.LearningResource}. */
    public record Resource(
        String title,
        String provider,
        String url,
        String type,
        String difficulty,
        String description,
        String estimatedEffort) {}

    /** A real YouTube tutorial video surfaced for the gap (never fabricated; empty when disabled). */
    public record TutorialVideo(
        String title,
        String channel,
        String url) {}

    /** The roadmap task/phase this recommendation is tied to, so the UI can link the two. */
    public record RelatedMilestone(
        String phaseKey,
        String phaseTitle,
        String taskId,
        String taskTitle) {}

    /**
     * A portfolio project the student can add to close the gap, posted through the existing
     * {@code POST /me/roadmap/projects}. Null when adding a project is not appropriate.
     */
    public record ProjectSuggestion(
        String name,
        String description,
        String addressesGap,
        List<String> skillsDemonstrated,
        String difficulty) {}
}
