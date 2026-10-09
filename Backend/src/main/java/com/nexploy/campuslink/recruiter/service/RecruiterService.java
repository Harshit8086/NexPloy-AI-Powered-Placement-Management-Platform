package com.nexploy.campuslink.recruiter.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.recruiter.dto.RecruiterResponse;
import com.nexploy.campuslink.recruiter.dto.RecruiterSettingsRequest;
import com.nexploy.campuslink.recruiter.dto.RecruiterSettingsResponse;
import com.nexploy.campuslink.recruiter.dto.RecruiterUpdateRequest;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.entity.RecruiterSettings;
import com.nexploy.campuslink.recruiter.exception.RecruiterNotFoundException;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.recruiter.repository.RecruiterSettingsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the recruiter/company profile response. Identity + company columns come straight
 * from the {@code recruiters} row; the two derived numbers are computed from the recruiter's
 * own persisted data:
 * <ul>
 *   <li>{@code activeDriveIds} — the ids of this recruiter's ACTIVE drives.</li>
 *   <li>{@code studentsHired} — how many of this recruiter's offers a student has joined.</li>
 * </ul>
 * Nothing here is fabricated: an unknown recruiter with no drives/offers yields an empty list
 * and a zero count, which the frontend surfaces as an empty state.
 */
@Service
@RequiredArgsConstructor
public class RecruiterService {

    private final DriveRepository driveRepository;
    private final OfferRepository offerRepository;
    private final RecruiterRepository recruiterRepository;
    private final RecruiterSettingsRepository recruiterSettingsRepository;
    private final CampusAccessService campusAccessService;

    public RecruiterResponse toResponse(Recruiter recruiter) {
        List<String> activeDriveIds = driveRepository.findByRecruiterId(recruiter.getId()).stream()
            .filter(drive -> drive.getStatus() == DriveStatus.ACTIVE)
            .map(drive -> drive.getId())
            .toList();

        int studentsHired = (int) offerRepository.findByRecruiterId(recruiter.getId()).stream()
            .filter(offer -> offer.isJoined())
            .count();

        return new RecruiterResponse(
            recruiter.getId(),
            recruiter.getCampusId(),
            recruiter.getName(),
            recruiter.getCompanyName(),
            recruiter.getEmail(),
            recruiter.isActive(),
            recruiter.getPhone(),
            recruiter.getCompanyWebsite(),
            recruiter.getCompanyIndustry(),
            recruiter.getCompanyLocation(),
            recruiter.getDesignation(),
            resolveLogoInitial(recruiter),
            recruiter.getCompanyDescription(),
            recruiter.getCompanySize(),
            recruiter.getCompanyLinkedin(),
            recruiter.getCompanyTwitter(),
            recruiter.getCompanyGithub(),
            activeDriveIds,
            studentsHired
        );
    }

    /** The saved initial, or a fallback derived from the company name's first letter. */
    private String resolveLogoInitial(Recruiter recruiter) {
        String saved = recruiter.getLogoInitial();
        if (saved != null && !saved.isBlank()) {
            return saved;
        }
        String company = recruiter.getCompanyName();
        return (company == null || company.isBlank())
            ? "?"
            : company.trim().substring(0, 1).toUpperCase();
    }

