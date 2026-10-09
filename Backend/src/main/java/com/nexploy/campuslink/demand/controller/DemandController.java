package com.nexploy.campuslink.demand.controller;

import com.nexploy.campuslink.demand.client.MlDemandClient.DemandForecastResult;
import com.nexploy.campuslink.demand.dto.DemandForecastRequestBody;
import com.nexploy.campuslink.demand.service.DemandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campuses/{campusId}/demand-forecast")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    @PostMapping
    public DemandForecastResult forecast(
        @PathVariable String campusId,
        @RequestParam(defaultValue = "current") String placementCycleId,
        @Valid @RequestBody DemandForecastRequestBody body
    ) {
        return demandService.forecast(campusId, placementCycleId, body);
    }
}
