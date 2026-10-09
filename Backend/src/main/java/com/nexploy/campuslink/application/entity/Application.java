package com.nexploy.campuslink.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application {

    @Id
    private String id;

    private String studentId;
    private String driveId;
    private LocalDate appliedOn;

    /** Optional note the student includes when applying. */
    @Column(length = 2000)
    private String coverNote;

    /** Optional id of the resume Document the student attached at apply time. */
    private String resumeDocumentId;

    /** Optional placement cycle this application belongs to (nullable, backward-compatible). */
    private String cycleId;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;
}

