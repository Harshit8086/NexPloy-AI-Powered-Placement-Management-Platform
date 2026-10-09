package com.nexploy.campuslink.schedule.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "event_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventAssignment {

    @Id
    private String id;

    private String eventId;
    private String studentId;
    private String campusId;
    private String assignmentRole;
    private OffsetDateTime createdAt;
}
