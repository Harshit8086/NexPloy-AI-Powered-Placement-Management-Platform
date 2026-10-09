package com.nexploy.campuslink.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.repository.DriveSkillRequirementRepository;
import com.nexploy.campuslink.student.dto.RoadmapProjectRequest;
import com.nexploy.campuslink.student.dto.RoadmapResponse;
import com.nexploy.campuslink.student.entity.CareerRoadmap;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.CareerRoadmapRepository;
import com.nexploy.campuslink.student.repository.ResumeAnalysisRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the deterministic core of {@link RoadmapService}: catalog materialization, seeding
 * from demonstrated skills (already-known skills start DONE, gaps start PLANNED), computed progress,
 * task toggling and idempotent project add. Uses a real {@link ObjectMapper} and {@link RoadmapCatalog}
 * with a stubbed persistence layer that echoes the last-saved roadmap back on read.
 */
@ExtendWith(MockitoExtension.class)
class RoadmapServiceTest {

    @Mock
    private CareerRoadmapRepository careerRoadmapRepository;

    @Mock
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Mock
    private StudentSkillRepository studentSkillRepository;

    @Mock
    private DriveRepository driveRepository;

    @Mock
    private DriveSkillRequirementRepository driveSkillRequirementRepository;

    private RoadmapService roadmapService;

    @BeforeEach
    void setUp() {
        roadmapService = new RoadmapService(
            careerRoadmapRepository, resumeAnalysisRepository, studentSkillRepository,
            driveRepository, driveSkillRequirementRepository,
            new RoadmapCatalog(), new ObjectMapper());
    }

    private Student backendStudent() {
        return Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("demo@campus.example")
            .targetRole("Backend Developer").build();
    }

    /** No prior roadmap → materialize from catalog; the saved entity is echoed on subsequent reads. */
    private void stubEmptyThenPersist() {
        when(careerRoadmapRepository.findByStudentId(anyString())).thenReturn(Optional.empty());
        when(careerRoadmapRepository.save(any(CareerRoadmap.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void materializesBackendRoadmapWithAllTasksPlannedWhenNothingDemonstrated() {
        stubEmptyThenPersist();
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of());
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());

        RoadmapResponse roadmap = roadmapService.getRoadmap(backendStudent());

        assertThat(roadmap.phases()).isNotEmpty();
        assertThat(roadmap.taskCount()).isGreaterThan(0);
        assertThat(roadmap.completedTaskCount()).isZero();
        assertThat(roadmap.overallProgress()).isZero();
        // With nothing done, the first phase is the current focus.
        assertThat(roadmap.currentFocus()).isNotNull();
        assertThat(roadmap.phases().get(0).status()).isEqualTo("CURRENT");
    }

    @Test
    void seedsDemonstratedSkillsAsDoneAndLeavesGapsPlanned() {
        stubEmptyThenPersist();
        // "SQL" rated above threshold is demonstrated; the backend catalog has an SQL task.
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of(
            StudentSkill.builder().id("sk_1").studentId("stu_1").skill("SQL").proficiency(80).build()));
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());

        RoadmapResponse roadmap = roadmapService.getRoadmap(backendStudent());

        boolean sqlDone = roadmap.phases().stream()
            .flatMap(p -> p.tasks().stream())
            .anyMatch(t -> "SQL".equals(t.skill()) && "DONE".equals(t.status()));
        assertThat(sqlDone).isTrue();
        assertThat(roadmap.completedTaskCount()).isEqualTo(1);
        assertThat(roadmap.overallProgress()).isGreaterThan(0);
    }

