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
 * A private mentoring note about a student. Sensitive data per SECURITY.md section 1;
 * readable only by the authoring mentor (via assignment) and privileged officers/admins.
 */
@Entity
@Table(name = "mentor_notes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorNote {

    @Id
    private String id;

    private String studentId;
    private String campusId;
    private String authorUsername;
    private String note;
    private OffsetDateTime createdAt;
}
