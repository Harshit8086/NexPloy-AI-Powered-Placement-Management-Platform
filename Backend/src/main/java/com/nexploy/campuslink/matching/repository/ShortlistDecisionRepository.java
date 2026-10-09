package com.nexploy.campuslink.matching.repository;

import com.nexploy.campuslink.matching.entity.ShortlistDecision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortlistDecisionRepository extends JpaRepository<ShortlistDecision, String> {

    List<ShortlistDecision> findByDriveId(String driveId);

    /** The most recent officer decision for a (drive, student) pair, used to derive the recommendation type. */
    Optional<ShortlistDecision> findFirstByDriveIdAndStudentIdOrderByDecidedAtDesc(
        String driveId, String studentId);
}
