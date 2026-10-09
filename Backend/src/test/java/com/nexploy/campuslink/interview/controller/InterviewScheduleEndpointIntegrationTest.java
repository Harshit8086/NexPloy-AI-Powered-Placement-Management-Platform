package com.nexploy.campuslink.interview.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.interview.dto.RecordResultRequest;
import com.nexploy.campuslink.interview.dto.ScheduleInterviewRequest;
import com.nexploy.campuslink.interview.dto.UpdateInterviewRequest;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewMode;
import com.nexploy.campuslink.interview.entity.InterviewResult;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
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
 * HTTP integration tests for interview scheduling / rescheduling / cancellation, plus the RBAC
 * on recording a result. Complements {@code InterviewEndpointIntegrationTest} (left untouched).
 * The key regression: POST /api/interviews/{id}/result follows the recruiter-owns-drive rule —
 * the owning recruiter may record, a recruiter from another drive is forbidden.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class InterviewScheduleEndpointIntegrationTest {

    private static final String RECRUITER_EMAIL = "rec@a.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        interviewRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();
        recruiterRepository.deleteAll();

        recruiterRepository.save(Recruiter.builder()
            .id("rec_a").campusId("campus_a").name("Ann Recruiter").companyName("TechNova")
            .email(RECRUITER_EMAIL).active(true).build());
        driveRepository.save(Drive.builder()
            .id("drv_1").campusId("campus_a").recruiterId("rec_a").companyName("TechNova").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(DriveStatus.ACTIVE).build());
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("student@campus.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).placementStatus("UNPLACED").build());
    }

    private Authentication as(UserRole role, String campusId, String username) {
        UserAccount user = UserAccount.builder()
            .username(username).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Interview seedInterview(String id, InterviewStatus status) {
        return interviewRepository.save(Interview.builder()
            .id(id).campusId("campus_a").studentId("stu_1").driveId("drv_1")
            .scheduleEventId("evt_1").mode(InterviewMode.ON_CAMPUS)
            .scheduledAt(OffsetDateTime.parse("2026-02-01T09:00:00Z"))
            .status(status).result(InterviewResult.PENDING)
            .createdAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void officerSchedulesInterview() throws Exception {
        ScheduleInterviewRequest request = new ScheduleInterviewRequest(
            "stu_1", "drv_1", InterviewMode.ON_CAMPUS,
            OffsetDateTime.parse("2026-03-01T09:00:00Z"),
            "Round 1", "Room A", "Panel 1", "evt_9", "TechNova");

        mockMvc.perform(post("/api/interviews")
                .with(authentication(as(UserRole.OFFICER, "campus_a", "officer@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.campusId").value("campus_a"))
            .andExpect(jsonPath("$.studentId").value("stu_1"))
            .andExpect(jsonPath("$.driveId").value("drv_1"))
            .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void officerReschedulesInterview() throws Exception {
        seedInterview("iv_1", InterviewStatus.SCHEDULED);
        UpdateInterviewRequest request = new UpdateInterviewRequest(
            OffsetDateTime.parse("2026-04-01T14:00:00Z"), InterviewMode.VIRTUAL,
            "Round 2", "Zoom", "Panel 2");

        mockMvc.perform(patch("/api/interviews/iv_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a", "officer@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("VIRTUAL"))
            .andExpect(jsonPath("$.round").value("Round 2"));
    }

    @Test
    void officerCancelsInterview() throws Exception {
        seedInterview("iv_1", InterviewStatus.SCHEDULED);

        mockMvc.perform(patch("/api/interviews/iv_1/cancel")
                .with(authentication(as(UserRole.OFFICER, "campus_a", "officer@a.example"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void recruiterCanRecordResultForOwnDrive() throws Exception {
        // RBAC: recording a result follows the same recruiter-owns-drive rule as scheduling —
        // the recruiter owns drv_1, so they may record the outcome of its interviews.
        seedInterview("iv_1", InterviewStatus.SCHEDULED);

        mockMvc.perform(post("/api/interviews/iv_1/result")
                .with(authentication(as(UserRole.RECRUITER, "campus_a", RECRUITER_EMAIL)))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new RecordResultRequest(InterviewResult.SELECTED, "Strong"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result").value("SELECTED"));
    }

    @Test
    void recruiterCannotRecordResultForAnotherRecruitersDrive() throws Exception {
        recruiterRepository.save(Recruiter.builder()
            .id("rec_b").campusId("campus_a").name("Bob Recruiter").companyName("OtherCo")
            .email("rec2@a.example").active(true).build());
        seedInterview("iv_1", InterviewStatus.SCHEDULED);

        mockMvc.perform(post("/api/interviews/iv_1/result")
                .with(authentication(as(UserRole.RECRUITER, "campus_a", "rec2@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new RecordResultRequest(InterviewResult.SELECTED, "Strong"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCanRecordResult() throws Exception {
        seedInterview("iv_1", InterviewStatus.SCHEDULED);

        mockMvc.perform(post("/api/interviews/iv_1/result")
                .with(authentication(as(UserRole.OFFICER, "campus_a", "officer@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new RecordResultRequest(InterviewResult.SELECTED, "Strong"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result").value("SELECTED"));
    }
}
