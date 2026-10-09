package com.nexploy.campuslink.recommendation.repository;

import com.nexploy.campuslink.recommendation.entity.RecommendationExplanation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationExplanationRepository
    extends JpaRepository<RecommendationExplanation, String> {

    Optional<RecommendationExplanation> findByMatchResultId(String matchResultId);

    List<RecommendationExplanation> findByMatchResultIdIn(List<String> matchResultIds);
}