    /**
     * Applies a partial profile edit: every non-null field overwrites its column, a null leaves it
     * unchanged. Identity/scope fields (id, email, campusId, active) are never touched here — used
     * by both the recruiter's own self-service PUT /me and the officer/admin management PUT.
     */
    @Transactional
    public Recruiter applyUpdate(Recruiter recruiter, RecruiterUpdateRequest request) {
        if (request.name() != null) recruiter.setName(request.name().trim());
        if (request.phone() != null) recruiter.setPhone(request.phone().trim());
        if (request.designation() != null) recruiter.setDesignation(request.designation().trim());
        if (request.logoInitial() != null) {
            String initial = request.logoInitial().trim();
            recruiter.setLogoInitial(initial.isEmpty() ? null : initial.toUpperCase());
        }
        if (request.companyName() != null) recruiter.setCompanyName(request.companyName().trim());
        if (request.companyIndustry() != null) recruiter.setCompanyIndustry(request.companyIndustry().trim());
        if (request.companyWebsite() != null) recruiter.setCompanyWebsite(request.companyWebsite().trim());
        if (request.companyLocation() != null) recruiter.setCompanyLocation(request.companyLocation().trim());
        if (request.companyDescription() != null) recruiter.setCompanyDescription(request.companyDescription().trim());
        if (request.companySize() != null) recruiter.setCompanySize(request.companySize().trim());
        if (request.companyLinkedin() != null) recruiter.setCompanyLinkedin(request.companyLinkedin().trim());
        if (request.companyTwitter() != null) recruiter.setCompanyTwitter(request.companyTwitter().trim());
        if (request.companyGithub() != null) recruiter.setCompanyGithub(request.companyGithub().trim());
        return recruiterRepository.save(recruiter);
    }

    /**
     * Officer/admin management edit of any recruiter within the caller's campus reach. Same bounded
     * fields as self-service (identity/scope never editable); campus access is enforced.
     */
    @Transactional
    public Recruiter adminUpdate(String recruiterId, RecruiterUpdateRequest request) {
        Recruiter recruiter = loadManageable(recruiterId);
        return applyUpdate(recruiter, request);
    }

    /** Officer/admin (de)activation of a recruiter — soft-delete via the active flag. */
    @Transactional
    public Recruiter setActive(String recruiterId, boolean active) {
        Recruiter recruiter = loadManageable(recruiterId);
        recruiter.setActive(active);
        return recruiterRepository.save(recruiter);
    }

    /** Loads a recruiter for officer/admin management, enforcing role and campus access. */
    private Recruiter loadManageable(String recruiterId) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage recruiters");
        }
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
        campusAccessService.requireAccess(user, recruiter.getCampusId());
        return recruiter;
    }

    /** The recruiter's settings row, lazily created with defaults the first time it is read. */
    @Transactional
    public RecruiterSettings getOrCreateSettings(String recruiterId) {
        return recruiterSettingsRepository.findByRecruiterId(recruiterId)
            .orElseGet(() -> recruiterSettingsRepository.save(RecruiterSettings.defaults(recruiterId)));
    }

    /** Partial settings update — every non-null field overwrites, a null leaves it unchanged. */
    @Transactional
    public RecruiterSettings applySettings(String recruiterId, RecruiterSettingsRequest request) {
        RecruiterSettings s = getOrCreateSettings(recruiterId);
        if (request.notifyNewApplication() != null) s.setNotifyNewApplication(request.notifyNewApplication());
        if (request.notifyShortlisted() != null) s.setNotifyShortlisted(request.notifyShortlisted());
        if (request.notifyInterview() != null) s.setNotifyInterview(request.notifyInterview());
        if (request.notifyOffer() != null) s.setNotifyOffer(request.notifyOffer());
        if (request.notifyDrive() != null) s.setNotifyDrive(request.notifyDrive());
        if (request.emailNotifications() != null) s.setEmailNotifications(request.emailNotifications());
        if (request.inAppNotifications() != null) s.setInAppNotifications(request.inAppNotifications());
        if (request.theme() != null) s.setTheme(request.theme().trim());
        if (request.language() != null) s.setLanguage(request.language().trim());
        if (request.timezone() != null) s.setTimezone(request.timezone().trim());
        if (request.dateFormat() != null) s.setDateFormat(request.dateFormat().trim());
        if (request.profileVisibility() != null) s.setProfileVisibility(request.profileVisibility().trim());
        if (request.contactVisibility() != null) s.setContactVisibility(request.contactVisibility().trim());
        if (request.allowDataAnalytics() != null) s.setAllowDataAnalytics(request.allowDataAnalytics());
        if (request.allowMarketingEmail() != null) s.setAllowMarketingEmail(request.allowMarketingEmail());
        return recruiterSettingsRepository.save(s);
    }

    public RecruiterSettingsResponse toSettingsResponse(RecruiterSettings settings) {
        return RecruiterSettingsResponse.from(settings);
    }
}
