package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.DriveService;
import com.nexploy.campuslink.student.dto.InterviewPrepResponse;
import com.nexploy.campuslink.student.dto.InterviewPrepResponse.TechnicalTopic;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic interview-preparation plans for the drives the student is in the pipeline for (has an
 * application to). For each drive, technical-topic coverage is the student's recorded proficiency in
 * that drive's required skills, and {@code preparationScore} is the mean of those coverages — so the
 * numbers are grounded in real stored data, never invented. Behavioural topics and likely questions
 * are rule-based text derived from the drive's role and required skills. A student with no
 * applications gets an empty list.
 */
@Service
@RequiredArgsConstructor
public class InterviewPrepService {

    private final ApplicationRepository applicationRepository;
    private final DriveRepository driveRepository;
    private final DriveService driveService;
    private final StudentSkillRepository studentSkillRepository;

    /** Standard behavioural interview themes — the same categories every candidate is asked about. */
    private static final List<String> BEHAVIOURAL_TOPICS = List.of(
        "Tell me about yourself",
        "A challenge you overcame in a project",
        "Working in a team / handling conflict",
        "Why this company and role");

    @Transactional(readOnly = true)
    public List<InterviewPrepResponse> plansFor(Student student) {
        Map<String, Integer> proficiency = proficiencyBySkill(student);

        List<Application> applications = applicationRepository.findByStudentId(student.getId());
        // Distinct drive ids, keeping first-seen order for stable output.
        List<String> driveIds = applications.stream()
            .map(Application::getDriveId).filter(id -> id != null).distinct().toList();

        List<InterviewPrepResponse> plans = new ArrayList<>();
        for (String driveId : driveIds) {
            Drive drive = driveRepository.findById(driveId).orElse(null);
            if (drive == null) continue;
            plans.add(plan(drive, proficiency));
        }

        // Least-prepared drives first so the student sees where to focus; ties break on drive id.
        plans.sort(Comparator
            .comparingInt(InterviewPrepResponse::preparationScore)
            .thenComparing(InterviewPrepResponse::driveId));
        return plans;
    }

    private InterviewPrepResponse plan(Drive drive, Map<String, Integer> proficiency) {
        List<String> requiredSkills = driveService.toResponse(drive).requiredSkills();

        List<TechnicalTopic> topics = new ArrayList<>();
        int coverageSum = 0;
        for (String skill : requiredSkills) {
            int coverage = proficiency.getOrDefault(skill.toLowerCase(Locale.ROOT), 0);
            topics.add(new TechnicalTopic(skill, coverage));
            coverageSum += coverage;
        }
        int preparationScore = topics.isEmpty() ? 0 : Math.round((float) coverageSum / topics.size());

        return new InterviewPrepResponse(
            drive.getId(),
            drive.getCompanyName(),
            drive.getRole(),
            preparationScore,
            topics,
            BEHAVIOURAL_TOPICS,
            likelyQuestions(drive, requiredSkills));
    }

    /** Rule-based likely questions grounded in this drive's role and required skills. */
    private List<String> likelyQuestions(Drive drive, List<String> requiredSkills) {
        List<String> questions = new ArrayList<>();
        String role = drive.getRole() == null ? "this role" : drive.getRole();
        String company = drive.getCompanyName() == null ? "this company" : drive.getCompanyName();
        questions.add("Why do you want the " + role + " role at " + company + "?");
        requiredSkills.stream().limit(3).forEach(skill ->
            questions.add("Walk through a project where you applied " + skill + "."));
        questions.add("Describe a time you had to learn a new technology quickly.");
        return questions;
    }

    private Map<String, Integer> proficiencyBySkill(Student student) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (StudentSkill skill : studentSkillRepository.findByStudentIdOrderBySkillAsc(student.getId())) {
            if (skill.getSkill() == null || skill.getSkill().isBlank()) continue;
            map.put(skill.getSkill().toLowerCase(Locale.ROOT),
                skill.getProficiency() == null ? 0 : skill.getProficiency());
        }
        return map;
    }
}
