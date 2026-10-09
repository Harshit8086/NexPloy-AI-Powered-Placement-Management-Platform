package com.nexploy.campuslink.mentor.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.mentor.dto.CreateAssignmentRequest;
import com.nexploy.campuslink.mentor.dto.CreateNoteRequest;
import com.nexploy.campuslink.mentor.entity.MentorAssignment;
import com.nexploy.campuslink.mentor.repository.MentorAssignmentRepository;
import com.nexploy.campuslink.mentor.repository.MentorNoteRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the mentor endpoints, focused on the assignment-based
 * access rule from SECURITY.md section 2 ("mentors access assigned students only"). This is the
 * first authorization rule that is not campus-based. Runs through real routing + the security
 * filter chain against in-memory H2 (the "test" profile).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class MentorEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private MentorAssignmentRepository assignmentRepository;

    @Autowired
    private MentorNoteRepository noteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        noteRepository.deleteAll();
        assignmentRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("d@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.0")).backlogs(0).readinessScore(75)
            .placementStatus("ACTIVE").build());
    }

    private Authentication as(UserRole role, String username, String campusId) {
        UserAccount user = UserAccount.builder()
            .username(username).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void assign(String mentor, String studentId, String campusId) {
        assignmentRepository.save(MentorAssignment.builder()
            .id("a-" + mentor + "-" + studentId).mentorUsername(mentor).studentId(studentId)
            .campusId(campusId).assignedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void officerCanAssignMentorToStudent() throws Exception {
        CreateAssignmentRequest request = new CreateAssignmentRequest("mentor1", "stu_1");

        mockMvc.perform(post("/api/mentor/assignments")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mentorUsername").value("mentor1"))
            .andExpect(jsonPath("$.studentId").value("stu_1"));
    }

    @Test
    void mentorCannotAssign() throws Exception {
        CreateAssignmentRequest request = new CreateAssignmentRequest("mentor1", "stu_1");

        mockMvc.perform(post("/api/mentor/assignments")
                .with(authentication(as(UserRole.MENTOR, "mentor1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void assignedMentorCanAddAndReadNotes() throws Exception {
        assign("mentor1", "stu_1", "campus_a");
        CreateNoteRequest note = new CreateNoteRequest("Focus on system design prep");

        mockMvc.perform(post("/api/mentor/students/stu_1/notes")
                .with(authentication(as(UserRole.MENTOR, "mentor1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(note)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authorUsername").value("mentor1"))
            .andExpect(jsonPath("$.note").value("Focus on system design prep"));

        mockMvc.perform(get("/api/mentor/students/stu_1/notes")
                .with(authentication(as(UserRole.MENTOR, "mentor1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].note").value("Focus on system design prep"));
    }

    @Test
    void unassignedMentorCannotReadStudentNotes() throws Exception {
        assign("mentor1", "stu_1", "campus_a");

        // mentor2 has no assignment to stu_1 -> StudentNotAssignedException -> 403.
        mockMvc.perform(get("/api/mentor/students/stu_1/notes")
                .with(authentication(as(UserRole.MENTOR, "mentor2", "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCanReadAnyStudentNotesInAccessibleCampus() throws Exception {
        assign("mentor1", "stu_1", "campus_a");

        mockMvc.perform(get("/api/mentor/students/stu_1/notes")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk());
    }
}
