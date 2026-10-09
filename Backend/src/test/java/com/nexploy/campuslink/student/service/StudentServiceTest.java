package com.nexploy.campuslink.student.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.student.dto.StudentProject;
import com.nexploy.campuslink.student.dto.StudentResponse;
import com.nexploy.campuslink.student.dto.StudentUpdateRequest;
import com.nexploy.campuslink.student.entity.Student;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentServiceTest {

    private final StudentService service = new StudentService(new ObjectMapper());

    @Test
    void appliesProjectsAndRoundTripsThroughResponse() {
        Student student = Student.builder().id("stu_1").campusId("campus_a").build();

        service.applyUpdate(student, update(List.of(
            new StudentProject("Placement Portal", List.of("React", "Spring Boot"), "Campus hiring app",
                "https://github.com/demo/portal", null))));

        // Serialized into the projectsJson column, then parsed back by toResponse.
        assertThat(student.getProjectsJson()).contains("Placement Portal", "Spring Boot");
        StudentResponse response = service.toResponse(student);
        assertThat(response.projects()).hasSize(1);
        assertThat(response.projects().get(0).name()).isEqualTo("Placement Portal");
        assertThat(response.projects().get(0).stack()).containsExactly("React", "Spring Boot");
        assertThat(response.projects().get(0).githubUrl()).isEqualTo("https://github.com/demo/portal");
    }

    @Test
    void nullProjectsLeavesExistingColumnUntouched() {
        Student student = Student.builder().id("stu_1").campusId("campus_a").build();
        student.setProjectsJson("[{\"name\":\"Existing\",\"stack\":[],\"summary\":\"\"}]");

        service.applyUpdate(student, update(null));

        assertThat(student.getProjectsJson()).contains("Existing");
    }

    /** A patch that touches only projects; every other field is null (unchanged). */
    private StudentUpdateRequest update(List<StudentProject> projects) {
        return new StudentUpdateRequest(
            null, null, null, null, null, null, projects, null, null, null);
    }
}
