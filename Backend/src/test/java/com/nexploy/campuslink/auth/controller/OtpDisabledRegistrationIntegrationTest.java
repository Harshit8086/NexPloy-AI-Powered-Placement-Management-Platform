package com.nexploy.campuslink.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.otp.repository.OtpVerificationRepository;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The no-SMTP prototype fallback of the V60 OTP layer. Split out of
 * {@link DirectoryRegistrationOtpIntegrationTest} because {@code @TestPropertySource} on a
 * {@code @Nested} class never overrides the enclosing context's properties. With
 * {@code nexploy.auth.otp.enabled=false}, send reports {@code otpRequired:false} and registration
 * succeeds without a code, so a demo without SMTP credentials still works.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = "nexploy.auth.otp.enabled=false")
class OtpDisabledRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @BeforeEach
    void seed() {
        otpRepository.deleteAll();
        userAccountRepository.deleteAll();
        studentRepository.deleteAll();
        campusRepository.deleteAll();
        campusRepository.save(Campus.builder()
            .id("campus_a").name("Campus A").city("Bengaluru").state("KA").active(true).build());
        studentRepository.save(Student.builder()
            .id("stu_campus_a_priya_student_example")
            .campusId("campus_a").name("Priya Verma").email("priya@student.example")
            .rollNumber("EC-2026-007").branch("ECE").graduationYear(2026)
            .cgpa(new BigDecimal("8.4")).backlogs(0)
            .source("DIRECTORY").active(true).readinessScore(0).placementStatus("UNPLACED").build());
    }

    @Test
    void registrationSucceedsWithoutOtpWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campusId\": \"campus_a\", \"email\": \"priya@student.example\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.otpRequired").value(false));

        mockMvc.perform(post("/api/auth/register/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "priya@student.example", "password": "s3cretpass",
                      "campusId": "campus_a" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
        assertThat(studentRepository.findAll()).hasSize(1);
        assertThat(studentRepository.findAll().get(0).getSource()).isEqualTo("DIRECTORY");
    }
}
