package com.nexploy.campuslink.drive.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "drives")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Drive {

    @Id
    private String id;

    private String campusId;
    private String recruiterId;
    /** Optional placement cycle this drive belongs to (nullable, backward-compatible). */
    private String cycleId;
    private String companyName;
    private String role;
    private String location;
    private BigDecimal packageLpa;
    private BigDecimal minimumCgpa;
    private Integer maximumBacklogs;
    private Integer graduationYear;
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    private DriveStatus status;

    // ---- Enrichment (V18): display + full eligibility criteria --------
    // driveType matches the frontend union ("On-Campus"/"Off-Campus"/"Virtual"/
    // "Pooled"); stored as a plain string since those values are not valid Java
    // identifiers for an enum.
    private String driveType;

    @Column(length = 2000)
    private String description;

    private Integer expectedHiring;
    private String venue;

    /** Optional eligibility floors; a null means the drive does not constrain it. */
    private BigDecimal minimumTenthPercentage;
    private BigDecimal minimumTwelfthPercentage;

    /** JSON array of strings, e.g. ["Java","Spring Boot"]. */
    @Column(columnDefinition = "text")
    private String requiredSkillsJson;

    /** JSON array of {name, description}. */
    @Column(columnDefinition = "text")
    private String roundsJson;

    /** JSON array of allowed branch codes, e.g. ["CSE","IT"]. Empty/absent = unconstrained. */
    @Column(columnDefinition = "text")
    private String allowedBranchesJson;

    // ---- Soft-delete audit (V42): who retired this drive, and when ----
    private java.time.OffsetDateTime closedAt;
    private String closedBy;
}

