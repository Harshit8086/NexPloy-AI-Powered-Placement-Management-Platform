package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * End-to-end HTTP test for the self-service student-skills endpoint (Phase 1a). Exercises real
 * routing, validation, the security chain, and the email==username self-resolution: a student can
 * only ever read or mutate their own skills. Runs against in-memory H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StudentSkillEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentSkillRepository studentSkillRepository;

    @BeforeEach
    void seed() {
        studentSkillRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .placementStatus("ACTIVE").build());
    }

    /** Auth principal whose username equals the student's email, as STUDENT accounts are. */
    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    /** An OFFICER principal on the student's campus; officers are campus-scoped. */
    private Authentication asOfficer() {
        UserAccount user = UserAccount.builder()
            .username("officer@campus.example").password("{noop}secret").campusId("campus_a")
            .role(UserRole.OFFICER).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    /** A different STUDENT (not the owner of stu_1) — must not read another student's skills. */
    private Authentication asOtherStudent() {
        UserAccount user = UserAccount.builder()
            .username("other@campus.example").password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void officerReadsAnotherStudentsSkillsViaDirectoryEndpoint() throws Exception {
        // The student records a skill on themselves...
        mockMvc.perform(put("/api/students/me/skills")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skill\":\"Spring\",\"proficiency\":65}"))
            .andExpect(status().isOk());

        // ...and an officer sees it on the directory endpoint keyed by student id.
        mockMvc.perform(get("/api/students/stu_1/skills").with(authentication(asOfficer())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].skill").value("Spring"))
            .andExpect(jsonPath("$[0].proficiency").value(65));
    }

    @Test
    void studentCannotReadAnotherStudentsSkills() throws Exception {
        mockMvc.perform(get("/api/students/stu_1/skills").with(authentication(asOtherStudent())))
            .andExpect(status().isForbidden());
    }

    @Test
    void directoryReadOfUnknownStudentIsNotFound() throws Exception {
        mockMvc.perform(get("/api/students/stu_missing/skills").with(authentication(asOfficer())))
            .andExpect(status().isNotFound());
    }

    @Test
    void addsThenListsSkill() throws Exception {
        mockMvc.perform(put("/api/students/me/skills")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skill\":\"Java\",\"proficiency\":70}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skill").value("Java"))
            .andExpect(jsonPath("$.proficiency").value(70))
            .andExpect(jsonPath("$.source").value("SELF_REPORTED"));

        mockMvc.perform(get("/api/students/me/skills").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].skill").value("Java"));
    }

    @Test
    void reSubmittingSameSkillUpdatesInPlaceCaseInsensitively() throws Exception {
        mockMvc.perform(put("/api/students/me/skills")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skill\":\"Java\",\"proficiency\":40}"))
            .andExpect(status().isOk());

        // Same skill, different case + new level: updates the existing row, no duplicate.
        mockMvc.perform(put("/api/students/me/skills")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skill\":\"java\",\"proficiency\":90,\"source\":\"ASSESSMENT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proficiency").value(90));

        mockMvc.perform(get("/api/students/me/skills").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].proficiency").value(90))
            .andExpect(jsonPath("$[0].source").value("ASSESSMENT"));
    }

    @Test
    void deletesOwnSkill() throws Exception {
        String location = mockMvc.perform(put("/api/students/me/skills")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skill\":\"Docker\",\"proficiency\":50}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String skillId = location.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete("/api/students/me/skills/" + skillId).with(authentication(asStudent())))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/students/me/skills").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingUnknownSkillReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/students/me/skills/sk_missing").with(authentication(asStudent())))
            .andExpect(status().isNotFound());
    }

    @Test
    void proficiencyOutOfRangeIsRejected() throws Exception {
        mockMvc.perform(put("/api/students/me/skills")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skill\":\"Java\",\"proficiency\":150}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/skills"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void verifiedSkillLevelCanBeNudgedWithinRange() throws Exception {
        studentSkillRepository.save(StudentSkill.builder()
            .id("sk_v1").studentId("stu_1").campusId("campus_a").skill("Java")
            .proficiency(70).source("SELF_REPORTED").verificationStatus("VERIFIED")
            .updatedAt(OffsetDateTime.now()).build());

        mockMvc.perform(put("/api/students/me/skills/sk_v1/level")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\":80}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.proficiency").value(80))
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
    }

    @Test
    void largeVerifiedLevelJumpNeedsReViva() throws Exception {
        studentSkillRepository.save(StudentSkill.builder()
            .id("sk_v2").studentId("stu_1").campusId("campus_a").skill("Java")
            .proficiency(70).source("SELF_REPORTED").verificationStatus("VERIFIED")
            .updatedAt(OffsetDateTime.now()).build());

        mockMvc.perform(put("/api/students/me/skills/sk_v2/level")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\":95}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void nonVerifiedSkillCannotBeLevelAdjusted() throws Exception {
        studentSkillRepository.save(StudentSkill.builder()
            .id("sk_v3").studentId("stu_1").campusId("campus_a").skill("Java")
            .proficiency(70).source("SELF_REPORTED").verificationStatus("SELF_DECLARED")
            .updatedAt(OffsetDateTime.now()).build());

        mockMvc.perform(put("/api/students/me/skills/sk_v3/level")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\":80}"))
            .andExpect(status().isBadRequest());
    }
}
