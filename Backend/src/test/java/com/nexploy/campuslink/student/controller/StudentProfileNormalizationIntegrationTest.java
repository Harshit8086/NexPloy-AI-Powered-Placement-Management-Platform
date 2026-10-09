package com.nexploy.campuslink.student.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentProjectEntry;
import com.nexploy.campuslink.student.repository.StudentProjectEntryRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for the profile-edit dual-write (V40). A PUT to /api/students/me with
 * projects must both (a) round-trip through the retained JSON columns in the response, and
 * (b) be mirrored into the normalized student_projects table. A second edit fully replaces the
 * normalized rows. Runs against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StudentProfileNormalizationIntegrationTest {

    private static final String EMAIL = "normalize@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentProjectEntryRepository projectRepository;

    @BeforeEach
    void seed() {
        projectRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_norm").campusId("campus_a").name("Norm").email(EMAIL)
            .rollNumber("R-9").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.0")).backlogs(0).readinessScore(75)
            .placementStatus("ACTIVE").build());
    }

    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void editMirrorsProjectsIntoNormalizedTable() throws Exception {
        String body = """
            {"projects":[{"name":"Placement Portal","stack":["React","Spring Boot"],
                          "summary":"Campus hiring app",
                          "githubUrl":"https://github.com/demo/portal",
                          "liveUrl":"https://portal.demo"}]}""";

        mockMvc.perform(put("/api/students/me")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            // JSON columns still back the response (retained, backward-compatible).
            .andExpect(jsonPath("$.projects[0].name").value("Placement Portal"))
            .andExpect(jsonPath("$.projects[0].stack[1]").value("Spring Boot"))
            .andExpect(jsonPath("$.projects[0].githubUrl").value("https://github.com/demo/portal"))
            .andExpect(jsonPath("$.projects[0].liveUrl").value("https://portal.demo"));

        List<StudentProjectEntry> projects = projectRepository.findByStudentIdOrderBySortOrder("stu_norm");
        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getName()).isEqualTo("Placement Portal");
        assertThat(projects.get(0).getSummary()).isEqualTo("Campus hiring app");
        assertThat(projects.get(0).getStackJson()).contains("React", "Spring Boot");
        assertThat(projects.get(0).getGithubUrl()).isEqualTo("https://github.com/demo/portal");
        assertThat(projects.get(0).getLiveUrl()).isEqualTo("https://portal.demo");
    }

    @Test
    void secondEditFullyReplacesNormalizedRows() throws Exception {
        mockMvc.perform(put("/api/students/me")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projects\":[{\"name\":\"P1\"},{\"name\":\"P2\"},{\"name\":\"P3\"}]}"))
            .andExpect(status().isOk());
        assertThat(projectRepository.findByStudentIdOrderBySortOrder("stu_norm")).hasSize(3);

        mockMvc.perform(put("/api/students/me")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projects\":[{\"name\":\"P1\"}]}"))
            .andExpect(status().isOk());

        List<StudentProjectEntry> projects = projectRepository.findByStudentIdOrderBySortOrder("stu_norm");
        assertThat(projects).extracting(StudentProjectEntry::getName).containsExactly("P1");
    }

    @Test
    void nullListsLeaveNormalizedRowsUntouched() throws Exception {
        mockMvc.perform(put("/api/students/me")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projects\":[{\"name\":\"P1\"}]}"))
            .andExpect(status().isOk());

        // A patch that omits projects must not clear the mirrored rows.
        mockMvc.perform(put("/api/students/me")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"+91-90000-00000\"}"))
            .andExpect(status().isOk());

        assertThat(projectRepository.findByStudentIdOrderBySortOrder("stu_norm")).hasSize(1);
    }
}
