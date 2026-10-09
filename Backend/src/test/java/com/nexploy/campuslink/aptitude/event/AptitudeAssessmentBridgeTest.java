package com.nexploy.campuslink.aptitude.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.aptitude.service.AptitudeTestCatalog;
import com.nexploy.campuslink.assessment.entity.AssessmentType;
import com.nexploy.campuslink.assessment.service.PreparedAssessmentRow;
import com.nexploy.campuslink.assessment.service.StudentAssessmentWriter;
import com.nexploy.campuslink.readiness.service.ReadinessService;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AptitudeAssessmentBridge#on}: the assessment row it writes (catalog-title
 * name, clamped normalized score, APTITUDE_TEST source, completion date), the readiness recompute
 * that follows, and the non-fatal behaviour on every failure path. The listener wiring itself
 * ({@code @Async} after commit) is standard Spring machinery exercised end to end by
 * {@code StudentAptitudeControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class AptitudeAssessmentBridgeTest {

    @Mock private StudentRepository studentRepository;
    @Mock private StudentAssessmentWriter assessmentWriter;
    @Mock private ReadinessService readinessService;

    private AptitudeAssessmentBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new AptitudeAssessmentBridge(
            new AptitudeTestCatalog(), studentRepository, assessmentWriter, readinessService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void officialScoreWritesAnAptitudeRowAndRecomputes() {
        Student student = Student.builder().id("stu_1").campusId("campus_a").build();
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student));

        bridge.on(new AptitudeAttemptCompletedEvent("stu_1", "campus_a", "att_1",
            "QUANTITATIVE", new BigDecimal("86.00"), OffsetDateTime.parse("2026-10-03T10:15:00Z")));

        ArgumentCaptor<List<PreparedAssessmentRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(assessmentWriter).applyForStudent(eq(student), rows.capture());
        PreparedAssessmentRow row = rows.getValue().get(0);
        assertThat(row.type()).isEqualTo(AssessmentType.APTITUDE);
        assertThat(row.name()).isEqualTo("Quantitative Aptitude");
        assertThat(row.rawScore()).isEqualByComparingTo("86.00");
        assertThat(row.maxScore()).isEqualByComparingTo("100");
        assertThat(row.normalizedScore()).isEqualTo(86);
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(row.source()).isEqualTo("APTITUDE_TEST");
        verify(readinessService).recomputeInternal("stu_1", "current");
    }

    @Test
    void scoresAboveOneHundredClampToTheSnapshotScale() {
        Student student = Student.builder().id("stu_1").campusId("campus_a").build();
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student));

        bridge.on(new AptitudeAttemptCompletedEvent("stu_1", "campus_a", "att_1",
            "QUANTITATIVE", new BigDecimal("150.00"), OffsetDateTime.parse("2026-10-03T10:15:00Z")));

        ArgumentCaptor<List<PreparedAssessmentRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(assessmentWriter).applyForStudent(eq(student), rows.capture());
        assertThat(rows.getValue().get(0).normalizedScore()).isEqualTo(100);
    }

    @Test
    void unknownTestIdFallsBackToAGenericName() {
        Student student = Student.builder().id("stu_1").campusId("campus_a").build();
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student));

        bridge.on(new AptitudeAttemptCompletedEvent("stu_1", "campus_a", "att_1",
            "LEGACY_TEST", new BigDecimal("50.00"), OffsetDateTime.parse("2026-10-03T10:15:00Z")));

        ArgumentCaptor<List<PreparedAssessmentRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(assessmentWriter).applyForStudent(eq(student), rows.capture());
        assertThat(rows.getValue().get(0).name()).isEqualTo("Aptitude Test");
    }

    @Test
    void aMissingStudentSkipsEverything() {
        when(studentRepository.findById("stu_gone")).thenReturn(Optional.empty());

        bridge.on(new AptitudeAttemptCompletedEvent("stu_gone", "campus_a", "att_1",
            "QUANTITATIVE", new BigDecimal("86.00"), OffsetDateTime.parse("2026-10-03T10:15:00Z")));

        verifyNoInteractions(assessmentWriter, readinessService);
    }

    @Test
    void aNullScoreIsIgnored() {
        bridge.on(new AptitudeAttemptCompletedEvent("stu_1", "campus_a", "att_1",
            "QUANTITATIVE", null, OffsetDateTime.parse("2026-10-03T10:15:00Z")));

        verifyNoInteractions(studentRepository, assessmentWriter, readinessService);
    }

    @Test
    void aWriterFailureIsNonFatalAndSkipsTheRecompute() {
        Student student = Student.builder().id("stu_1").campusId("campus_a").build();
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student));
        when(assessmentWriter.applyForStudent(any(), any()))
            .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> bridge.on(new AptitudeAttemptCompletedEvent("stu_1", "campus_a",
            "att_1", "QUANTITATIVE", new BigDecimal("86.00"),
            OffsetDateTime.parse("2026-10-03T10:15:00Z"))))
            .doesNotThrowAnyException();
        verify(readinessService, never()).recomputeInternal(anyString(), anyString());
    }

    @Test
    void aRecomputeFailureIsNonFatal() {
        Student student = Student.builder().id("stu_1").campusId("campus_a").build();
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student));
        when(readinessService.recomputeInternal(anyString(), anyString()))
            .thenThrow(new IllegalStateException("ml down"));

        assertThatCode(() -> bridge.on(new AptitudeAttemptCompletedEvent("stu_1", "campus_a",
            "att_1", "QUANTITATIVE", new BigDecimal("86.00"),
            OffsetDateTime.parse("2026-10-03T10:15:00Z"))))
            .doesNotThrowAnyException();
    }
}