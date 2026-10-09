package com.nexploy.campuslink.risk.entity;

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
 * A tracked support plan created in response to placement risk (PRD/DESIGN — the `InterventionPlan`
 * contract entity, and the `InterventionAssigned` domain event in DESIGN.md section 7). Unlike a
 * {@link RiskEntry}, a plan is mutable: it moves through PROPOSED -> ACTIVE -> COMPLETED, its
 * action items are checked off, and progress is derived from those items. It binds a student to a
 * mentor and records the risk entry it was raised from, so the risk -> intervention link is
 * persisted rather than implicit.
 */
@Entity
@Table(name = "intervention_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionPlan {

    @Id
    private String id;

    private String studentId;
    private String campusId;
    private String mentorUsername;

    /** The risk entry that prompted this plan, for traceability. */
    private String riskEntryId;

    /** PROPOSED, ACTIVE, COMPLETED. */
    private String status;

    /** Focus areas, serialized as a JSON array of strings. */
    @Column(columnDefinition = "text")
    private String focusAreasJson;

    /** Action items, serialized as a JSON array of {id, label, done}. */
    @Column(columnDefinition = "text")
    private String actionsJson;

    /** Derived from action items (0-100); recomputed on every action toggle. */
    private int progress;

    @Column(length = 2000)
    private String notes;

    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
