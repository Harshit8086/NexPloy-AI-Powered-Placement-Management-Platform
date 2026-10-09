package com.nexploy.campuslink.student.controller;

import com.nexploy.campuslink.student.dto.SkillEvidenceRequest;
import com.nexploy.campuslink.student.dto.SkillLevelRequest;
import com.nexploy.campuslink.student.dto.SkillUpsertRequest;
import com.nexploy.campuslink.student.dto.StudentSkillResponse;
import com.nexploy.campuslink.student.service.StudentSkillService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service skill management for the signed-in student. All operations resolve the student by
 * email (== username), so a student can only ever read or change their own skills.
 */
@RestController
@RequestMapping("/api/students/me/skills")
@RequiredArgsConstructor
public class StudentSkillController {

    private final StudentSkillService studentSkillService;

    @GetMapping
    public List<StudentSkillResponse> listSkills() {
        return studentSkillService.listOwnSkills().stream()
            .map(StudentSkillResponse::from).toList();
    }

    /** Adds a skill, or updates the proficiency/source of an existing one (keyed on skill label). */
    @PutMapping
    public StudentSkillResponse upsertSkill(@Valid @RequestBody SkillUpsertRequest request) {
        return StudentSkillResponse.from(studentSkillService.upsertOwnSkill(request));
    }

    /** Attaches a proof link to a skill and promotes it to EVIDENCED (unless already VERIFIED). */
    @PutMapping("/{skillId}/evidence")
    public StudentSkillResponse attachEvidence(
        @PathVariable String skillId,
        @Valid @RequestBody SkillEvidenceRequest request
    ) {
        return StudentSkillResponse.from(
            studentSkillService.attachEvidence(skillId, request.url(), request.note()));
    }

    /** Small self-correction of a VERIFIED skill's level; large jumps are refused and need a re-viva. */
    @PutMapping("/{skillId}/level")
    public StudentSkillResponse adjustVerifiedLevel(
        @PathVariable String skillId,
        @Valid @RequestBody SkillLevelRequest request
    ) {
        return StudentSkillResponse.from(
            studentSkillService.adjustVerifiedLevel(skillId, request.level()));
    }

    @DeleteMapping("/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable String skillId) {
        studentSkillService.deleteOwnSkill(skillId);
    }
}
