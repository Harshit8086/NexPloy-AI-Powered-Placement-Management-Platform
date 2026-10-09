package com.nexploy.campuslink.drive.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.dto.ChangeDriveStatusRequest;
import com.nexploy.campuslink.drive.dto.CreateDriveRequest;
import com.nexploy.campuslink.drive.dto.UpdateDriveRequest;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.placement.repository.PlacementCycleRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import java.math.BigDecimal;
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
 * HTTP integration tests for the drive CRUD endpoints (POST create, PUT update, PATCH status).
 * Mirrors {@code InterviewEndpointIntegrationTest}: real routing + security filter chain against
 * in-memory H2, seeding via repositories and authenticating with a {@code UserAccount} principal.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DriveEndpointIntegrationTest {

    private static final String RECRUITER_EMAIL = "rec@a.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private PlacementCycleRepository placementCycleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        driveRepository.deleteAll();
        placementCycleRepository.deleteAll();
        recruiterRepository.deleteAll();
        recruiterRepository.save(Recruiter.builder()
            .id("rec_a").campusId("campus_a").name("Ann Recruiter").companyName("TechNova")
            .email(RECRUITER_EMAIL).active(true).build());
    }

    private Authentication as(UserRole role, String campusId) {
        return asUser(role, campusId, "u-" + role + "-" + campusId);
    }

    private Authentication asUser(UserRole role, String campusId, String username) {
        UserAccount user = UserAccount.builder()
            .username(username).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Drive seedDrive(String id, String campusId, String recruiterId, DriveStatus status) {
        return driveRepository.save(Drive.builder()
            .id(id).campusId(campusId).recruiterId(recruiterId).companyName("TechNova").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(status).build());
    }

    private CreateDriveRequest createRequest(String recruiterId, String companyName, BigDecimal packageLpa) {
        return new CreateDriveRequest(recruiterId, companyName, "SDE", "Bengaluru", packageLpa,
            null, null, null, 2026, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void officerCreatesDrive() throws Exception {
        mockMvc.perform(post("/api/drives")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    createRequest("rec_a", "TechNova", new BigDecimal("18.5")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recruiterId").value("rec_a"))
            .andExpect(jsonPath("$.campusId").value("campus_a"))
            .andExpect(jsonPath("$.companyName").value("TechNova"))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void recruiterCreatesOwnDrive() throws Exception {
        mockMvc.perform(post("/api/drives")
                .with(authentication(asUser(UserRole.RECRUITER, "campus_a", RECRUITER_EMAIL)))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    createRequest("rec_a", "TechNova", new BigDecimal("10.0")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createMissingCompanyNameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/drives")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    createRequest("rec_a", null, new BigDecimal("10.0")))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createNonPositivePackageIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/drives")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    createRequest("rec_a", "TechNova", new BigDecimal("0")))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void statusTransitionDraftToActive() throws Exception {
        seedDrive("drv_1", "campus_a", "rec_a", DriveStatus.DRAFT);

        mockMvc.perform(patch("/api/drives/drv_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeDriveStatusRequest(DriveStatus.ACTIVE))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void illegalStatusTransitionIsConflict() throws Exception {
        // DRAFT may only move to ACTIVE or CANCELLED — DRAFT -> COMPLETED is illegal.
        seedDrive("drv_1", "campus_a", "rec_a", DriveStatus.DRAFT);

        mockMvc.perform(patch("/api/drives/drv_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeDriveStatusRequest(DriveStatus.COMPLETED))))
            .andExpect(status().isConflict());
    }

    @Test
    void studentCannotCreateDrive() throws Exception {
        mockMvc.perform(post("/api/drives")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    createRequest("rec_a", "TechNova", new BigDecimal("10.0")))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recruiterOnAnotherCampusCannotCreateDrive() throws Exception {
        // Recruiter user on campus_b targeting rec_a (campus_a) fails the campus-access check.
        mockMvc.perform(post("/api/drives")
                .with(authentication(asUser(UserRole.RECRUITER, "campus_b", "other@b.example")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    createRequest("rec_a", "TechNova", new BigDecimal("10.0")))))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownDriveStatusChangeIsNotFound() throws Exception {
        mockMvc.perform(patch("/api/drives/missing/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeDriveStatusRequest(DriveStatus.ACTIVE))))
            .andExpect(status().isNotFound());
    }

    @Test
    void officerUpdatesDrive() throws Exception {
        seedDrive("drv_1", "campus_a", "rec_a", DriveStatus.DRAFT);
        UpdateDriveRequest request = new UpdateDriveRequest("rec_a", "TechNova Labs", "Backend SDE",
            "Pune", new BigDecimal("20.0"), null, null, null, 2026, null, null, null, null, null,
            null, null, null, null, null);

        mockMvc.perform(put("/api/drives/drv_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.companyName").value("TechNova Labs"))
            .andExpect(jsonPath("$.role").value("Backend SDE"))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ---- Opt-in pagination contract (G1) --------------------------------------

    @Test
    void listWithoutPagingParamsReturnsBareArray() throws Exception {
        seedDrive("drv_1", "campus_a", "rec_a", DriveStatus.DRAFT);
        seedDrive("drv_2", "campus_a", "rec_a", DriveStatus.DRAFT);

        mockMvc.perform(get("/api/drives")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listWithPagingParamsReturnsEnvelope() throws Exception {
        seedDrive("drv_1", "campus_a", "rec_a", DriveStatus.DRAFT);
        seedDrive("drv_2", "campus_a", "rec_a", DriveStatus.DRAFT);

        mockMvc.perform(get("/api/drives?page=0&size=1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void oversizePageSizeIsCapped() throws Exception {
        seedDrive("drv_1", "campus_a", "rec_a", DriveStatus.DRAFT);

        mockMvc.perform(get("/api/drives?size=500")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(100));
    }
}
