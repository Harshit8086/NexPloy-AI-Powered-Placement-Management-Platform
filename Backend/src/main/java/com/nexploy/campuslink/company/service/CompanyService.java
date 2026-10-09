package com.nexploy.campuslink.company.service;

import com.nexploy.campuslink.company.dto.CompanyDriveSummary;
import com.nexploy.campuslink.company.dto.CompanyResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Assembles a {@link CompanyResponse} from a recruiter row plus that recruiter's real drives. A
 * company here is a recruiter's presence at a campus; the drive counts and summaries are computed
 * from persisted {@code drives}, never invented. Ordering is deterministic (drive id) so responses
 * are stable.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final DriveRepository driveRepository;

    public CompanyResponse toResponse(Recruiter recruiter) {
        List<Drive> drives = driveRepository.findByRecruiterId(recruiter.getId()).stream()
            .sorted(Comparator.comparing(Drive::getId))
            .toList();

        int activeDriveCount = (int) drives.stream()
            .filter(d -> d.getStatus() == DriveStatus.ACTIVE)
            .count();

        List<CompanyDriveSummary> summaries = drives.stream()
            .map(d -> new CompanyDriveSummary(
                d.getId(),
                d.getRole(),
                d.getStatus() == null ? null : d.getStatus().name(),
                d.getLocation(),
                d.getPackageLpa(),
                d.getDeadline()))
            .toList();

        return new CompanyResponse(
            recruiter.getId(),
            recruiter.getCampusId(),
            recruiter.getCompanyName(),
            recruiter.getCompanyIndustry(),
            recruiter.getCompanyWebsite(),
            recruiter.getCompanyLocation(),
            activeDriveCount,
            drives.size(),
            summaries
        );
    }
}
