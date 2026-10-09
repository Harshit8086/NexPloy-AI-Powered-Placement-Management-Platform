package com.nexploy.campuslink.placement.controller;

import com.nexploy.campuslink.placement.dto.ChangeCycleStatusRequest;
import com.nexploy.campuslink.placement.dto.CreatePlacementCycleRequest;
import com.nexploy.campuslink.placement.dto.PlacementCycleResponse;
import com.nexploy.campuslink.placement.dto.UpdatePlacementCycleRequest;
import com.nexploy.campuslink.placement.service.PlacementCycleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/placement-cycles")
@RequiredArgsConstructor
public class PlacementCycleController {

    private final PlacementCycleService placementCycleService;

    @GetMapping
    public List<PlacementCycleResponse> listCycles(@RequestParam(required = false) String campusId) {
        return placementCycleService.list(campusId);
    }

    @GetMapping("/{cycleId}")
    public PlacementCycleResponse getCycle(@PathVariable String cycleId) {
        return placementCycleService.get(cycleId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlacementCycleResponse createCycle(@Valid @RequestBody CreatePlacementCycleRequest request) {
        return placementCycleService.create(request);
    }

    @PutMapping("/{cycleId}")
    public PlacementCycleResponse updateCycle(
        @PathVariable String cycleId,
        @Valid @RequestBody UpdatePlacementCycleRequest request
    ) {
        return placementCycleService.update(cycleId, request);
    }

    @PatchMapping("/{cycleId}/status")
    public PlacementCycleResponse changeCycleStatus(
        @PathVariable String cycleId,
        @Valid @RequestBody ChangeCycleStatusRequest request
    ) {
        return placementCycleService.changeStatus(cycleId, request.status());
    }
}
