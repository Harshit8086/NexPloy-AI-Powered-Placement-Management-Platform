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
@Table(name = "schedule_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEvent {

    @Id
    private String id;

    private String driveId;
    private String campusId;
    private String venueId;
    private String panelId;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private Integer expectedParticipants;
    private Integer venueCapacity;
    private boolean conflict;

    // ---- Multi-type scheduling (V50): additive; existing rows default to INTERVIEW/INDIVIDUAL ----
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    private ParticipantMode participantMode;

    private Integer participantCount;
}

