package com.nexploy.campuslink.offer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Offer {

    @Id
    private String id;

    private String campusId;
    private String studentId;
    private String driveId;
    private String recruiterId;
    /** Optional placement cycle this offer belongs to (nullable, backward-compatible). */
    private String cycleId;
    private String role;
    private BigDecimal packageLpa;

    @Enumerated(EnumType.STRING)
    private OfferStatus status;

    private OffsetDateTime extendedAt;
    private OffsetDateTime decidedAt;
    private String decidedBy;

    // ---- Enrichment (V20): offer & joining-status tracking ----
    private String companyName;
    private String offerType;
    private String location;
    private LocalDate joiningDate;
    private LocalDate acceptanceDeadline;

    @lombok.Builder.Default
    private boolean documentsVerified = false;

    @lombok.Builder.Default
    private boolean joined = false;

    /** Set when the offer originated from an internship (PPO tracking). */
    private String fromInternship;
}
