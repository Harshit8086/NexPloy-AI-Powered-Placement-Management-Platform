package com.nexploy.campuslink.readiness.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.readiness.client.MlReadinessClient;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.Assessments;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScore;
import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScoreRequest;
import com.nexploy.campuslink.readiness.dto.ReadinessScoreRequestBody;
import com.nexploy.campuslink.readiness.dto.RecordSkillEvidenceRequest;
import com.nexploy.campuslink.readiness.entity.ReadinessSnapshot;
import com.nexploy.campuslink.readiness.entity.SkillEvidence;
import com.nexploy.campuslink.readiness.repository.ReadinessSnapshotRepository;
import com.nexploy.campuslink.readiness.repository.SkillEvidenceRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.service.ReadinessInputAssembler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates readiness scoring and its evidence trail. Enforces authorization and resolves the
 * student (Spring is the system of record), delegates scoring to the stateless Python ML service,
 * then persists a derived {@link ReadinessSnapshot} as append-only history — mirroring DESIGN.md
 * section 2, which requires raw evidence ({@link SkillEvidence}) to be stored separately from
 * derived scores and never overwritten by a model result.
 */
@Service
@RequiredArgsConstructor
public class ReadinessService {

    private final StudentRepository studentRepository;
    private final SkillEvidenceRepository skillEvidenceRepository;
    private final ReadinessSnapshotRepository readinessSnapshotRepository;
    private final CampusAccessService campusAccessService;
    private final MlReadinessClient mlReadinessClient;
    private final ReadinessInputAssembler inputAssembler;
    private final ObjectMapper objectMapper;

    @Value("${ml.policy-version:policy-2026-01}")
    private String modelPolicyVersion;

    @Transactional
    public ReadinessScore score(String studentId, String placementCycleId, ReadinessScoreRequestBody body) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());

        // Read (never mutate) the raw evidence this student has on record.
        List<SkillEvidence> evidence = skillEvidenceRepository.findByStudentIdOrderByRecordedAtDesc(studentId);

        ReadinessScoreRequest request = new ReadinessScoreRequest(
            UUID.randomUUID().toString(),
            student.getCampusId(),
            placementCycleId,
            modelPolicyVersion,
            studentId,
            new Assessments(
                body.academic(), body.aptitude(), body.communication(),
                body.projects(), body.interview()
            )
        );
        ReadinessScore result = mlReadinessClient.score(request);

        persistSnapshot(student, placementCycleId, result, evidence);
        return result;
    }

    /**
     * Recomputes readiness from the student's EXISTING data (assessments, academics, projects) via
     * {@link ReadinessInputAssembler} — never a caller-supplied body — then writes the result back to
     * the columns the UI reads ({@code students.readiness_score} + {@code breakdown_json}, Path B) and
     * appends a derived {@link ReadinessSnapshot} (append-only history, Path A). This is what closes
     * the gap between the orphaned scoring path and the displayed score. Missing dimensions stay
     * absent (never zero-filled): the ML service excludes and renormalises them, so the written
     * breakdown simply omits them and the UI shows "Not Available".
     */
    @Transactional
    public ReadinessScore recompute(String studentId, String placementCycleId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return recomputeAndWriteBack(student, placementCycleId);
    }

    /**
     * System-scoped readiness recompute for background / event-driven callers (e.g.
     * {@code ReadinessRecomputeListener}, a scheduled sweep) that run WITHOUT a security context.
     * Resolves the student and runs the same derive → score → write-back → snapshot path as
     * {@link #recompute}, but performs NO {@link CampusAccessService} check: it is triggered by the
     * system in response to a change the platform already authorized, never directly by a request.
     * Callers must therefore only pass a {@code studentId} the triggering action was allowed to touch.
     */
    @Transactional
    public ReadinessScore recomputeInternal(String studentId, String placementCycleId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        return recomputeAndWriteBack(student, placementCycleId);
    }

    /** Shared derive → score → write-back → snapshot path; caller has resolved and authorized the student. */
    private ReadinessScore recomputeAndWriteBack(Student student, String placementCycleId) {
        ReadinessScoreRequestBody derived = inputAssembler.assemble(student);
        List<SkillEvidence> evidence =
            skillEvidenceRepository.findByStudentIdOrderByRecordedAtDesc(student.getId());

        ReadinessScoreRequest request = new ReadinessScoreRequest(
            UUID.randomUUID().toString(),
            student.getCampusId(),
            placementCycleId,
            modelPolicyVersion,
            student.getId(),
            new Assessments(
                derived.academic(), derived.aptitude(), derived.communication(),
                derived.projects(), derived.interview()
            )
        );
        ReadinessScore result = mlReadinessClient.score(request);

        // Write back to the displayed columns (Path B) from the same derived result.
        student.setReadinessScore((int) Math.round(result.score()));
        student.setBreakdownJson(writeBreakdown(result));
        studentRepository.save(student);

        persistSnapshot(student, placementCycleId, result, evidence);
        return result;
    }
    @Transactional
    public SkillEvidence recordEvidence(String studentId, RecordSkillEvidenceRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());

        SkillEvidence evidence = SkillEvidence.builder()
            .id("evid_" + UUID.randomUUID())
            .studentId(studentId)
            .campusId(student.getCampusId())
            .skill(request.skill())
            .source(request.source())
            .level(request.level())
            .detail(request.detail())
            .recordedBy(user.getUsername())
            .recordedAt(OffsetDateTime.now())
            .build();
        return skillEvidenceRepository.save(evidence);
    }

    @Transactional(readOnly = true)
    public List<SkillEvidence> listEvidence(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return skillEvidenceRepository.findByStudentIdOrderByRecordedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<ReadinessSnapshot> listSnapshots(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return readinessSnapshotRepository.findByStudentIdOrderByGeneratedAtDesc(studentId);
    }

    private void persistSnapshot(Student student, String placementCycleId, ReadinessScore result,
                                 List<SkillEvidence> evidence) {
        String evidenceIds = evidence.stream().map(SkillEvidence::getId).collect(Collectors.joining(","));
        ReadinessSnapshot snapshot = ReadinessSnapshot.builder()
            .id("rds_" + UUID.randomUUID())
            .studentId(student.getId())
            .campusId(student.getCampusId())
            .placementCycleId(placementCycleId)
            .score(result.score())
            .category(result.category())
            .componentScoresJson(writeBreakdown(result))
            .evidenceIds(evidenceIds)
            .confidence(result.confidence())
            .calculationVersion(modelPolicyVersion)
            .modelVersion(result.modelVersion())
            .featureVersion(result.featureVersion())
            .generatedAt(result.generatedAt() == null ? OffsetDateTime.now() : result.generatedAt())
            .createdAt(OffsetDateTime.now())
            .build();
        readinessSnapshotRepository.save(snapshot);
    }

    private String writeBreakdown(ReadinessScore result) {
        try {
            return objectMapper.writeValueAsString(result.breakdown() == null ? Map.of() : result.breakdown());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize readiness component scores", exception);
        }
    }
}
