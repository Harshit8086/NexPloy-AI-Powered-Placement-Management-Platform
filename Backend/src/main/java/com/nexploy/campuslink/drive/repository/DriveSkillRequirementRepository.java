package com.nexploy.campuslink.drive.repository;

import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface DriveSkillRequirementRepository extends JpaRepository<DriveSkillRequirement, String> {

    List<DriveSkillRequirement> findByDriveId(String driveId);

    List<DriveSkillRequirement> findByDriveIdOrderByWeightDescSkillAsc(String driveId);

    List<DriveSkillRequirement> findByDriveIdAndApprovedTrue(String driveId);

    List<DriveSkillRequirement> findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc(String driveId);

    Optional<DriveSkillRequirement> findByDriveIdAndSkillIgnoreCase(String driveId, String skill);

    boolean existsByDriveIdAndSkillIgnoreCase(String driveId, String skill);

    long countByDriveIdAndApprovedTrue(String driveId);

    @Modifying
    @Transactional
    void deleteByDriveId(String driveId);
}
