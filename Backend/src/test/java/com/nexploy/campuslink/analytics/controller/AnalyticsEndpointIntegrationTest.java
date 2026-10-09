package com.nexploy.campuslink.analytics.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
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
 * End-to-end HTTP integration test for the campus analytics endpoint. Verifies the JSON contract
 * shape the dashboard consumes, campus-scope enforcement, and the two deterministic definitions
 * the service promises: "placed" = holds an ACCEPTED offer (not the free-text placement status),
 * and offer acceptance rate = accepted / decided (accepted + declined). Runs against in-memory H2
 * (the "test" profile), no ML worker or Postgres needed.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AnalyticsEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private OfferRepository offerRepository;

    @BeforeEach
    void seed() {
        studentRepository.deleteAll();
        offerRepository.deleteAll();

        // Two students in campus_a; one holds an accepted offer -> placed.
        studentRepository.save(student("stu_1", "campus_a", 80));
        studentRepository.save(student("stu_2", "campus_a", 60));
        // A student in another campus that must never leak into campus_a's rollup.
        studentRepository.save(student("stu_other", "campus_b", 90));

        // campus_a offers: 1 accepted, 1 declined, 1 still extended.
        offerRepository.save(offer("o1", "campus_a", "stu_1", OfferStatus.ACCEPTED));
        offerRepository.save(offer("o2", "campus_a", "stu_2", OfferStatus.DECLINED));
        offerRepository.save(offer("o3", "campus_a", "stu_2", OfferStatus.EXTENDED));
    }

    private Student student(String id, String campusId, int readiness) {
        return Student.builder()
            .id(id).campusId(campusId).name("N-" + id).email(id + "@e.example")
            .rollNumber("R-" + id).branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.0")).backlogs(0).readinessScore(readiness)
            .placementStatus("ACTIVE").build();
    }

    private Offer offer(String id, String campusId, String studentId, OfferStatus status) {
        return Offer.builder()
            .id(id).campusId(campusId).studentId(studentId).driveId("drive_1").recruiterId("rec_1")
            .role("SDE").packageLpa(new BigDecimal("12.0")).status(status)
            .extendedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void placementSummaryReturnsCampusScopedRollupInContractShape() throws Exception {
        mockMvc.perform(get("/api/campuses/campus_a/analytics/placement-summary")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.campusId").value("campus_a"))
            .andExpect(jsonPath("$.studentCount").value(2))          // stu_other excluded
            .andExpect(jsonPath("$.placedStudentCount").value(1))    // only stu_1 accepted
            .andExpect(jsonPath("$.placementRate").value(0.5))       // 1 of 2
            .andExpect(jsonPath("$.averageReadinessScore").value(70.0)) // (80+60)/2
            .andExpect(jsonPath("$.offerCount").value(3))
            .andExpect(jsonPath("$.offerAcceptanceRate").value(0.5)) // 1 accepted / 2 decided
            .andExpect(jsonPath("$.offersByStatus.ACCEPTED").value(1))
            .andExpect(jsonPath("$.offersByStatus.DECLINED").value(1))
            .andExpect(jsonPath("$.offersByStatus.EXTENDED").value(1));
    }

    @Test
    void recruiterCannotReadAnotherCampusAnalytics() throws Exception {
        mockMvc.perform(get("/api/campuses/campus_a/analytics/placement-summary")
                .with(authentication(as(UserRole.RECRUITER, "campus_b"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/campuses/campus_a/analytics/placement-summary"))
            .andExpect(status().is4xxClientError());
    }
}
