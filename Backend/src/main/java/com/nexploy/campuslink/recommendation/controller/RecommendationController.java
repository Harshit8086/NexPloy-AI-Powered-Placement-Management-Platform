package com.nexploy.campuslink.recommendation.controller;

import com.nexploy.campuslink.recommendation.dto.CandidateRecommendationResponse;
import com.nexploy.campuslink.recommendation.dto.RecommendationResponse;
import com.nexploy.campuslink.recommendation.service.RecommendationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for the explainable recommendation. The two GETs are side-effect free from the caller's
 * point of view (they may lazily populate deterministic evidence and serve a persisted explanation, but
 * never regenerate AI text); the POST forces a fresh AI generation. All authorization and the
 * deterministic/AI separation live in {@link RecommendationService}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** Explainable recommendation for one application. Owning student or a manager of the drive. */
    @GetMapping("/applications/{applicationId}/recommendation")
    public RecommendationResponse forApplication(@PathVariable String applicationId) {
        return recommendationService.forApplication(applicationId, false);
    }

    /** Force a fresh AI generation for one application, then return it. */
    @PostMapping("/applications/{applicationId}/recommendation/regenerate")
    public RecommendationResponse regenerate(@PathVariable String applicationId) {
        return recommendationService.forApplication(applicationId, true);
    }

    /** Ranked candidate recommendations for a drive (managers only); persisted explanations only. */
    @GetMapping("/drives/{driveId}/candidate-recommendations")
    public List<CandidateRecommendationResponse> forDrive(@PathVariable String driveId) {
        return recommendationService.forDrive(driveId);
    }
}
