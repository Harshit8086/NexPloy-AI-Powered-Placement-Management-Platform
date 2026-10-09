package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.StudentGoal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGoalRepository extends JpaRepository<StudentGoal, String> {

    /** One active roadmap per student, so this resolves the student's current applied goal. */
    Optional<StudentGoal> findByStudentId(String studentId);
}
