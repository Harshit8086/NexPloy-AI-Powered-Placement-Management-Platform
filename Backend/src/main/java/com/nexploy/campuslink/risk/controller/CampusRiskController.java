package com.nexploy.campuslink.risk.controller;

import com.nexploy.campuslink.risk.dto.RiskEntryResponse;
import com.nexploy.campuslink.risk.service.RiskService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campus-wide risk roll-up: the latest risk entry per student, for the officer Risk Radar and
 * the command-center high-risk panel. Campus access is enforced in the service.
 */
@RestController
@RequestMapping("/api/campuses/{campusId}/risk-entries")
@RequiredArgsConstructor
public class CampusRiskController {

    private final RiskService riskService;

    @GetMapping
    public List<RiskEntryResponse> listForCampus(@PathVariable String campusId) {
        return riskService.listCampusEntries(campusId);
    }

    @PostMapping("/assess-all")
    public List<RiskEntryResponse> assessCampus(
        @PathVariable String campusId,
        @RequestParam(defaultValue = "current") String placementCycleId
    ) {
        return riskService.assessCampus(campusId, placementCycleId);
    }
}
