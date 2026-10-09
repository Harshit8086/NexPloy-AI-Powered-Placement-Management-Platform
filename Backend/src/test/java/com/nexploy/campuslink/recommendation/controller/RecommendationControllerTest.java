package com.nexploy.campuslink.recommendation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.matching.entity.MatchResult;
import com.nexploy.campuslink.matching.repository.MatchResultRepository;
import com.nexploy.campuslink.recommendation.repository.RecommendationExplanationRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * End-to-end HTTP integration test for the recommendation endpoints against in-memory H2, exercising
 * real routing, the security filter chain, and the access matrix. No LLM runs here ({@code
 * nexploy.ai.provider} is unset in the test profile), so the AI text degrades to the deterministic
 * FALLBACK — which is exactly the "AI unavailable must not break the endpoint" path.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RecommendationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private DriveRepository driveRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private MatchResultRepository matchResultRepository;
    @Autowired private RecommendationExplanationRepository explanationRepository;

    private static final String STUDENT_EMAIL = "owner@e.example";

    @BeforeEach
    void seed() {
        explanationRepository.deleteAll();
        matchResultRepository.deleteAll();
        applicationRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(STUDENT_EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80).placementStatus("ACTIVE").build());
        driveRepository.save(Drive.builder()
            .id("drv_1").campusId("campus_a").recruiterId("rec_1").companyName("Acme").role("SDE")
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(2).graduationYear(2026)
            .deadline(LocalDate.now().plusDays(7)).status(DriveStatus.ACTIVE).build());
        applicationRepository.save(Application.builder()
            .id("app_1").studentId("stu_1").driveId("drv_1").appliedOn(LocalDate.now())
            .status(ApplicationStatus.APPLIED).build());
        matchResultRepository.save(MatchResult.builder()
            .id("mr_1").campusId("campus_a").cycleId("current").studentId("stu_1").driveId("drv_1")
            .overallFit(80).eligibilityPassed(true)
            .breakdownJson("{\"eligibility\":100,\"skills\":80,\"semanticMatch\":80,\"projects\":70,"
                + "\"interview\":60,\"readiness\":75,\"academics\":82}")
            .whySelectedJson("[\"Strong skills\"]").weakOrMissingJson("[\"Few projects\"]")
            .modelVersion("m1").computedAt(OffsetDateTime.now()).build());
    }

    @Test
    void officerGetsRecommendationWithDeterministicFallbackWhenAiDisabled() throws Exception {
        mockMvc.perform(get("/api/applications/app_1/recommendation")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicationId").value("app_1"))
            .andExpect(jsonPath("$.recommendationType").value("ELIGIBLE"))
            .andExpect(jsonPath("$.overallFit").value(80))
            .andExpect(jsonPath("$.aiExplanation").isNotEmpty())
            .andExpect(jsonPath("$.explanationStatus").value("FALLBACK"));
    }

    @Test
    void owningStudentMayReadOwnRecommendation() throws Exception {
        mockMvc.perform(get("/api/applications/app_1/recommendation")
                .with(authentication(asStudent(STUDENT_EMAIL, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.studentId").value("stu_1"));
    }

    @Test
    void nonOwnerStudentIsForbidden() throws Exception {
        mockMvc.perform(get("/api/applications/app_1/recommendation")
                .with(authentication(asStudent("someone-else@e.example", "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recruiterFromAnotherCampusIsForbidden() throws Exception {
        mockMvc.perform(get("/api/applications/app_1/recommendation")
                .with(authentication(as(UserRole.RECRUITER, "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownApplicationIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/applications/missing/recommendation")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void regeneratePostReturnsRecommendation() throws Exception {
        mockMvc.perform(post("/api/applications/app_1/recommendation/regenerate")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.explanationStatus").value("FALLBACK"));
    }

    @Test
    void candidateListIsOrderedAndServesPersistedOnly() throws Exception {
        mockMvc.perform(get("/api/drives/drv_1/candidate-recommendations")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].studentId").value("stu_1"))
            .andExpect(jsonPath("$[0].overallFit").value(80))
            // Persisted-only: no explanation has been generated for the drive list.
            .andExpect(jsonPath("$[0].aiExplanation").doesNotExist());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/applications/app_1/recommendation"))
            .andExpect(status().is4xxClientError());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Authentication asStudent(String email, String campusId) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId(campusId)
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
