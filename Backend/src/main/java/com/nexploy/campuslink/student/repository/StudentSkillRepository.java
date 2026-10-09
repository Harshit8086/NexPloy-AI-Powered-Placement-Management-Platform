package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.StudentSkill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentSkillRepository extends JpaRepository<StudentSkill, String> {

    List<StudentSkill> findByStudentIdOrderBySkillAsc(String studentId);

    List<StudentSkill> findByCampusId(String campusId);

    /** Case-insensitive lookup so "java" and "Java" resolve to the same row for upsert. */
    Optional<StudentSkill> findByStudentIdAndSkillIgnoreCase(String studentId, String skill);
}
