package com.nexploy.campuslink.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.student.dto.LearningRecommendationResponse;
import com.nexploy.campuslink.student.dto.RoadmapResponse;
import com.nexploy.campuslink.student.dto.RoadmapResponse.Phase;
import com.nexploy.campuslink.student.dto.RoadmapResponse.Task;
import com.nexploy.campuslink.student.entity.Student;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LearningRecommendationService}: recommendations are built only from real,
 * still-PLANNED roadmap gaps (demonstrated/DONE skills are excluded), each carries only curated
 * resources (empty + {@code hasCuratedResource=false} when none exist — never invented), gaps map to
 * their roadmap milestone, and {@code alreadyAdded} reflects an existing portfolio project.
 */
@ExtendWith(MockitoExtension.class)
class LearningRecommendationServiceTest {

    @Mock
    private RoadmapService roadmapService;

    private LearningRecommendationService service;

    @BeforeEach
    void setUp() {
        // YouTubeClient with no key is inert: searchTutorials returns empty, so tutorialVideos stay
        // empty and enrichment never touches the curated-resource assertions below.
        service = new LearningRecommendationService(
            roadmapService,
            new LearningResourceCatalog(),
            new YouTubeClient(null, "https://www.googleapis.com/youtube/v3",
                new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    private Student student() {
        return Student.builder().id("stu_1").campusId("campus_a").email("s@campus.example")
            .targetRole("Backend Developer").build();
    }

    private Task task(String id, String title, String skill, String kind, String status) {
        return new Task(id, title, "Do " + title, skill, kind, "CATALOG", status, skill);
    }

    @Test
    void recommendsOnlyPendingSkillsAndSkipsDoneOnes() {
        RoadmapResponse roadmap = new RoadmapResponse("Backend Developer", 20, 1, 3, 1, 0, null,
            List.of(new Phase("apis", "Build REST APIs", "why", "CURRENT", 33, List.of(
                task("t1", "Persist data with SQL", "SQL", "TASK", "DONE"),
                task("t2", "Design and build a REST API", "REST API", "TASK", "PLANNED"),
                task("t3", "Design and build a REST API", "REST API", "TASK", "PLANNED")))),
            List.of());
        when(roadmapService.getRoadmap(any())).thenReturn(roadmap);

        List<LearningRecommendationResponse> recs = service.recommend(student());

        // SQL is DONE -> excluded; REST API appears once (deduped) even though two tasks reference it.
        assertThat(recs).hasSize(1);
        LearningRecommendationResponse rest = recs.get(0);
        assertThat(rest.skill()).isEqualTo("REST API");
        assertThat(rest.relatedMilestone().taskId()).isEqualTo("t2");
        assertThat(rest.relatedMilestone().phaseKey()).isEqualTo("apis");
        assertThat(rest.hasCuratedResource()).isTrue();
        assertThat(rest.resources()).isNotEmpty();
        assertThat(rest.resources()).allMatch(r -> r.url().startsWith("https://"));
        assertThat(rest.learningSequence()).isNotEmpty();
        assertThat(rest.practiceTask()).isNotBlank();
    }

    @Test
    void gapWithNoCuratedResourceIsFlaggedNotInvented() {
        RoadmapResponse roadmap = new RoadmapResponse("Backend Developer", 0, 1, 1, 0, 0, null,
            List.of(new Phase("scale", "Scale", "why", "CURRENT", 0, List.of(
                task("t9", "Learn an exotic skill", "Nonexistent Skill", "TASK", "PLANNED")))),
            List.of());
        when(roadmapService.getRoadmap(any())).thenReturn(roadmap);

        List<LearningRecommendationResponse> recs = service.recommend(student());

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).hasCuratedResource()).isFalse();
        assertThat(recs.get(0).resources()).isEmpty();
        // Still actionable: a practice task + the roadmap link are grounded even with no resource.
        assertThat(recs.get(0).practiceTask()).isNotBlank();
        assertThat(recs.get(0).addToRoadmap()).isNotNull();
    }

    @Test
    void alreadyAddedReflectsAnExistingPortfolioProject() {
        RoadmapResponse roadmap = new RoadmapResponse("Backend Developer", 0, 2, 2, 1, 1, null,
            List.of(
                new Phase("apis", "Build REST APIs", "why", "CURRENT", 0, List.of(
                    task("t2", "Design and build a REST API", "REST API", "TASK", "PLANNED"))),
                new Phase("projects", "Portfolio Projects", "p", "UPCOMING", 0, List.of(
                    new Task("p1", "REST API Showcase Project", "d", "REST API", "PROJECT",
                        "STUDENT", "PLANNED", "REST API")))),
            List.of());
        when(roadmapService.getRoadmap(any())).thenReturn(roadmap);

        List<LearningRecommendationResponse> recs = service.recommend(student());

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).alreadyAdded()).isTrue();
    }

    @Test
    void emptyRoadmapYieldsNoRecommendations() {
        RoadmapResponse roadmap = new RoadmapResponse("Backend Developer", 100, 0, 0, 0, 0, null,
            List.of(), List.of());
        when(roadmapService.getRoadmap(any())).thenReturn(roadmap);

        assertThat(service.recommend(student())).isEmpty();
    }
}
