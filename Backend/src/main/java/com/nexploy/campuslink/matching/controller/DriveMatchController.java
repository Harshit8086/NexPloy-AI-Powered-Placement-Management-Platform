package com.nexploy.campuslink.matching.controller;

import com.nexploy.campuslink.matching.dto.AutoShortlistRequest;
import com.nexploy.campuslink.matching.dto.AutoShortlistResponse;
import com.nexploy.campuslink.matching.dto.MatchResponse;
import com.nexploy.campuslink.matching.dto.ShortlistDecisionResponse;
import com.nexploy.campuslink.matching.dto.ShortlistRequest;
import com.nexploy.campuslink.matching.service.MatchingService;
import com.nexploy.campuslink.matching.service.ShortlistService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side of matching: the explainable ranked candidate pool for a drive, in the frontend
 * {@code CandidateMatch} shape. Distinct from {@code POST .../ranking}, which ranks a
 * caller-supplied candidate list; this GET derives the pool (applicants) and features from
 * stored data. Campus access is enforced in the service.
 */
@RestController
@RequestMapping("/api/drives/{driveId}/matches")
@RequiredArgsConstructor
public class DriveMatchController {

    private final MatchingService matchingService;
    private final ShortlistService shortlistService;

    @GetMapping
    public List<MatchResponse> matchesForDrive(
        @PathVariable String driveId,
        @RequestParam(defaultValue = "current") String placementCycleId
    ) {
        return matchingService.matchesForDrive(driveId, placementCycleId);
    }

    /**
     * Officer shortlist/reject decision over a scored candidate. Records the decision and
     * advances the application funnel (SHORTLISTED / REJECTED).
     */
    @PostMapping("/{studentId}/shortlist")
    public ShortlistDecisionResponse shortlist(
        @PathVariable String driveId,
        @PathVariable String studentId,
        @RequestBody(required = false) ShortlistRequest request
    ) {
        return shortlistService.decide(driveId, studentId, request);
    }

    /**
     * Batch auto-shortlist over the drive's scored applicants. Eligibility is re-verified
     * server-side as the hard gate; eligible applicants at or above the fit floor are shortlisted.
     */
    @PostMapping("/auto-shortlist")
    public AutoShortlistResponse autoShortlist(
        @PathVariable String driveId,
        @RequestBody(required = false) AutoShortlistRequest request
    ) {
        return shortlistService.autoShortlist(driveId, request);
    }
}
