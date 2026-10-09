package com.nexploy.campuslink.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single student project, normalized out of {@code students.projects_json} (V40). Named
 * {@code StudentProjectEntry} to avoid a clash with the API DTO
 * {@link com.nexploy.campuslink.student.dto.StudentProject}. The project's stack is kept as
 * a JSON array string ({@code stackJson}) rather than a further child table — a single small
 * list read/written as a unit. The JSON column on students is retained and dual-written.
 */
@Entity
@Table(name = "student_projects")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProjectEntry {

    @Id
    private String id;

    private String studentId;
    private String campusId;
    private String name;

    @Column(columnDefinition = "text")
    private String summary;

    /** JSON array of stack technologies, e.g. {@code ["React","Spring Boot"]}. */
    @Column(columnDefinition = "text")
    private String stackJson;

    /** Optional repository link (V65). */
    private String githubUrl;

    /** Optional live-demo link (V65). */
    private String liveUrl;

    private int sortOrder;
}
