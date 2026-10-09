package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.StudentCertificate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificateRepository extends JpaRepository<StudentCertificate, String> {

    /** A student's certificates, newest first — the profile showcase / list order. */
    List<StudentCertificate> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
