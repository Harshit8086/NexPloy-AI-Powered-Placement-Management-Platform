package com.nexploy.campuslink.matching.repository;

import com.nexploy.campuslink.matching.entity.MatchResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResult, String> {

    List<MatchResult> findByDriveIdOrderByOverallFitDesc(String driveId);

    Optional<MatchResult> findByDriveIdAndStudentIdAndModelVersion(
        String driveId, String studentId, String modelVersion);

    Optional<MatchResult> findFirstByDriveIdAndStudentIdOrderByComputedAtDesc(
        String driveId, String studentId);
}
