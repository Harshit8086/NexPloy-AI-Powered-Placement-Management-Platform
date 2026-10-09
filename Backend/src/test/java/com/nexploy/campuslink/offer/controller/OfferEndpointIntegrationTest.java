package com.nexploy.campuslink.offer.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.offer.dto.CreateOfferRequest;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
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
 * End-to-end HTTP integration test for the offer lifecycle endpoints. Runs through real routing,
 * the security filter chain, and the guarded state machine against in-memory H2 (the "test"
 * profile) — no ML worker or Postgres needed. Covers extend/revoke authorization (officer/admin
 * or the recruiter who owns the drive), the accept transition, the guard that only EXTENDED
 * offers can be decided (409), and campus-scope plus role enforcement (403).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OfferEndpointIntegrationTest {

    private static final String RECRUITER_EMAIL = "rec@a.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OfferRepository offerRepository;

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
        offerRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();
        recruiterRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email("student@campus.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).placementStatus("UNPLACED").build());
        recruiterRepository.save(Recruiter.builder()
            .id("rec_1").campusId("campus_a").name("Ann Recruiter").companyName("TechNova")
            .email(RECRUITER_EMAIL).active(true).build());
        driveRepository.save(Drive.builder()
            .id("drive_1").campusId("campus_a").recruiterId("rec_1").companyName("TechNova").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(DriveStatus.ACTIVE).build());
    }

    private Authentication as(UserRole role, String campusId) {
        return as(role, campusId, "u-" + role + "-" + campusId);
    }

    private Authentication as(UserRole role, String campusId, String username) {
        UserAccount user = UserAccount.builder()
            .username(username).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Offer seedExtended(String id, String campusId) {
        return offerRepository.save(Offer.builder()
            .id(id).campusId(campusId).studentId("stu_1").driveId("drive_1").recruiterId("rec_1")
            .role("SDE").packageLpa(new BigDecimal("12.0")).status(OfferStatus.EXTENDED)
            .extendedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void officerCanExtendAnOffer() throws Exception {
        CreateOfferRequest request = new CreateOfferRequest(
            "stu_1", "drive_1", "rec_1", "SDE", new BigDecimal("15.5"),
            null, null, null, null, null, null);

        mockMvc.perform(post("/api/offers")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("EXTENDED"))
            .andExpect(jsonPath("$.campusId").value("campus_a"))
            .andExpect(jsonPath("$.packageLpa").value(15.5));
    }

    @Test
    void owningRecruiterCanExtendAnOffer() throws Exception {
        CreateOfferRequest request = new CreateOfferRequest(
            "stu_1", "drive_1", "rec_1", "SDE", new BigDecimal("15.5"),
            null, null, null, null, null, null);

        mockMvc.perform(post("/api/offers")
                .with(authentication(as(UserRole.RECRUITER, "campus_a", RECRUITER_EMAIL)))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("EXTENDED"))
            .andExpect(jsonPath("$.campusId").value("campus_a"));
    }

    @Test
    void recruiterWithoutProfileCannotExtendAnOffer() throws Exception {
        // A RECRUITER account whose username matches no recruiter profile is denied by the
        // recruiter-owns-drive rule, even on the drive's own campus.
        CreateOfferRequest request = new CreateOfferRequest(
            "stu_1", "drive_1", "rec_1", "SDE", new BigDecimal("15.5"),
            null, null, null, null, null, null);

        mockMvc.perform(post("/api/offers")
                .with(authentication(as(UserRole.RECRUITER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void nonOwningRecruiterCannotExtendAnOffer() throws Exception {
        recruiterRepository.save(Recruiter.builder()
            .id("rec_2").campusId("campus_a").name("Bob Recruiter").companyName("OtherCo")
            .email("rec2@a.example").active(true).build());
        CreateOfferRequest request = new CreateOfferRequest(
            "stu_1", "drive_1", "rec_1", "SDE", new BigDecimal("15.5"),
            null, null, null, null, null, null);

        mockMvc.perform(post("/api/offers")
                .with(authentication(as(UserRole.RECRUITER, "campus_a", "rec2@a.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void extendedOfferCanBeAccepted() throws Exception {
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.decidedBy").value("u-OFFICER-campus_a"));
    }

    @Test
    void acceptingAnAlreadyDecidedOfferIsRejected() throws Exception {
        offerRepository.save(Offer.builder()
            .id("offer_1").campusId("campus_a").studentId("stu_1").driveId("drive_1")
            .recruiterId("rec_1").role("SDE").packageLpa(new BigDecimal("12.0"))
            .status(OfferStatus.ACCEPTED)
            .extendedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z"))
            .decidedAt(OffsetDateTime.parse("2026-01-02T10:00:00Z")).decidedBy("someone").build());

        // Only EXTENDED offers can transition -> InvalidOfferTransitionException -> 409.
        mockMvc.perform(post("/api/offers/offer_1/decline")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isConflict());
    }

    @Test
    void recruiterCannotReadOfferFromAnotherCampus() throws Exception {
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(get("/api/offers/offer_1")
                .with(authentication(as(UserRole.RECRUITER, "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recruiterInSameCampusCannotAcceptAnOffer() throws Exception {
        // Campus membership is not enough: accept/decline is limited to the owning student or an
        // officer/admin. A recruiter on the same campus must not be able to decide the offer.
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.RECRUITER, "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCanRevokeAnExtendedOffer() throws Exception {
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(post("/api/offers/offer_1/revoke")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void owningRecruiterCanRevokeOwnDriveOffer() throws Exception {
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(post("/api/offers/offer_1/revoke")
                .with(authentication(as(UserRole.RECRUITER, "campus_a", RECRUITER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void nonOwningRecruiterCannotRevokeOffer() throws Exception {
        recruiterRepository.save(Recruiter.builder()
            .id("rec_2").campusId("campus_a").name("Bob Recruiter").companyName("OtherCo")
            .email("rec2@a.example").active(true).build());
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(post("/api/offers/offer_1/revoke")
                .with(authentication(as(UserRole.RECRUITER, "campus_a", "rec2@a.example"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownOfferReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/offers/missing")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }
}
