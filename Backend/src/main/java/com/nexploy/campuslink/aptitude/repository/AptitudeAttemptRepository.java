package com.nexploy.campuslink.aptitude.repository;

import com.nexploy.campuslink.aptitude.entity.AptitudeAttempt;
import com.nexploy.campuslink.aptitude.entity.AptitudeAttemptMode;
import com.nexploy.campuslink.aptitude.entity.AptitudeAttemptStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AptitudeAttemptRepository extends JpaRepository<AptitudeAttempt, String> {

    /** A student's attempts, newest first, for the history list. */
    List<AptitudeAttempt> findByStudentIdOrderByStartedAtDesc(String studentId);

    /** Ownership-scoped lookup — a different student's attempt reads as not found. */
    Optional<AptitudeAttempt> findByIdAndStudentId(String id, String studentId);

    /** Resume support: an unfinished attempt for this test already in flight. */
    Optional<AptitudeAttempt> findFirstByStudentIdAndTestIdAndStatus(String studentId, String testId,
                                                                      AptitudeAttemptStatus status);

    /** Most recent completed attempt per test id, for the "last score" badge on test cards. */
    Optional<AptitudeAttempt> findFirstByStudentIdAndTestIdAndModeAndStatusOrderByStartedAtDesc(
        String studentId, String testId, AptitudeAttemptMode mode, AptitudeAttemptStatus status);

    /** Resume support: an unfinished retry of this source attempt already in flight. */
    Optional<AptitudeAttempt> findFirstByStudentIdAndSourceAttemptIdAndStatus(
        String studentId, String sourceAttemptId, AptitudeAttemptStatus status);
}
