package com.nexploy.campuslink.aptitude.entity;

import jakarta.persistence.Column;
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

/**
 * One MCQ in the aptitude module. Serves two lanes that must never mix: bank rows
 * (studentId NULL, source SEEDED/OFFICER) back the official timed tests, while practice rows
 * (studentId = owner, source AI_GENERATED) are a student's private AI-generated items and are
 * excluded from official draws by the repository filter.
 */
@Entity
@Table(name = "aptitude_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AptitudeQuestion {

    @Id
    private String id;

    /** NULL = global bank question visible to every campus. */
    private String campusId;

    /** Owner of an AI_GENERATED practice question; NULL for bank rows. */
    private String studentId;

    @Enumerated(EnumType.STRING)
    private AptitudeQuestionSource source;

    /** QUANTITATIVE | LOGICAL | VERBAL | DATA_INTERPRETATION. */
    private String category;

    private String difficulty;

    @Column(columnDefinition = "text")
    private String prompt;

    @Column(name = "option_a", columnDefinition = "text")
    private String optionA;

    @Column(name = "option_b", columnDefinition = "text")
    private String optionB;

    @Column(name = "option_c", columnDefinition = "text")
    private String optionC;

    @Column(name = "option_d", columnDefinition = "text")
    private String optionD;

    /** 'A'..'D'. */
    @Column(length = 1)
    private String correctOption;

    @Column(columnDefinition = "text")
    private String explanation;

    private boolean active;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