    @Test
    void lowProficiencySkillIsNotTreatedAsDemonstrated() {
        stubEmptyThenPersist();
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of(
            StudentSkill.builder().id("sk_1").studentId("stu_1").skill("SQL").proficiency(30).build()));
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());

        RoadmapResponse roadmap = roadmapService.getRoadmap(backendStudent());

        assertThat(roadmap.completedTaskCount()).isZero();
    }

    @Test
    void togglingATaskToDoneAdvancesProgress() {
        stubEmptyThenPersist();
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of());
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        Student student = backendStudent();

        RoadmapResponse initial = roadmapService.getRoadmap(student);
        String firstTaskId = initial.phases().get(0).tasks().get(0).id();
        // After the first materialization the entity exists; subsequent calls read it back.
        CareerRoadmap saved = captureSaved(initial, student);
        when(careerRoadmapRepository.findByStudentId(anyString())).thenReturn(Optional.of(saved));

        RoadmapResponse after = roadmapService.toggleTask(student, firstTaskId, "DONE");

        assertThat(after.completedTaskCount()).isEqualTo(1);
        boolean done = after.phases().stream().flatMap(p -> p.tasks().stream())
            .anyMatch(t -> t.id().equals(firstTaskId) && "DONE".equals(t.status()));
        assertThat(done).isTrue();
    }

    @Test
    void addingAProjectIsIdempotentByTitle() {
        stubEmptyThenPersist();
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of());
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        Student student = backendStudent();

        RoadmapResponse initial = roadmapService.getRoadmap(student);
        CareerRoadmap saved = captureSaved(initial, student);
        when(careerRoadmapRepository.findByStudentId(anyString())).thenReturn(Optional.of(saved));

        RoadmapProjectRequest req = new RoadmapProjectRequest(
            "Payments API", "Build a payments service", "REST API", List.of("REST API"), "Intermediate");
        RoadmapResponse first = roadmapService.addProject(student, req);
        RoadmapResponse second = roadmapService.addProject(student, req);

        assertThat(first.projectCount()).isEqualTo(1);
        assertThat(second.projectCount()).isEqualTo(1); // dedupe: not added twice
    }

    @Test
    void regeneratePreservesManualCompletionAndPicksUpNewlyDemonstratedSkills() {
        // First materialization: nothing demonstrated -> everything PLANNED.
        stubEmptyThenPersist();
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of());
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        Student student = backendStudent();

        RoadmapResponse initial = roadmapService.getRoadmap(student);
        // Manually complete a non-SQL task so we can prove the completion survives regeneration.
        String manualTaskId = initial.phases().stream()
            .flatMap(p -> p.tasks().stream())
            .filter(t -> !"SQL".equals(t.skill()))
            .map(RoadmapResponse.Task::id)
            .findFirst().orElseThrow();
        CareerRoadmap afterMaterialize = captureSaved(initial, student);
        when(careerRoadmapRepository.findByStudentId(anyString())).thenReturn(Optional.of(afterMaterialize));
        RoadmapResponse toggled = roadmapService.toggleTask(student, manualTaskId, "DONE");
        assertThat(toggled.completedTaskCount()).isEqualTo(1);

        // The student now records SQL (>= threshold), then regenerates.
        CareerRoadmap afterToggle = captureSaved(toggled, student);
        when(careerRoadmapRepository.findByStudentId(anyString())).thenReturn(Optional.of(afterToggle));
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of(
            StudentSkill.builder().id("sk_1").studentId("stu_1").skill("SQL").proficiency(80).build()));

        RoadmapResponse regenerated = roadmapService.regenerate(student);

        // Manual completion survives AND the newly-demonstrated SQL task is now DONE.
        boolean manualStillDone = regenerated.phases().stream().flatMap(p -> p.tasks().stream())
            .anyMatch(t -> t.id().equals(manualTaskId) && "DONE".equals(t.status()));
        assertThat(manualStillDone).isTrue();
        boolean sqlDone = regenerated.phases().stream().flatMap(p -> p.tasks().stream())
            .anyMatch(t -> "SQL".equals(t.skill()) && "DONE".equals(t.status()));
        assertThat(sqlDone).isTrue();
        assertThat(regenerated.completedTaskCount()).isEqualTo(2);
    }

    @Test
    void firstViewRecoversFromAConcurrentMaterializationRace() {
        // Two concurrent first views: this one finds nothing, tries to insert, and loses the
        // unique(student_id) race; it must re-read the winner's row instead of surfacing a 500.
        Student student = backendStudent();
        CareerRoadmap winner = CareerRoadmap.builder()
            .id("career_roadmap_winner").studentId("stu_1").campusId("campus_a")
            .targetRole("Backend Developer")
            .roadmapJson("{\"targetRole\":\"Backend Developer\",\"phases\":[]}")
            .build();
        when(careerRoadmapRepository.findByStudentId(anyString()))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(winner));
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of());
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString()))
            .thenReturn(Optional.empty());
        when(careerRoadmapRepository.save(any(CareerRoadmap.class)))
            .thenThrow(new DataIntegrityViolationException("uq_career_roadmap_student"));

        RoadmapResponse roadmap = roadmapService.getRoadmap(student);

        // No exception escaped; we served the concurrently-created roadmap.
        assertThat(roadmap.targetRole()).isEqualTo("Backend Developer");
    }

    @Test
    void unknownRoleBuildsPhasesFromDriveRequirementsBucketedByWeight() {
        stubEmptyThenPersist();
        // "Unity" rated above threshold is demonstrated — it must start DONE even on a drive-built
        // roadmap, proving seeding works outside the curated catalog too.
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of(
            StudentSkill.builder().id("sk_1").studentId("stu_1").skill("Unity").proficiency(80).build()));
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        Student student = Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("demo@campus.example")
            .targetRole("AR/VR Developer").build();
        when(driveRepository.findByCampusId("campus_a")).thenReturn(List.of(
            Drive.builder().id("drive_1").campusId("campus_a").role("AR/VR Developer").build()));
        when(driveSkillRequirementRepository.findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc("drive_1"))
            .thenReturn(List.of(
                DriveSkillRequirement.builder().id("req_1").driveId("drive_1")
                    .skill("Unity").requiredLevel(80).weight(3).build(),
                DriveSkillRequirement.builder().id("req_2").driveId("drive_1")
                    .skill("C#").requiredLevel(70).weight(2).build(),
                DriveSkillRequirement.builder().id("req_3").driveId("drive_1")
                    .skill("Shader Programming").requiredLevel(55).weight(1).build()));

        RoadmapResponse roadmap = roadmapService.getRoadmap(student);

        // Three weight buckets -> three phases, one task per approved requirement.
        assertThat(roadmap.phaseCount()).isEqualTo(3);
        assertThat(roadmap.phases().get(0).key()).isEqualTo("core");
        assertThat(roadmap.phases().get(1).key()).isEqualTo("build");
        assertThat(roadmap.phases().get(2).key()).isEqualTo("stretch");
        assertThat(roadmap.phases().get(0).tasks()).extracting(RoadmapResponse.Task::skill)
            .containsExactly("Unity");
        assertThat(roadmap.taskCount()).isEqualTo(3);
        boolean unityDone = roadmap.phases().stream().flatMap(p -> p.tasks().stream())
            .anyMatch(t -> "Unity".equals(t.skill()) && "DONE".equals(t.status()));
        assertThat(unityDone).isTrue();
    }

    @Test
    void unknownRoleWithoutDriveRequirementsFallsBackToGenericTrack() {
        stubEmptyThenPersist();
        when(studentSkillRepository.findByStudentIdOrderBySkillAsc(anyString())).thenReturn(List.of());
        when(resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(driveRepository.findByCampusId(anyString())).thenReturn(List.of());
        Student student = Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("demo@campus.example")
            .targetRole("AR/VR Developer").build();

        RoadmapResponse roadmap = roadmapService.getRoadmap(student);

        // No curated family, no drive requirements -> the catalog's generic engineering track.
        assertThat(roadmap.phases().get(0).title()).isEqualTo("Engineering foundations");
    }

    /** The roadmap JSON of the most recent save, wrapped as the persisted entity for read-back. */
    private CareerRoadmap captureSaved(RoadmapResponse ignored, Student student) {
        org.mockito.ArgumentCaptor<CareerRoadmap> captor =
            org.mockito.ArgumentCaptor.forClass(CareerRoadmap.class);
        org.mockito.Mockito.verify(careerRoadmapRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
