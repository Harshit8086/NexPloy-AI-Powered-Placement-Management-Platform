package com.nexploy.campuslink.matching.entity;

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
 * An officer's accept/reject decision over a scored match. Immutable audit row; the
 * corresponding funnel advance (application -> SHORTLISTED) is recorded separately in
 * application_status_history.
 */
@Entity
@Table(name = "shortlist_decisions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortlistDecision {

    @Id
    private String id;

    /** Nullable: a decision can be recorded even if the match was never persisted. */
    private String matchResultId;

    private String driveId;
    private String studentId;
    private String decidedBy;

    /** SHORTLIST or REJECT. */
    private String decision;

    @Column(length = 500)
    private String reason;

    private OffsetDateTime decidedAt;
}
