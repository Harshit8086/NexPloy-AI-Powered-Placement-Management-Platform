package com.nexploy.campuslink.drive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private DriveRepository driveRepository;

    // Branch parsing runs only when a drive sets allowed branches (none in these cases),
    // so the mapper is never invoked; @InjectMocks only needs the dependency present.
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EligibilityService eligibilityService;

    @Test
    void returnsEligibleWhenAllHardCriteriaPass() {
        Drive drive = Drive.builder()
            .id("drive-1")
            .minimumCgpa(new BigDecimal("7.0"))
            .maximumBacklogs(1)
            .graduationYear(2026)
            .build();
        Student student = Student.builder()
            .id("student-1")
            .cgpa(new BigDecimal("8.2"))
            .backlogs(0)
            .graduationYear(2026)
            .build();
        when(driveRepository.findById("drive-1")).thenReturn(Optional.of(drive));
        when(studentRepository.findById("student-1")).thenReturn(Optional.of(student));

        EligibilityResponse response = eligibilityService.evaluate("drive-1", "student-1");

        assertThat(response.eligible()).isTrue();
        assertThat(response.failureReasons()).isEmpty();
    }

    @Test
    void returnsFailureReasonsWhenHardCriteriaFail() {
        Drive drive = Drive.builder()
            .id("drive-1")
            .minimumCgpa(new BigDecimal("7.0"))
            .maximumBacklogs(0)
            .graduationYear(2026)
            .build();
        Student student = Student.builder()
            .id("student-1")
            .cgpa(new BigDecimal("6.8"))
            .backlogs(2)
            .graduationYear(2025)
            .build();
        when(driveRepository.findById("drive-1")).thenReturn(Optional.of(drive));
        when(studentRepository.findById("student-1")).thenReturn(Optional.of(student));

        EligibilityResponse response = eligibilityService.evaluate("drive-1", "student-1");

        assertThat(response.eligible()).isFalse();
        assertThat(response.failureReasons()).hasSize(3);
    }
}
