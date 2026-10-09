package com.nexploy.campuslink.student.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A skill the student currently claims, with a self-rated proficiency (0-100). This is the
 * student's own mutable record — one row per skill, upserted in place — and is what skill-gap
 * analysis and semantic matching read as present ability.
 *
 * <p>Distinct from {@code SkillEvidence} in the readiness module: that is an append-only trail of
 * observations feeding a derived score and is never overwritten, whereas a {@code StudentSkill} is
 * edited freely by the student.
 */
@Entity
@Table(name = "student_skills")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSkill {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    /** Canonical skill label, e.g. "Java", "System Design". Unique per student (case-insensitive). */
    private String skill;

    /** Self-rated proficiency 0-100. */
    private Integer proficiency;

    /** How the level was set: SELF_REPORTED (default), ASSESSMENT, RESUME, MENTOR. */
    private String source;

    /**
     * Verification level, independent of {@link #source}: SELF_DECLARED (default) -> EVIDENCED
     * (student attached a proof link) -> VERIFIED (passed an AI skill viva). Set server-side only.
     */
    private String verificationStatus;

    /** Optional https proof link backing the skill — a project, GitHub repo or certificate. */
    private String evidenceUrl;

    /** Optional short note describing the attached evidence. */
    private String evidenceNote;

    private OffsetDateTime updatedAt;
}
