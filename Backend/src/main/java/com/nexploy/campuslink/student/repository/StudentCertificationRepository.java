package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.StudentCertification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificationRepository extends JpaRepository<StudentCertification, String> {

    List<StudentCertification> findByStudentIdOrderBySortOrder(String studentId);

    void deleteByStudentId(String studentId);
}
