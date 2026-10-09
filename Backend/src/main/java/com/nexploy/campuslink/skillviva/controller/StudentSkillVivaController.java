package com.nexploy.campuslink.skillviva.controller;

import com.nexploy.campuslink.skillviva.dto.StartVivaResponse;
import com.nexploy.campuslink.skillviva.dto.SubmitVivaRequest;
import com.nexploy.campuslink.skillviva.dto.SubmitVivaResponse;
import com.nexploy.campuslink.skillviva.service.SkillVivaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Skill Viva for the signed-in student. Ownership of the skill is enforced in the service (the
 * student is resolved by email), so a student can only run a viva for their own skill. A passed
 * viva promotes that skill to VERIFIED.
 */
@RestController
@RequestMapping("/api/students/me/skills/{skillId}/viva")
@RequiredArgsConstructor
public class StudentSkillVivaController {

    private final SkillVivaService skillVivaService;

    /** Starts a viva for one owned skill and returns the generated questions. */
    @PostMapping("/start")
    public StartVivaResponse start(@PathVariable String skillId) {
        return skillVivaService.start(skillId);
    }

    /** Submits the answered questions; on a pass, the skill becomes VERIFIED. */
    @PostMapping("/submit")
    public SubmitVivaResponse submit(
        @PathVariable String skillId,
        @Valid @RequestBody SubmitVivaRequest request
    ) {
        return skillVivaService.submit(skillId, request);
    }
}
