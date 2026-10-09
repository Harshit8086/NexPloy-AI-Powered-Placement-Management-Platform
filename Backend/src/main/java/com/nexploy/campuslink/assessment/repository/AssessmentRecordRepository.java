package com.nexploy.campuslink.assessment.repository;

import com.nexploy.campuslink.assessment.entity.AssessmentRecord;
import com.nexploy.campuslink.assessment.entity.AssessmentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRecordRepository extends JpaRepository<AssessmentRecord, String> {

    /** All of a student's dated records, used to rebuild the current snapshot (latest per type). */
    List<AssessmentRecord> findByStudentId(String studentId);

    /**
     * Existing row for the business key {@code (student, type, name, date)}. Present → the import
     * updates it in place; absent → the import inserts a new dated record.
     */
    Optional<AssessmentRecord> findByStudentIdAndAssessmentTypeAndAssessmentNameAndAssessmentDate(
        String studentId, AssessmentType assessmentType, String assessmentName, LocalDate assessmentDate);
}
