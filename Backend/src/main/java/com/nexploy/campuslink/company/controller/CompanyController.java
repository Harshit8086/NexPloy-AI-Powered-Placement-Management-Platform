package com.nexploy.campuslink.company.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.company.dto.CompanyResponse;
import com.nexploy.campuslink.company.service.CompanyService;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only company directory. A company is a recruiter's presence at a campus, aggregated with its
 * real drives. Campus-scoped like the rest of the app: OFFICER/ADMIN see every campus, everyone else
 * sees only their own campus. No fabricated companies — the list is exactly the recruiter rows.
 */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final RecruiterRepository recruiterRepository;
    private final CompanyService companyService;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public List<CompanyResponse> listCompanies() {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see every campus's companies; officers are scoped to their own campus.
        List<Recruiter> recruiters = user.getRole() == UserRole.ADMIN
            ? recruiterRepository.findAll()
            : recruiterRepository.findByCampusId(user.getCampusId());
        return recruiters.stream()
            .sorted(Comparator.comparing(Recruiter::getId))
            .map(companyService::toResponse)
            .toList();
    }

    @GetMapping("/{companyId}")
    public CompanyResponse getCompany(@PathVariable String companyId) {
        UserAccount user = campusAccessService.currentUser();
        Recruiter recruiter = recruiterRepository.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

        campusAccessService.requireAccess(user, recruiter.getCampusId());
        return companyService.toResponse(recruiter);
    }
}
