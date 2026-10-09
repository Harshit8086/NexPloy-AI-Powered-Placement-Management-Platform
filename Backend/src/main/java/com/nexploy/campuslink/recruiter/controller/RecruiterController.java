package com.nexploy.campuslink.recruiter.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.recruiter.dto.RecruiterResponse;
import com.nexploy.campuslink.recruiter.dto.RecruiterSettingsRequest;
import com.nexploy.campuslink.recruiter.dto.RecruiterSettingsResponse;
import com.nexploy.campuslink.recruiter.dto.RecruiterUpdateRequest;
import com.nexploy.campuslink.recruiter.dto.SetRecruiterActiveRequest;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.recruiter.service.RecruiterService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruiters")
@RequiredArgsConstructor
public class RecruiterController {

    private final RecruiterRepository recruiterRepository;
    private final RecruiterService recruiterService;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public List<RecruiterResponse> listRecruiters() {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see every campus's recruiters; officers are scoped to their own campus.
        List<Recruiter> recruiters = user.getRole() == UserRole.ADMIN
            ? recruiterRepository.findAll()
            : recruiterRepository.findByCampusId(user.getCampusId());
        return recruiters.stream().map(recruiterService::toResponse).toList();
    }

    /** The signed-in recruiter's own company profile (username == email for RECRUITER accounts). */
    @GetMapping("/me")
    public RecruiterResponse getOwnProfile() {
        return recruiterService.toResponse(currentRecruiter());
    }

    /** The signed-in recruiter edits their own account/company profile. Partial patch. */
    @PutMapping("/me")
    public RecruiterResponse updateOwnProfile(@Valid @RequestBody RecruiterUpdateRequest request) {
        Recruiter updated = recruiterService.applyUpdate(currentRecruiter(), request);
        return recruiterService.toResponse(updated);
    }

    /** The signed-in recruiter's persisted settings (notifications, preferences, privacy). */
    @GetMapping("/me/settings")
    public RecruiterSettingsResponse getOwnSettings() {
        return recruiterService.toSettingsResponse(
            recruiterService.getOrCreateSettings(currentRecruiter().getId()));
    }

    /** The signed-in recruiter updates their settings. Partial patch (per section). */
    @PutMapping("/me/settings")
    public RecruiterSettingsResponse updateOwnSettings(@Valid @RequestBody RecruiterSettingsRequest request) {
        return recruiterService.toSettingsResponse(
            recruiterService.applySettings(currentRecruiter().getId(), request));
    }

    /** Resolves the recruiter row for the signed-in recruiter account (username == email). */
    private Recruiter currentRecruiter() {
        UserAccount user = campusAccessService.currentUser();
        return recruiterRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("Recruiter not found: " + user.getUsername()));
    }

    @GetMapping("/{recruiterId}")
    public RecruiterResponse getRecruiter(@PathVariable String recruiterId) {
        UserAccount user = campusAccessService.currentUser();
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new IllegalArgumentException("Recruiter not found: " + recruiterId));

        campusAccessService.requireAccess(user, recruiter.getCampusId());
        return recruiterService.toResponse(recruiter);
    }

    /** Officer/admin edits any recruiter within their campus reach (bounded, self-service fields). */
    @PutMapping("/{recruiterId}")
    public RecruiterResponse updateRecruiter(
        @PathVariable String recruiterId,
        @Valid @RequestBody RecruiterUpdateRequest request
    ) {
        return recruiterService.toResponse(recruiterService.adminUpdate(recruiterId, request));
    }

    /** Officer/admin (de)activates a recruiter — soft-delete via the active flag. */
    @PatchMapping("/{recruiterId}/active")
    public RecruiterResponse setRecruiterActive(
        @PathVariable String recruiterId,
        @Valid @RequestBody SetRecruiterActiveRequest request
    ) {
        return recruiterService.toResponse(recruiterService.setActive(recruiterId, request.active()));
    }
}
