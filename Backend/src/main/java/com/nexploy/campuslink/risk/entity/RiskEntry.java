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
 * A persisted placement-risk result for a student, written append-only each time risk is scored
 * (mirroring the readiness snapshot pattern; DESIGN.md sections 6-7). Risk is support-oriented —
 * it records a support probability, a band (LOW/MEDIUM/HIGH), the driving factors, and the
 * recommended interventions — and is never an exclusion decision. A high-band entry is what an
 * officer/mentor turns into a tracked {@link InterventionPlan}; the entry itself is immutable
 * history and is never overwritten.
 */
@Entity
@Table(name = "risk_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskEntry {

    @Id
    private String id;

    private String studentId;
    private String campusId;
    private String placementCycleId;

    /** Optional FK to placement_cycles (nullable, dual with the legacy placementCycleId string). */
    private String cycleId;

    /** LOW, MEDIUM, HIGH — the frontend RiskLevel contract. */
    private String level;

    private double supportProbability;

    @Column(length = 500)
    private String primaryReason;

    /** Driving factors, serialized as a JSON array of {name, impact, direction}. */
    @Column(columnDefinition = "text")
    private String factorsJson;

    /** Recommended interventions, serialized as a JSON array of strings. */
    @Column(columnDefinition = "text")
    private String recommendedInterventionsJson;

    private double confidence;

    private String modelVersion;
    private String featureVersion;

    private OffsetDateTime generatedAt;
    private OffsetDateTime createdAt;
}
