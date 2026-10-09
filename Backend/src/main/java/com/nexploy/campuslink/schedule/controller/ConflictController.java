package com.nexploy.campuslink.schedule.controller;

import com.nexploy.campuslink.schedule.dto.ScheduleConflictResponse;
import com.nexploy.campuslink.schedule.service.ConflictDetectionService;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule/conflicts")
@RequiredArgsConstructor
public class ConflictController {

    private final ConflictDetectionService conflictDetectionService;

    @GetMapping
    public List<ScheduleConflictResponse> listConflicts(
        @RequestParam(required = false) String campusId
    ) {
        return conflictDetectionService.detect(campusId);
    }
}

