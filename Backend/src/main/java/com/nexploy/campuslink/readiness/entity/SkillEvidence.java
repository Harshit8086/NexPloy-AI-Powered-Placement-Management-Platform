package com.nexploy.campuslink.readiness.entity;

import jakarta.persistence.Column;
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
 * A raw, append-only piece of skill evidence for a student (DESIGN.md section 2:
 * "Store raw evidence separately from derived scores... Never overwrite raw evidence with a
 * model result"). This is the system of record for what was observed — a self-reported skill
 * level, an assessment result, a certification — and is never mutated by readiness scoring. A
 * {@link ReadinessSnapshot} references these rows by id; it does not replace them.
 */
@Entity
@Table(name = "skill_evidence")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEvidence {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    /** The skill this evidence speaks to, e.g. "java", "system-design". */
    private String skill;

    /** Where the observation came from: SELF_REPORTED, ASSESSMENT, CERTIFICATION, MENTOR. */
    private String source;

    /** Observed level 0-100. Kept exactly as recorded; a model result never overwrites it. */
    private Integer level;

    @Column(length = 500)
    private String detail;

    /** Who recorded it (username), for audit. */
    private String recordedBy;

    private OffsetDateTime recordedAt;
}
