package com.nexploy.campuslink.interview.entity;

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

@Entity
@Table(name = "interviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interview {

    @Id
    private String id;

    private String campusId;
    private String studentId;
    private String driveId;
    private String scheduleEventId;

    @Enumerated(EnumType.STRING)
    private InterviewMode mode;

    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    private InterviewStatus status;

    @Enumerated(EnumType.STRING)
    private InterviewResult result;

    private String feedback;
    private OffsetDateTime createdAt;

    // ---- Enrichment (V21): display fields for the frontend InterviewSlot ----
    private String round;
    private String venue;
    private String panel;
    private String companyName;
}
