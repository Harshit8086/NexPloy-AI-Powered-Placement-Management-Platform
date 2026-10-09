package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.StudentCertificateFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCertificateFileRepository extends JpaRepository<StudentCertificateFile, String> {
}
