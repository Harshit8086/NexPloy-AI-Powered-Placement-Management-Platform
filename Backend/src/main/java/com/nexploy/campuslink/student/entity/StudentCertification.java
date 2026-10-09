package com.nexploy.campuslink.student.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single student certification, normalized out of {@code students.certifications_json}
 * (V40). The JSON column is retained and dual-written; these rows are the normalized
 * mirror a later phase can promote to the sole source before the JSON column is retired.
 */
@Entity
@Table(name = "student_certifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCertification {

    @Id
    private String id;

    private String studentId;
    private String campusId;
    private String name;
    private int sortOrder;
}
