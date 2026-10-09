package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.StudentProjectEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProjectEntryRepository extends JpaRepository<StudentProjectEntry, String> {

    List<StudentProjectEntry> findByStudentIdOrderBySortOrder(String studentId);

    void deleteByStudentId(String studentId);
}
