package com.nexploy.campuslink.drive.entity;

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
 * Per-drive skill requirement defining the required proficiency level (0-100), importance weight,
 * and mandatory flag for a specific drive. Rows are either extracted by Spring AI from the job description
 * (and reviewed/approved by the placement officer) or directly entered by the officer.
 */
@Entity
@Table(name = "drive_skill_requirements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveSkillRequirement {

    @Id
    private String id;

    @Column(nullable = false)
    private String driveId;

    @Column(nullable = false, length = 120)
    private String skill;

    @Column(nullable = false)
    private Integer requiredLevel;

    @Builder.Default
    @Column(nullable = false)
    private Integer weight = 1;

    @Builder.Default
    @Column(nullable = false)
    private Boolean mandatory = true;

    @Builder.Default
    @Column(nullable = false, length = 32)
    private String source = "OFFICER";

    /** AI extraction confidence (0-100), null for manually entered requirements. */
    private Integer confidence;

    @Builder.Default
    @Column(nullable = false)
    private Boolean approved = true;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
