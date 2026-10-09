package com.nexploy.campuslink.application.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable record of one application-status transition — the placement funnel's audit
 * trail. Mirrors {@code offer_status_changes}: written by the service on every advance.
 */
@Entity
@Table(name = "application_status_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusChange {

    @Id
    private String id;

    private String applicationId;

    /** Null on the very first recorded transition. */
    @Enumerated(EnumType.STRING)
    private ApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus newStatus;

    private String changedBy;
    private OffsetDateTime changedAt;
}
