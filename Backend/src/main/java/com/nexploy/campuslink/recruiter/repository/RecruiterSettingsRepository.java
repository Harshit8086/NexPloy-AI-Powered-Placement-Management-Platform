package com.nexploy.campuslink.recruiter.repository;

import com.nexploy.campuslink.recruiter.entity.RecruiterSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruiterSettingsRepository extends JpaRepository<RecruiterSettings, String> {

    Optional<RecruiterSettings> findByRecruiterId(String recruiterId);
}
