package com.nexploy.campuslink.matching.service;

import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.matching.client.EmbeddingClient;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owns skill embeddings for DB-side semantic similarity (pgvector). Only the ML service can produce
 * a vector, so this asks the {@link EmbeddingClient} for one and stores it via the repositories.
 *
 * <p>Backfill is lazy and self-healing: {@code ensure*} computes and stores an embedding only when
 * the row has none yet, so a match request over a fresh pool populates the columns on first use
 * with no separate job. Skill edits are not yet invalidating the stored vector — a later change to
 * a student's skills or a drive's required skills should clear the column so it is recomputed; at
 * demo scale skills are static, so this is a known, documented gap rather than a silent one.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;

    /** Ensure the drive's required-skills embedding exists, computing + storing it if absent. */
    public void ensureDriveEmbedding(String driveId, List<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return; // No required skills → no embedding; similarity stays null (missing evidence).
        }
        if (Boolean.TRUE.equals(driveRepository.hasRequiredSkillsEmbedding(driveId))) {
            return;
        }
        String literal = toVectorLiteral(embeddingClient.embed(requiredSkills));
        if (literal != null) {
            driveRepository.updateRequiredSkillsEmbedding(driveId, literal);
        }
    }

    /** Ensure the student's skills embedding exists, computing + storing it if absent. */
    public void ensureStudentEmbedding(String studentId, List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(studentRepository.hasSkillsEmbedding(studentId))) {
            return;
        }
        String literal = toVectorLiteral(embeddingClient.embed(skills));
        if (literal != null) {
            studentRepository.updateSkillsEmbedding(studentId, literal);
        }
    }

    /**
     * The DB-side cosine similarity (0-100) between a drive and a student, or {@code null} when
     * either embedding is absent. Callers ensure the embeddings first.
     */
    public Double similarityPct(String driveId, String studentId) {
        return studentRepository.semanticSimilarityPct(driveId, studentId);
    }

    /**
     * Ensure the drive's description embedding exists (role + description + required-skills text),
     * computing + storing it if absent. The texts are forwarded to the ML service as terms; it joins
     * and embeds them the same way it embeds skills, so stored vectors stay comparable.
     */
    public void ensureDriveDescriptionEmbedding(String driveId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return; // No description text → no embedding; project relevance stays null.
        }
        if (Boolean.TRUE.equals(driveRepository.hasDescriptionEmbedding(driveId))) {
            return;
        }
        String literal = toVectorLiteral(embeddingClient.embed(texts));
        if (literal != null) {
            driveRepository.updateDescriptionEmbedding(driveId, literal);
        }
    }

    /** Ensure the student's projects embedding exists (project names/summaries/stacks), if absent. */
    public void ensureStudentProjectsEmbedding(String studentId, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(studentRepository.hasProjectsEmbedding(studentId))) {
            return;
        }
        String literal = toVectorLiteral(embeddingClient.embed(texts));
        if (literal != null) {
            studentRepository.updateProjectsEmbedding(studentId, literal);
        }
    }

    /**
     * The DB-side cosine similarity (0-100) between a student's projects embedding and a drive's
     * description embedding — the deep project-JD relevance signal — or {@code null} when either
     * embedding is absent. Callers ensure the embeddings first.
     */
    public Double projectRelevancePct(String driveId, String studentId) {
        return studentRepository.projectRelevancePct(driveId, studentId);
    }

    /** Render a vector as the pgvector text literal "[v0,v1,...]"; {@code null} for an absent vector. */
    static String toVectorLiteral(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (Double value : vector) {
            joiner.add(Double.toString(value == null ? 0.0 : value));
        }
        return joiner.toString();
    }
}
