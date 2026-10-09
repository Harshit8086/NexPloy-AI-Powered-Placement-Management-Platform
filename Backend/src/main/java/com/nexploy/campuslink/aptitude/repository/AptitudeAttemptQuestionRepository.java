package com.nexploy.campuslink.aptitude.repository;

import com.nexploy.campuslink.aptitude.entity.AptitudeAttemptQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AptitudeAttemptQuestionRepository extends JpaRepository<AptitudeAttemptQuestion, String> {

    /** The attempt's drawn questions in display order. */
    List<AptitudeAttemptQuestion> findByAttemptIdOrderByOrdinalAsc(String attemptId);

    /** Answer-save target: must belong to the attempt being edited. */
    Optional<AptitudeAttemptQuestion> findByIdAndAttemptId(String id, String attemptId);
}
