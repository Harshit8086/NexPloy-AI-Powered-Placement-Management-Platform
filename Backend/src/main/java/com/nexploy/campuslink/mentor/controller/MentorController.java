package com.nexploy.campuslink.mentor.controller;

import com.nexploy.campuslink.mentor.dto.CreateAssignmentRequest;
import com.nexploy.campuslink.mentor.dto.CreateMentorRequest;
import com.nexploy.campuslink.mentor.dto.CreateNoteRequest;
import com.nexploy.campuslink.mentor.dto.MentorAccountResponse;
import com.nexploy.campuslink.mentor.dto.MentorAssignmentResponse;
import com.nexploy.campuslink.mentor.dto.MentorNoteResponse;
import com.nexploy.campuslink.mentor.service.MentorService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor")
@RequiredArgsConstructor
public class MentorController {

    private final MentorService mentorService;

    @GetMapping("/assignments")
    public List<MentorAssignmentResponse> listAssignments(@RequestParam(required = false) String mentorUsername) {
        return mentorService.listAssignments(mentorUsername);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public MentorAssignmentResponse assign(@Valid @RequestBody CreateAssignmentRequest request) {
        return mentorService.assign(request);
    }

    @GetMapping("/accounts")
    public List<MentorAccountResponse> listMentors(@RequestParam(required = false) String campusId) {
        return mentorService.listMentors(campusId);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public MentorAccountResponse createMentor(@Valid @RequestBody CreateMentorRequest request) {
        return mentorService.createMentorAccount(request);
    }

    @GetMapping("/students/{studentId}/notes")
    public List<MentorNoteResponse> listNotes(@PathVariable String studentId) {
        return mentorService.listNotes(studentId);
    }

    @PostMapping("/students/{studentId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public MentorNoteResponse addNote(
        @PathVariable String studentId,
        @Valid @RequestBody CreateNoteRequest request
    ) {
        return mentorService.addNote(studentId, request);
    }
}
