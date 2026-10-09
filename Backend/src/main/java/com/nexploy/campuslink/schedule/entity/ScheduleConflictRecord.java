package com.nexploy.campuslink.schedule.entity;

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
@Table(name = "schedule_conflicts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleConflictRecord {

    @Id
    private String id;

    private String campusId;

    @Enumerated(EnumType.STRING)
    private ConflictType conflictType;

    private String eventId;
    private String conflictingEventId;
    private String resourceId;
    private OffsetDateTime overlapStart;
    private OffsetDateTime overlapEnd;
    private String message;
    private OffsetDateTime detectedAt;
}
