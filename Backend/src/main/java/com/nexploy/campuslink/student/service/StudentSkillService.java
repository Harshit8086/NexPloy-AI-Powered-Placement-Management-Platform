package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.student.dto.SkillUpsertRequest;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.exception.StudentSkillNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service management of the signed-in student's skill set. The student is resolved by email
 * (== username), so a student only ever reads or mutates their own rows. Upsert is keyed on the
 * skill label (case-insensitive) so re-submitting a skill updates its proficiency in place rather
 * than creating a duplicate.
 */
@Service
@RequiredArgsConstructor
public class StudentSkillService {

    private static final String DEFAULT_SOURCE = "SELF_REPORTED";
    private static final String STATUS_SELF_DECLARED = "SELF_DECLARED";
    private static final String STATUS_EVIDENCED = "EVIDENCED";
    private static final String STATUS_VERIFIED = "VERIFIED";

    /** How far a VERIFIED skill's level may be nudged directly (in points) before a re-test is required. */
    private static final int VERIFIED_ADJUSTMENT_RANGE = 15;

    private final StudentRepository studentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final CampusAccessService campusAccessService;

    @Transactional(readOnly = true)
    public List<StudentSkill> listOwnSkills() {
        Student student = currentStudent();
        return studentSkillRepository.findByStudentIdOrderBySkillAsc(student.getId());
    }

    /**
     * Read another student's skill set for a placement-staff surface (e.g. the officer Student
     * Directory drawer). Only OFFICER/ADMIN/MENTOR may read a student they don't own, and only
     * within campus reach — students and recruiters have no cross-profile skill read here.
     */
    @Transactional(readOnly = true)
    public List<StudentSkill> listSkillsForStudent(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != com.nexploy.campuslink.auth.entity.UserRole.OFFICER
            && user.getRole() != com.nexploy.campuslink.auth.entity.UserRole.ADMIN
            && user.getRole() != com.nexploy.campuslink.auth.entity.UserRole.MENTOR) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Only placement staff may read another student's skills");
        }
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return studentSkillRepository.findByStudentIdOrderBySkillAsc(student.getId());
    }

    @Transactional
    public StudentSkill upsertOwnSkill(SkillUpsertRequest request) {
        Student student = currentStudent();
        String source = (request.source() == null || request.source().isBlank())
            ? DEFAULT_SOURCE
            : request.source();

        StudentSkill skill = studentSkillRepository
            .findByStudentIdAndSkillIgnoreCase(student.getId(), request.skill())
            .orElseGet(() -> StudentSkill.builder()
                .id("sk_" + UUID.randomUUID())
                .studentId(student.getId())
                .campusId(student.getCampusId())
                .skill(request.skill())
                .verificationStatus(STATUS_SELF_DECLARED)
                .build());

        // On update, keep the existing id/label AND verification status (re-rating proficiency must
        // not reset an earned EVIDENCED/VERIFIED badge); refresh the mutable fields only.
        skill.setProficiency(request.proficiency());
        skill.setSource(source);
        skill.setUpdatedAt(OffsetDateTime.now());
        return studentSkillRepository.save(skill);
    }

    /**
     * Self-adjusts the proficiency of an already-VERIFIED skill without re-testing (a verified badge
     * means the level was demonstrated once, and small corrections up or down are allowed to stand).
     * Self-declared/evidenced skills have no such leeway — their level is self-reported anyway, and
     * a big jump should come from a viva.
     */
    @Transactional
    public StudentSkill adjustVerifiedLevel(String skillId, int newLevel) {
        Student student = currentStudent();
        StudentSkill skill = ownedSkill(student, skillId);
        if (!STATUS_VERIFIED.equals(skill.getVerificationStatus())) {
            throw new IllegalArgumentException(
                "Only a verified skill's level can be adjusted directly — verify it first or update the self-rated level");
        }
        int bounded = Math.max(0, Math.min(100, newLevel));
        int current = skill.getProficiency() == null ? 0 : skill.getProficiency();
        if (Math.abs(bounded - current) > VERIFIED_ADJUSTMENT_RANGE) {
            throw new IllegalArgumentException(
                "A change this large needs a re-test (viva) to keep the verified level trustworthy");
        }
        skill.setProficiency(bounded);
        skill.setUpdatedAt(OffsetDateTime.now());
        return studentSkillRepository.save(skill);
    }

    /**
     * Attaches a proof link (https) to one of the student's own skills and promotes it to EVIDENCED,
     * unless it is already VERIFIED (a stronger status, never downgraded).
     */
    @Transactional
    public StudentSkill attachEvidence(String skillId, String url, String note) {
        if (url == null || !url.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            throw new IllegalArgumentException("Evidence link must be an https URL");
        }
        Student student = currentStudent();
        StudentSkill skill = ownedSkill(student, skillId);
        skill.setEvidenceUrl(url);
        skill.setEvidenceNote(note);
        if (!STATUS_VERIFIED.equals(skill.getVerificationStatus())) {
            skill.setVerificationStatus(STATUS_EVIDENCED);
        }
        skill.setUpdatedAt(OffsetDateTime.now());
        return studentSkillRepository.save(skill);
    }

    /** Loads one of the signed-in student's own skills (404 if not theirs). Used by the skill viva. */
    @Transactional(readOnly = true)
    public StudentSkill getOwnedSkill(String skillId) {
        return ownedSkill(currentStudent(), skillId);
    }

    /**
     * Records a skill viva result: recalibrates the stored proficiency to the measured level (so the
     * % reflects the student's real, demonstrated ability — up or down from what they claimed) and
     * marks the skill VERIFIED. The next viva then targets this stored level.
     */
    @Transactional
    public StudentSkill recordVivaLevel(String skillId, int achievedLevel) {
        Student student = currentStudent();
        StudentSkill skill = ownedSkill(student, skillId);
        skill.setProficiency(Math.max(0, Math.min(100, achievedLevel)));
        skill.setVerificationStatus(STATUS_VERIFIED);
        skill.setUpdatedAt(OffsetDateTime.now());
        return studentSkillRepository.save(skill);
    }

    private StudentSkill ownedSkill(Student student, String skillId) {
        return studentSkillRepository.findById(skillId)
            .filter(s -> s.getStudentId().equals(student.getId()))
            .orElseThrow(() -> new StudentSkillNotFoundException(skillId));
    }

    @Transactional
    public void deleteOwnSkill(String skillId) {
        Student student = currentStudent();
        StudentSkill skill = studentSkillRepository.findById(skillId)
            .filter(s -> s.getStudentId().equals(student.getId()))
            .orElseThrow(() -> new StudentSkillNotFoundException(skillId));
        studentSkillRepository.delete(skill);
    }

    /** Resolves the signed-in student's own record (username == email for STUDENT accounts). */
    private Student currentStudent() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        campusAccessService.requireAccess(user, student.getCampusId());
        return student;
    }
}
