package com.nexploy.campuslink.risk.controller;

import com.nexploy.campuslink.risk.client.MlRiskClient.RiskPrediction;
import com.nexploy.campuslink.risk.dto.RiskEntryResponse;
import com.nexploy.campuslink.risk.dto.RiskPredictRequestBody;
import com.nexploy.campuslink.risk.service.RiskService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @PostMapping
    public RiskPrediction predict(
        @PathVariable String studentId,
        @RequestParam(defaultValue = "current") String placementCycleId,
        @Valid @RequestBody RiskPredictRequestBody body
    ) {
        return riskService.predict(studentId, placementCycleId, body);
    }

    /** Persisted risk history for the student (newest first). */
    @GetMapping("/entries")
    public List<RiskEntryResponse> entries(@PathVariable String studentId) {
        return riskService.listEntries(studentId);
    }
}
