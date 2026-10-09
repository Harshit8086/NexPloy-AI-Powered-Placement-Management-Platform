package com.nexploy.campuslink.student.entity;

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
 * One dated point in a student's backlog timeline. Append-only: a row is written whenever the
 * student's active-backlog count changes (self-edit or officer edit) or an officer records a
 * timeline note. {@code students.backlogs} remains the authoritative current count and the only
 * value readiness/eligibility consume; this table is the descriptive history behind it.
 */
@Entity
@Table(name = "student_backlog_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentBacklogHistory {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    /** Academic semester this count applies to, when known. */
    private Integer semester;

    /** Active-backlog count as of this record. */
    private Integer activeBacklogs;

    /** How many backlogs were cleared versus the previous record; null when not derivable. */
    private Integer clearedBacklogs;

    private String note;

    /** Username of whoever caused the record (the student, an officer, or "system" for backfill). */
    private String recordedBy;

    private OffsetDateTime recordedAt;
}
