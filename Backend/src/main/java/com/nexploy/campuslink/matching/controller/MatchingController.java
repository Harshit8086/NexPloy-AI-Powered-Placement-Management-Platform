package com.nexploy.campuslink.matching.controller;

import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingResult;
import com.nexploy.campuslink.matching.dto.RankRequestBody;
import com.nexploy.campuslink.matching.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drives/{driveId}/ranking")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @PostMapping
    public MatchingResult rank(
        @PathVariable String driveId,
        @RequestParam(defaultValue = "current") String placementCycleId,
        @Valid @RequestBody RankRequestBody body
    ) {
        return matchingService.rank(driveId, placementCycleId, body);
    }
}
