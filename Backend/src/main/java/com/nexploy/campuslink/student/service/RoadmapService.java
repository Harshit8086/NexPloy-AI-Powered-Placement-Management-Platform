package com.nexploy.campuslink.student.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.repository.DriveSkillRequirementRepository;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse;
import com.nexploy.campuslink.student.dto.RoadmapProjectRequest;
import com.nexploy.campuslink.student.dto.RoadmapResponse;
import com.nexploy.campuslink.student.dto.WhatIfFactorResponse;
import com.nexploy.campuslink.student.entity.CareerRoadmap;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.CareerRoadmapRepository;
import com.nexploy.campuslink.student.repository.ResumeAnalysisRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import com.nexploy.campuslink.student.service.RoadmapCatalog.PhaseTemplate;
import com.nexploy.campuslink.student.service.RoadmapCatalog.TaskTemplate;
import com.nexploy.campuslink.student.service.StoredRoadmap.StoredPhase;
import com.nexploy.campuslink.student.service.StoredRoadmap.StoredTask;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in student's Career Roadmap (Phase C). Materializes a personalized, gap-driven plan from
 * the curated {@link RoadmapCatalog} on first view, persists it per student, and lets the student
 * complete tasks and add portfolio projects — all grounded, never fabricated:
 *
 * <ul>
 *   <li><b>Structure</b> comes from the version-controlled catalog, chosen by the student's target
 *       role; a role with no curated track gets its phases auto-built from the officer-managed drive
 *       skill requirements instead, so a new role needs no code change. No AI invents phases or
 *       tasks.</li>
 *   <li><b>Seeding</b> pre-completes a task when its skill is already demonstrated (union of stored
 *       strengths, skills rated &ge; {@value #DEMONSTRATED_PROFICIENCY}, and the latest persisted
 *       resume analysis' DEMONSTRATED skill-match), so the roadmap never asks a student to relearn
 *       what they can already do.</li>
 *   <li><b>Progress</b> is computed from real task status ({@code done/total}); phase status follows
 *       from its tasks; nothing here is a made-up percentage.</li>
 * </ul>
 *
 * <p>Independent of {@code StudentGoal}/What-If. All entry points are resolved by the caller to the
 * signed-in student, so a student only ever reads or mutates their own roadmap.
 */
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private static final Logger log = LoggerFactory.getLogger(RoadmapService.class);

    /** A self-rated skill at or above this level counts as demonstrated for roadmap seeding. */
    private static final int DEMONSTRATED_PROFICIENCY = 60;

    /** The phase that holds student-added portfolio projects; created on first add. */
    private static final String PROJECTS_PHASE_KEY = "projects";
    private static final String PROJECTS_PHASE_TITLE = "Portfolio Projects";
    private static final String PROJECTS_PHASE_RATIONALE =
        "Projects you have committed to building to demonstrate the skills above.";

    private static final int MAX_RECOMMENDED_PROJECTS = 4;

    private final CareerRoadmapRepository careerRoadmapRepository;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final DriveRepository driveRepository;
    private final DriveSkillRequirementRepository driveSkillRequirementRepository;
    private final RoadmapCatalog roadmapCatalog;
    private final ObjectMapper objectMapper;

    /** The student's roadmap, materializing (and persisting) it from the catalog on first view. */
    public RoadmapResponse getRoadmap(Student student) {
        return assemble(read(loadOrCreate(student)));
    }

    /**
     * Dynamically generates the What-If simulation factors grounded in the student's own Career
     * Roadmap. Unfinished roadmap tasks (status == PLANNED) are presented as high-impact growth
     * options, complemented with standard practice milestones (DSA, Mock Interviews, Portfolio).
     */
    public List<WhatIfFactorResponse> getWhatIfFactors(Student student) {
        CareerRoadmap entity = loadOrCreate(student);
        StoredRoadmap current = read(entity);

        List<WhatIfFactorResponse> factors = new ArrayList<>();
        Set<String> seenSkills = new LinkedHashSet<>();

        for (StoredPhase phase : current.phases()) {
            int defaultWeight = "core".equalsIgnoreCase(phase.key()) ? 5
                : "build".equalsIgnoreCase(phase.key()) ? 4
                : 3;

            for (StoredTask task : phase.tasks()) {
                if ("PLANNED".equalsIgnoreCase(task.status())) {
                    String skillKey = task.skill() != null ? task.skill().toLowerCase(Locale.ROOT) : task.id();
                    if (!seenSkills.contains(skillKey)) {
                        seenSkills.add(skillKey);
                        factors.add(new WhatIfFactorResponse(
                            task.id(),
                            skillKey,
                            task.title(),
                            "ROADMAP_SKILL",
                            defaultWeight,
                            task.detail(),
                            task.addressesGap() != null
                        ));
                    }
                }
            }
        }

        // Determine branch and domain context
        String branch = ((student.getBranch() != null ? student.getBranch() : "") + " "
            + (student.getBranchCode() != null ? student.getBranchCode() : "")).trim().toUpperCase(Locale.ROOT);
        String targetRole = student.getTargetRole() != null ? student.getTargetRole().toLowerCase(Locale.ROOT) : "";

        boolean isSoftwareTarget = targetRole.contains("software") || targetRole.contains("developer")
            || targetRole.contains("frontend") || targetRole.contains("backend") || targetRole.contains("full stack")
            || targetRole.contains("sde") || targetRole.contains("devops") || targetRole.contains("data")
            || targetRole.contains("programmer");

        String practiceKey = "dsa";
        String practiceLabel = "Improve DSA (+30 Problems)";
        String practiceDetail = "Target data structures and algorithmic patterns required for technical rounds.";

        String projectKey = "newProject";
        String projectLabel = "Build 1 Portfolio Project";
        String projectDetail = "Develop and deploy an end-to-end full stack application or project.";

        if (!isSoftwareTarget) {
            if (branch.contains("CIVIL") || branch.contains(" CE") || branch.startsWith("CE") || targetRole.contains("civil")) {
                practiceKey = "civilPractice";
                practiceLabel = "Core Civil & Structural Aptitude (+30 Questions)";
                practiceDetail = "AutoCAD, structural mechanics, surveying, and site engineering practice.";
                projectKey = "civilProject";
                projectLabel = "Build 1 Structural / CAD Design Project";
                projectDetail = "Structural design, site estimate, or BIM/CAD planning model.";
            } else if (branch.contains("ELECTRICAL") || branch.contains(" EEE") || branch.contains(" EE")
                || branch.startsWith("EE") || branch.startsWith("EEE") || targetRole.contains("electrical")) {
                practiceKey = "electricalPractice";
                practiceLabel = "Core Electrical & Circuit Aptitude (+30 Questions)";
                practiceDetail = "Power systems, circuit analysis, control systems, and machines problem solving.";
                projectKey = "electricalProject";
                projectLabel = "Build 1 Power / Circuit Design Project";
                projectDetail = "Design and simulate an electrical drive, circuit schematic, or power converter.";
            } else if (branch.contains("MECH") || branch.contains(" ME") || branch.startsWith("ME") || targetRole.contains("mechanical")) {
                practiceKey = "mechanicalPractice";
                practiceLabel = "Core Mechanical & Thermal Aptitude (+30 Questions)";
                practiceDetail = "Thermodynamics, fluid mechanics, SOM, and manufacturing problem sets.";
                projectKey = "mechanicalProject";
                projectLabel = "Build 1 CAD / FEA Simulation Project";
                projectDetail = "SolidWorks/CATIA 3D component model, kinematic analysis, or thermal simulation.";
            } else if (branch.contains("ECE") || branch.contains("ELECTRONIC") || targetRole.contains("electronics")) {
                practiceKey = "ecePractice";
                practiceLabel = "Embedded C & Digital Electronics (+30 Problems)";
                practiceDetail = "Digital logic design, microcontrollers, and signal processing problem sets.";
                projectKey = "eceProject";
                projectLabel = "Build 1 Embedded / IoT Hardware Project";
                projectDetail = "Microcontroller firmware, PCB design, or IoT prototype with sensors.";
            }
        }

        factors.add(new WhatIfFactorResponse(
            practiceKey,
            practiceKey,
            practiceLabel,
            "PRACTICE",
            4,
            practiceDetail,
            false
        ));
        factors.add(new WhatIfFactorResponse(
            "mockInterviews",
            "mockInterviews",
            "3 Mock Interviews",
            "INTERVIEW",
            5,
            "Conduct structured technical & behavioral rounds with senior mentors.",
            false
        ));
        factors.add(new WhatIfFactorResponse(
            projectKey,
            projectKey,
            projectLabel,
            "PROJECT",
            4,
            projectDetail,
            false
        ));
        factors.add(new WhatIfFactorResponse(
            "communication",
            "communication",
            "Improve Communication",
            "PRACTICE",
            3,
            "Master structured articulation, STAR method responses, and soft skills.",
            false
        ));

        return factors;
    }

    /**
     * Sets one task's status (PLANNED/DONE) and returns the recomputed roadmap. An unknown task id is
     * a client error (400). The task is addressed within the student's own roadmap only.
     */
    @Transactional
    public RoadmapResponse toggleTask(Student student, String taskId, String status) {
        CareerRoadmap entity = loadOrCreate(student);
        StoredRoadmap current = read(entity);

        boolean found = false;
        List<StoredPhase> phases = new ArrayList<>();
        for (StoredPhase phase : current.phases()) {
            List<StoredTask> tasks = new ArrayList<>();
            for (StoredTask task : phase.tasks()) {
                if (task.id().equals(taskId)) {
                    found = true;
                    tasks.add(new StoredTask(task.id(), task.title(), task.detail(), task.skill(),
                        task.kind(), task.source(), status, task.addressesGap()));
                } else {
                    tasks.add(task);
                }
            }
            phases.add(new StoredPhase(phase.key(), phase.title(), phase.rationale(), tasks));
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown roadmap task: " + taskId);
        }
        return save(entity, new StoredRoadmap(current.targetRole(), phases));
    }

    /**
     * Adds a portfolio project to the roadmap (idempotent — a project whose title already exists is
     * ignored) and returns the recomputed roadmap. Used by both the roadmap's recommended-project
     * cards and the resume analysis "Add to Roadmap" action.
     */
    @Transactional
    public RoadmapResponse addProject(Student student, RoadmapProjectRequest request) {
        CareerRoadmap entity = loadOrCreate(student);
        StoredRoadmap current = read(entity);

        boolean duplicate = current.phases().stream()
            .flatMap(p -> p.tasks().stream())
            .anyMatch(t -> "PROJECT".equals(t.kind()) && t.title().equalsIgnoreCase(request.name().trim()));
        if (duplicate) {
            return assemble(current);
        }

        String skill = (request.skillsDemonstrated() != null && !request.skillsDemonstrated().isEmpty())
            ? request.skillsDemonstrated().get(0)
            : request.addressesGap();
        StoredTask project = new StoredTask(
            "task_" + UUID.randomUUID(),
            request.name().trim(),
            request.description(),
            skill,
            "PROJECT",
            "RESUME_SUGGESTION",
            "PLANNED",
            request.addressesGap());

        List<StoredPhase> phases = new ArrayList<>(current.phases());
        int projectsIdx = indexOfPhase(phases, PROJECTS_PHASE_KEY);
        if (projectsIdx >= 0) {
            StoredPhase existing = phases.get(projectsIdx);
            List<StoredTask> tasks = new ArrayList<>(existing.tasks());
            tasks.add(project);
            phases.set(projectsIdx, new StoredPhase(existing.key(), existing.title(), existing.rationale(), tasks));
        } else {
            phases.add(new StoredPhase(PROJECTS_PHASE_KEY, PROJECTS_PHASE_TITLE, PROJECTS_PHASE_RATIONALE,
                new ArrayList<>(List.of(project))));
        }
        return save(entity, new StoredRoadmap(current.targetRole(), phases));
    }

    /**
     * Rebuilds the roadmap's catalog structure from the (possibly changed) target role and the
     * student's latest demonstrated skills, while preserving progress: a task stays DONE if it was
     * completed before or its skill is now demonstrated, and student-added portfolio projects are
     * carried over untouched. This is how newly-recorded skills flip pending gaps to done without
     * discarding manual completions. Never fabricates — structure is still catalog-driven.
     */
    @Transactional
    public RoadmapResponse regenerate(Student student) {
        CareerRoadmap entity = loadOrCreate(student);
        StoredRoadmap current = read(entity);
        StoredRoadmap fresh = generate(student);

        // Prior catalog tasks, keyed by phase + title (stable across regenerations, unlike the
        // per-task random ids). We reuse both the prior id (so a task the student toggled keeps its
        // identity for the client) and the prior status. Projects are handled separately below.
        Map<String, StoredTask> priorTasks = new HashMap<>();
        for (StoredPhase phase : current.phases()) {
            for (StoredTask task : phase.tasks()) {
                if ("TASK".equals(task.kind())) {
                    priorTasks.put(taskKey(phase.key(), task.title()), task);
                }
            }
        }

        List<StoredPhase> merged = new ArrayList<>();
        for (StoredPhase phase : fresh.phases()) {
            List<StoredTask> tasks = new ArrayList<>();
            for (StoredTask task : phase.tasks()) {
                StoredTask prior = priorTasks.get(taskKey(phase.key(), task.title()));
                // Keep it DONE if the student already finished it, or if it is now demonstrated
                // (the fresh seed sets DONE for demonstrated skills). Otherwise it is still pending.
                String status = (prior != null && "DONE".equals(prior.status())) || "DONE".equals(task.status())
                    ? "DONE" : "PLANNED";
                // Preserve the prior id where the task carried over, so client references stay valid.
                String id = prior != null ? prior.id() : task.id();
                tasks.add(new StoredTask(id, task.title(), task.detail(), task.skill(),
                    task.kind(), task.source(), status, task.addressesGap()));
            }
            merged.add(new StoredPhase(phase.key(), phase.title(), phase.rationale(), tasks));
        }

        // Student-added portfolio projects are not part of the catalog — carry them over as-is.
        int projectsIdx = indexOfPhase(current.phases(), PROJECTS_PHASE_KEY);
        if (projectsIdx >= 0) {
            merged.add(current.phases().get(projectsIdx));
        }

        return save(entity, new StoredRoadmap(student.getTargetRole(), merged));
    }

    /** Stable identity for a catalog task across regenerations: its phase key + lower-cased title. */
    private String taskKey(String phaseKey, String title) {
        return phaseKey + '\u0000' + title.toLowerCase(Locale.ROOT);
    }

    // ---- Materialization -------------------------------------------------------------------------
    /**
     * Loads the student's roadmap, generating and persisting it from the catalog if none exists. Two
     * concurrent first views could both find nothing and both try to insert; the loser hits the
     * {@code unique(student_id)} constraint, so we catch that and re-read the row the winner wrote
     * rather than surfacing a 500.
     */
    private CareerRoadmap loadOrCreate(Student student) {
        return careerRoadmapRepository.findByStudentId(student.getId())
            .orElseGet(() -> {
                try {
                    return persistNew(student, generate(student));
                } catch (DataIntegrityViolationException race) {
                    return careerRoadmapRepository.findByStudentId(student.getId())
                        .orElseThrow(() -> race);
                }
            });
    }

    /** Builds a fresh roadmap from the catalog, pre-completing already-demonstrated skills. */
    private StoredRoadmap generate(Student student) {
        Set<String> demonstrated = demonstratedSkills(student);
        List<StoredPhase> phases = new ArrayList<>();
        for (PhaseTemplate phase : phasesForStudent(student)) {
            List<StoredTask> tasks = new ArrayList<>();
            for (TaskTemplate task : phase.tasks()) {
                boolean done = task.skill() != null
                    && demonstrated.contains(task.skill().toLowerCase(Locale.ROOT));
                tasks.add(new StoredTask(
                    "task_" + UUID.randomUUID(),
                    task.title(), task.detail(), task.skill(),
                    "TASK", "CATALOG", done ? "DONE" : "PLANNED", task.skill()));
            }
            phases.add(new StoredPhase(phase.key(), phase.title(), phase.rationale(), tasks));
        }
        return new StoredRoadmap(student.getTargetRole(), phases);
    }

    /**
     * The phase structure for a student's roadmap: the curated catalog track when the target role
     * matches a known family; otherwise phases auto-built from the officer-managed drive skill
     * requirements; and only when neither applies, the catalog's generic track.
     */
    private List<PhaseTemplate> phasesForStudent(Student student) {
        List<PhaseTemplate> curated = roadmapCatalog.curatedPhasesFor(student.getTargetRole());
        if (curated != null) {
            return curated;
        }
        List<PhaseTemplate> driveBacked = driveBackedPhases(student);
        if (!driveBacked.isEmpty()) {
            return driveBacked;
        }

        // Branch-aware catalog fallback for core engineering students
        String branch = ((student.getBranch() != null ? student.getBranch() : "") + " "
            + (student.getBranchCode() != null ? student.getBranchCode() : "")).trim().toUpperCase(Locale.ROOT);
        if (branch.contains("CIVIL") || branch.contains(" CE") || branch.startsWith("CE")) {
            return roadmapCatalog.civil();
        } else if (branch.contains("ELECTRICAL") || branch.contains(" EEE") || branch.contains(" EE")
            || branch.startsWith("EE") || branch.startsWith("EEE")) {
            return roadmapCatalog.electrical();
        } else if (branch.contains("MECH") || branch.contains(" ME") || branch.startsWith("ME")) {
            return roadmapCatalog.mechanical();
        }

        return roadmapCatalog.phasesFor(student.getTargetRole());
    }

    /**
     * Auto-builds roadmap phases from the approved skill requirements of the drives on the student's
     * campus whose role matches the student's target role, bucketed by officer-assigned weight:
     * weight &ge; 3 -> Core, weight 2 -> Build, weight &le; 1 -> Stretch. One task per required skill
     * keeps the skill label, so demonstrated-skill seeding and recommended projects work unchanged.
     * Empty when no matching drive has approved requirements. An officer re-weighting a requirement
     * moves that task between phase buckets on the next regenerate.
     */
    private List<PhaseTemplate> driveBackedPhases(Student student) {
        String targetRole = student.getTargetRole();
        if (targetRole == null || targetRole.isBlank() || student.getCampusId() == null) {
            return List.of();
        }
        for (Drive drive : driveRepository.findByCampusId(student.getCampusId())) {
            if (drive.getRole() == null || !targetRole.trim().equalsIgnoreCase(drive.getRole().trim())) {
                continue;
            }
            List<DriveSkillRequirement> requirements = driveSkillRequirementRepository
                .findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc(drive.getId());
            if (requirements.isEmpty()) {
                continue;
            }
            List<TaskTemplate> core = new ArrayList<>();
            List<TaskTemplate> build = new ArrayList<>();
            List<TaskTemplate> stretch = new ArrayList<>();
            for (DriveSkillRequirement req : requirements) {
                TaskTemplate task = new TaskTemplate(
                    "Master " + req.getSkill(),
                    "Reach level " + req.getRequiredLevel() + " in " + req.getSkill()
                        + " — required for " + drive.getRole() + ".",
                    req.getSkill());
                int weight = req.getWeight() == null ? 1 : req.getWeight();
                if (weight >= 3) {
                    core.add(task);
                } else if (weight == 2) {
                    build.add(task);
                } else {
                    stretch.add(task);
                }
            }
            List<PhaseTemplate> phases = new ArrayList<>();
            if (!core.isEmpty()) {
                phases.add(new PhaseTemplate("core", "Core requirements",
                    "The highest-weighted skills for " + targetRole + " — master these first.", core));
            }
            if (!build.isEmpty()) {
                phases.add(new PhaseTemplate("build", "Build on the core",
                    "Medium-weight skills that round out the role once the core is solid.", build));
            }
            if (!stretch.isEmpty()) {
                phases.add(new PhaseTemplate("stretch", "Stretch skills",
                    "Lower-weight skills that differentiate you once the essentials are done.", stretch));
            }
            return phases;
        }
        return List.of();
    }

    /**
     * The skills the student has already demonstrated (lower-cased), from authoritative stored data:
     * recorded strengths, self-rated skills at or above the threshold, and the DEMONSTRATED entries of
     * their most recent persisted resume analysis. Never invents ability.
     */
    private Set<String> demonstratedSkills(Student student) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : readStringList(student.getStrengthsJson())) {
            out.add(s.toLowerCase(Locale.ROOT));
        }
        studentSkillRepository.findByStudentIdOrderBySkillAsc(student.getId()).stream()
            .filter(s -> s.getProficiency() != null && s.getProficiency() >= DEMONSTRATED_PROFICIENCY)
            .forEach(s -> out.add(s.getSkill().toLowerCase(Locale.ROOT)));
        resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(student.getId())
            .ifPresent(latest -> latestDemonstrated(latest.getAnalysisJson()).forEach(
                s -> out.add(s.toLowerCase(Locale.ROOT))));
        return out;
    }

    /** DEMONSTRATED skill labels from a stored resume analysis JSON; empty if it cannot be parsed. */
    private List<String> latestDemonstrated(String analysisJson) {
        if (analysisJson == null || analysisJson.isBlank()) {
            return List.of();
        }
        try {
            ResumeAnalysisResponse analysis = objectMapper.readValue(analysisJson, ResumeAnalysisResponse.class);
            if (analysis.skillMatch() == null) {
                return List.of();
            }
            return analysis.skillMatch().stream()
                .filter(m -> "DEMONSTRATED".equals(m.status()))
                .map(ResumeAnalysisResponse.SkillMatch::requiredSkill)
                .toList();
        } catch (JsonProcessingException e) {
            log.warn("Could not parse stored resume analysis for roadmap seeding: {}", e.toString());
            return List.of();
        }
    }

    // ---- Assembly (derived, never stored) --------------------------------------------------------

    /** Computes the API view — phase/overall progress, statuses, current focus, recommended projects. */
    private RoadmapResponse assemble(StoredRoadmap stored) {
        List<RoadmapResponse.Phase> phases = new ArrayList<>();
        int totalTasks = 0;
        int doneTasks = 0;
        int projectCount = 0;
        boolean currentAssigned = false;
        RoadmapResponse.CurrentFocus focus = null;

        for (StoredPhase phase : stored.phases()) {
            int phaseTotal = phase.tasks().size();
            int phaseDone = (int) phase.tasks().stream().filter(t -> "DONE".equals(t.status())).count();
            projectCount += (int) phase.tasks().stream().filter(t -> "PROJECT".equals(t.kind())).count();
            totalTasks += phaseTotal;
            doneTasks += phaseDone;

            boolean phaseComplete = phaseTotal > 0 && phaseDone == phaseTotal;
            String status;
            if (phaseComplete) {
                status = "DONE";
            } else if (!currentAssigned) {
                status = "CURRENT";
                currentAssigned = true;
                focus = new RoadmapResponse.CurrentFocus(
                    phase.key(), phase.title(), phase.rationale(), phaseDone, phaseTotal);
            } else {
                status = "UPCOMING";
            }
            int phaseProgress = phaseTotal == 0 ? 0 : (int) Math.round(100.0 * phaseDone / phaseTotal);

            List<RoadmapResponse.Task> tasks = phase.tasks().stream()
                .map(t -> new RoadmapResponse.Task(t.id(), t.title(), t.detail(), t.skill(),
                    t.kind(), t.source(), t.status(), t.addressesGap()))
                .toList();
            phases.add(new RoadmapResponse.Phase(
                phase.key(), phase.title(), phase.rationale(), status, phaseProgress, tasks));
        }

        int overall = totalTasks == 0 ? 0 : (int) Math.round(100.0 * doneTasks / totalTasks);
        return new RoadmapResponse(
            stored.targetRole(), overall, stored.phases().size(), totalTasks, doneTasks, projectCount,
            focus, phases, recommendedProjects(stored));
    }

    /**
     * Projects the student could add, one per still-pending catalog skill (deduped, capped). Each is
     * grounded in a real gap on the roadmap; {@code alreadyAdded} reflects whether a matching PROJECT
     * task already exists so the UI can disable the button.
     */
    private List<RoadmapResponse.RecommendedProject> recommendedProjects(StoredRoadmap stored) {
        Set<String> existingProjectTitles = new LinkedHashSet<>();
        stored.phases().stream().flatMap(p -> p.tasks().stream())
            .filter(t -> "PROJECT".equals(t.kind()))
            .forEach(t -> existingProjectTitles.add(t.title().toLowerCase(Locale.ROOT)));

        List<RoadmapResponse.RecommendedProject> out = new ArrayList<>();
        Set<String> seenSkills = new LinkedHashSet<>();
        for (StoredPhase phase : stored.phases()) {
            for (StoredTask task : phase.tasks()) {
                if (!"TASK".equals(task.kind()) || !"PLANNED".equals(task.status())) {
                    continue;
                }
                String skill = task.skill();
                if (skill == null || !seenSkills.add(skill.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String name = skill + " Showcase Project";
                out.add(new RoadmapResponse.RecommendedProject(
                    "rec_" + skill.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"),
                    name,
                    "Build a focused project that demonstrates " + skill + " end-to-end.",
                    skill,
                    List.of(skill),
                    "Intermediate",
                    existingProjectTitles.contains(name.toLowerCase(Locale.ROOT))));
                if (out.size() >= MAX_RECOMMENDED_PROJECTS) {
                    return out;
                }
            }
        }
        return out;
    }

    // ---- Persistence helpers ---------------------------------------------------------------------

    private CareerRoadmap persistNew(Student student, StoredRoadmap roadmap) {
        OffsetDateTime now = OffsetDateTime.now();
        CareerRoadmap entity = CareerRoadmap.builder()
            .id("career_roadmap_" + UUID.randomUUID())
            .studentId(student.getId())
            .campusId(student.getCampusId())
            .targetRole(student.getTargetRole())
            .roadmapJson(write(roadmap))
            .createdAt(now)
            .updatedAt(now)
            .build();
        return careerRoadmapRepository.save(entity);
    }

    private RoadmapResponse save(CareerRoadmap entity, StoredRoadmap roadmap) {
        entity.setRoadmapJson(write(roadmap));
        entity.setUpdatedAt(OffsetDateTime.now());
        careerRoadmapRepository.save(entity);
        return assemble(roadmap);
    }

    private StoredRoadmap read(CareerRoadmap entity) {
        try {
            return objectMapper.readValue(entity.getRoadmapJson(), StoredRoadmap.class);
        } catch (JsonProcessingException e) {
            // Our own serialized structure should always parse; treat corruption as an empty roadmap
            // rather than a 500 so the student can still get a fresh one on next materialization.
            throw new IllegalStateException("Stored roadmap for student " + entity.getStudentId()
                + " could not be parsed", e);
        }
    }

    private String write(StoredRoadmap roadmap) {
        try {
            return objectMapper.writeValueAsString(roadmap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize roadmap", e);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private int indexOfPhase(List<StoredPhase> phases, String key) {
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
