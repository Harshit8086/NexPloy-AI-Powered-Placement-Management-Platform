package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.student.dto.LearningRecommendationResponse;
import com.nexploy.campuslink.student.dto.LearningRecommendationResponse.ProjectSuggestion;
import com.nexploy.campuslink.student.dto.LearningRecommendationResponse.RelatedMilestone;
import com.nexploy.campuslink.student.dto.LearningRecommendationResponse.Resource;
import com.nexploy.campuslink.student.dto.LearningRecommendationResponse.TutorialVideo;
import com.nexploy.campuslink.student.dto.RoadmapResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.service.LearningResourceCatalog.LearningResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the signed-in student's Learning Recommendations — one per still-pending skill gap on their
 * Career Roadmap, paired with curated resources. This is a <em>view over</em> the existing roadmap,
 * not a second roadmap: gaps are the roadmap's own PLANNED catalog tasks (already role-scoped,
 * prerequisite-ordered, and never seeded for skills the student has demonstrated), and acting on a
 * recommendation adds a project to that same roadmap.
 *
 * <p>Grounding (PRIME DIRECTIVE): a skill only appears if it is a real pending gap; a resource only
 * appears if it is in the version-controlled {@link LearningResourceCatalog}. When the catalog has
 * nothing for a gap, the recommendation carries no resources and {@code hasCuratedResource} is false
 * — the UI shows "no curated resource yet" rather than an invented link. Demonstrated skills are
 * excluded because their roadmap tasks are DONE, unless they remain a prerequisite gap elsewhere.
 */
@Service
@RequiredArgsConstructor
public class LearningRecommendationService {

    /** Difficulty ordering so a learning sequence reads foundational -> advanced. */
    private static final Map<String, Integer> DIFFICULTY_ORDER =
        Map.of("Beginner", 0, "Intermediate", 1, "Advanced", 2);

    private final RoadmapService roadmapService;
    private final LearningResourceCatalog resourceCatalog;
    private final YouTubeClient youTubeClient;

    /**
     * Recommendations for every distinct still-pending skill on the student's roadmap, in roadmap
     * (prerequisite) order. Empty when the roadmap has no pending catalog gaps.
     */
    public List<LearningRecommendationResponse> recommend(Student student) {
        RoadmapResponse roadmap = roadmapService.getRoadmap(student);
        String role = roadmap.targetRole();

        // Titles of projects already on the roadmap, so "Add to Roadmap" reflects the real state.
        Set<String> existingProjectTitles = new LinkedHashSet<>();
        roadmap.phases().stream()
            .flatMap(p -> p.tasks().stream())
            .filter(t -> "PROJECT".equals(t.kind()))
            .forEach(t -> existingProjectTitles.add(t.title().toLowerCase(Locale.ROOT)));

        List<LearningRecommendationResponse> out = new ArrayList<>();
        Set<String> seenSkills = new LinkedHashSet<>();
        for (RoadmapResponse.Phase phase : roadmap.phases()) {
            for (RoadmapResponse.Task task : phase.tasks()) {
                // Only pending catalog learning steps are gaps; DONE tasks and student projects skip.
                if (!"TASK".equals(task.kind()) || !"PLANNED".equals(task.status())) {
                    continue;
                }
                String skill = task.skill();
                if (skill == null || skill.isBlank() || !seenSkills.add(skill.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                out.add(build(role, phase, task, skill, existingProjectTitles));
            }
        }
        return out;
    }

    private LearningRecommendationResponse build(String role, RoadmapResponse.Phase phase,
                                                 RoadmapResponse.Task task, String skill,
                                                 Set<String> existingProjectTitles) {
        String gap = (task.addressesGap() != null && !task.addressesGap().isBlank())
            ? task.addressesGap() : skill;

        List<Resource> resources = resourceCatalog.resourcesFor(skill).stream()
            .sorted(Comparator.comparingInt(r -> DIFFICULTY_ORDER.getOrDefault(r.difficulty(), 1)))
            .map(this::toDto)
            .toList();

        String whyLearn = whyLearn(skill, role, task);
        String practiceTask = task.detail() != null && !task.detail().isBlank()
            ? task.detail()
            : "Build a small project that demonstrates " + skill + ".";
        List<String> sequence = learningSequence(resources, practiceTask);

        String projectName = skill + " Showcase Project";
        ProjectSuggestion addToRoadmap = new ProjectSuggestion(
            projectName,
            "Build a focused project that demonstrates " + skill + " end-to-end.",
            gap,
            List.of(skill),
            "Intermediate");
        boolean alreadyAdded = existingProjectTitles.contains(projectName.toLowerCase(Locale.ROOT));

        RelatedMilestone milestone = new RelatedMilestone(
            phase.key(), phase.title(), task.id(), task.title());

        // Optional YouTube enrichment: real tutorial links for the gap. Empty when disabled/none.
        List<TutorialVideo> tutorialVideos = youTubeClient.searchTutorials(skill, 3).stream()
            .map(v -> new TutorialVideo(v.title(), v.channel(), v.url()))
            .toList();

        return new LearningRecommendationResponse(
            skill, gap, whyLearn, sequence, resources, practiceTask, milestone,
            addToRoadmap, alreadyAdded, !resources.isEmpty(), tutorialVideos);
    }

    private String whyLearn(String skill, String role, RoadmapResponse.Task task) {
        String roleClause = (role == null || role.isBlank())
            ? "It is a foundational skill on your roadmap"
            : skill + " is expected for a " + role;
        String detail = task.detail() != null && !task.detail().isBlank()
            ? " " + task.detail()
            : "";
        return roleClause + ", and it is still pending on your roadmap." + detail;
    }

    private List<String> learningSequence(List<Resource> resources, String practiceTask) {
        List<String> steps = new ArrayList<>();
        for (Resource r : resources) {
            steps.add("Study: " + r.title() + " (" + r.provider() + ")");
        }
        steps.add("Practise: " + practiceTask);
        return steps;
    }

    private Resource toDto(LearningResource r) {
        return new Resource(r.title(), r.provider(), r.url(), r.type(), r.difficulty(),
            r.description(), r.estimatedEffort());
    }
}
