package com.nexploy.campuslink.attendance.controller;

import com.nexploy.campuslink.attendance.client.MlAttendanceClient.AttendancePrediction;
import com.nexploy.campuslink.attendance.dto.AttendancePredictRequestBody;
import com.nexploy.campuslink.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule/events/{slotId}/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    public AttendancePrediction predict(
        @PathVariable String slotId,
        @RequestParam String studentId,
        @RequestParam(defaultValue = "current") String placementCycleId,
        @Valid @RequestBody AttendancePredictRequestBody body
    ) {
        return attendanceService.predict(slotId, studentId, placementCycleId, body);
    }
}
