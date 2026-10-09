package com.nexploy.campuslink.mentor.entity;

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
 * Links a mentor (a UserAccount with role MENTOR, keyed by username) to a student they
 * are assigned to. This assignment is the basis for the "mentors access assigned students
 * only" rule in SECURITY.md section 2.
 */
@Entity
@Table(name = "mentor_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorAssignment {

    @Id
    private String id;

    private String mentorUsername;
    private String studentId;
    private String campusId;
    private OffsetDateTime assignedAt;
}
