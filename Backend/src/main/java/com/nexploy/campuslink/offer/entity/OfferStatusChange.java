package com.nexploy.campuslink.offer.entity;

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
 * Immutable audit record of a single offer state transition. Required by SECURITY.md
 * section 4: consequential decisions must retain old and new values and who made them.
 */
@Entity
@Table(name = "offer_status_changes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferStatusChange {

    @Id
    private String id;

    private String offerId;

    @Enumerated(EnumType.STRING)
    private OfferStatus previousStatus;

    @Enumerated(EnumType.STRING)
    private OfferStatus newStatus;

    private String changedBy;
    private OffsetDateTime changedAt;
}
