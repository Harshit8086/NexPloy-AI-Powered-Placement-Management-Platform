package com.nexploy.campuslink.auth.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AccessControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @BeforeEach
    void seed() {
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_demo")
            .campusId("campus_demo")
            .name("Demo Student")
            .email("demo@student.example")
            .rollNumber("DEMO-001")
            .branch("CSE")
            .branchCode("CS")
            .graduationYear(2026)
            .cgpa(new BigDecimal("8.2"))
            .backlogs(0)
            .readinessScore(80)
            .placementStatus("ACTIVE")
            .build());
    }

    @Test
    void recruiterCannotAccessStudentOutsideTheirCampus() throws Exception {
        UserAccount recruiter = UserAccount.builder()
            .username("recruiter-foreign")
            .password("{noop}secret")
            .campusId("campus_other")
            .role(UserRole.RECRUITER)
            .enabled(true)
            .build();

        mockMvc.perform(get("/api/students/stu_demo")
                .with(authentication(new UsernamePasswordAuthenticationToken(recruiter, null, recruiter.getAuthorities()))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCanAccessStudentInOwnCampus() throws Exception {
        UserAccount officer = UserAccount.builder()
            .username("officer")
            .password("{noop}secret")
            .campusId("campus_demo")
            .role(UserRole.OFFICER)
            .enabled(true)
            .build();

        mockMvc.perform(get("/api/students/stu_demo")
                .with(authentication(new UsernamePasswordAuthenticationToken(officer, null, officer.getAuthorities()))))
            .andExpect(status().isOk());
    }

    @Test
    void officerCannotAccessStudentInAnotherCampus() throws Exception {
        UserAccount officer = UserAccount.builder()
            .username("officer-foreign")
            .password("{noop}secret")
            .campusId("campus_other")
            .role(UserRole.OFFICER)
            .enabled(true)
            .build();

        mockMvc.perform(get("/api/students/stu_demo")
                .with(authentication(new UsernamePasswordAuthenticationToken(officer, null, officer.getAuthorities()))))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessStudentInAnyCampus() throws Exception {
        UserAccount admin = UserAccount.builder()
            .username("admin")
            .password("{noop}secret")
            .campusId("campus_other")
            .role(UserRole.ADMIN)
            .enabled(true)
            .build();

        mockMvc.perform(get("/api/students/stu_demo")
                .with(authentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()))))
            .andExpect(status().isOk());
    }
}
