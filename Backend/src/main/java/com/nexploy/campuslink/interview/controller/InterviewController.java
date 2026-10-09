package com.nexploy.campuslink.interview.controller;

import com.nexploy.campuslink.interview.dto.InterviewResponse;
import com.nexploy.campuslink.interview.dto.RecordResultRequest;
import com.nexploy.campuslink.interview.dto.ScheduleInterviewRequest;
import com.nexploy.campuslink.interview.dto.UpdateInterviewRequest;
import com.nexploy.campuslink.interview.service.InterviewService;
import com.nexploy.campuslink.shared.dto.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping
    public ResponseEntity<?> listInterviews(
        @RequestParam(required = false) String campusId,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        if (PageRequests.requested(page, size)) {
            return ResponseEntity.ok(interviewService.list(campusId, PageRequests.of(page, size, sort)));
        }
        return ResponseEntity.ok(interviewService.list(campusId));
    }

    @GetMapping("/{interviewId}")
    public InterviewResponse getInterview(@PathVariable String interviewId) {
        return interviewService.get(interviewId);
    }

    /** POST /api/interviews — officer/recruiter schedules a new interview slot. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse scheduleInterview(@Valid @RequestBody ScheduleInterviewRequest request) {
        return interviewService.schedule(request);
    }

    /** PATCH /api/interviews/{id} — officer/recruiter reschedules an interview. */
    @PatchMapping("/{interviewId}")
    public InterviewResponse rescheduleInterview(
        @PathVariable String interviewId,
        @Valid @RequestBody UpdateInterviewRequest request
    ) {
        return interviewService.reschedule(interviewId, request);
    }

    /** PATCH /api/interviews/{id}/cancel — officer/recruiter cancels an interview. */
    @PatchMapping("/{interviewId}/cancel")
    public InterviewResponse cancelInterview(@PathVariable String interviewId) {
        return interviewService.cancel(interviewId);
    }

    @PostMapping("/{interviewId}/result")
    public InterviewResponse recordResult(
        @PathVariable String interviewId,
        @Valid @RequestBody RecordResultRequest request
    ) {
        return interviewService.recordResult(interviewId, request);
    }
}
