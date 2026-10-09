package com.nexploy.campuslink.drive.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.dto.BulkUpsertDriveRequirementsRequest;
import com.nexploy.campuslink.drive.dto.UpsertDriveSkillRequirementRequest;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.repository.DriveSkillRequirementRepository;
import java.time.OffsetDateTime;
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

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DriveSkillRequirementEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private DriveSkillRequirementRepository requirementRepository;

    @BeforeEach
    void setup() {
        requirementRepository.deleteAll();
        driveRepository.deleteAll();
    }

    private Drive createDrive(String id, String campusId) {
        return driveRepository.save(Drive.builder()
            .id(id).campusId(campusId).companyName("Tech Corp").role("Backend Engineer")
            .status(DriveStatus.ACTIVE).driveType("On-Campus")
            .description("Looking for Java and Spring Boot engineers with SQL experience.")
            .requiredSkillsJson("[\"Java\",\"Spring Boot\"]").build());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role).password("{noop}pass").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void officerCanCreateAndListRequirements() throws Exception {
        Drive drive = createDrive("drv_1", "campus_a");

        UpsertDriveSkillRequirementRequest request = new UpsertDriveSkillRequirementRequest(
            "Java", 80, 3, true, "OFFICER", null, true
        );

        mockMvc.perform(post("/api/drives/drv_1/skill-requirements")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.skill").value("Java"))
            .andExpect(jsonPath("$.requiredLevel").value(80))
            .andExpect(jsonPath("$.approved").value(true));

        mockMvc.perform(get("/api/drives/drv_1/skill-requirements")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].skill").value("Java"));
    }

    @Test
    void studentCannotMutateRequirements() throws Exception {
        createDrive("drv_2", "campus_a");

        UpsertDriveSkillRequirementRequest request = new UpsertDriveSkillRequirementRequest(
            "Docker", 70, 2, true, "OFFICER", null, true
        );

        mockMvc.perform(post("/api/drives/drv_2/skill-requirements")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCanTriggerAiExtractionAndBulkSave() throws Exception {
        createDrive("drv_ai", "campus_a");

        // Trigger AI extraction (fallback or LLM)
        mockMvc.perform(post("/api/drives/drv_ai/skill-requirements/extract-ai")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.extractedSkills.length()").isNotEmpty());

        // Bulk upsert / review save
        BulkUpsertDriveRequirementsRequest bulk = new BulkUpsertDriveRequirementsRequest(List.of(
            new UpsertDriveSkillRequirementRequest("Java", 85, 3, true, "AI_EXTRACTED", 95, true),
            new UpsertDriveSkillRequirementRequest("PostgreSQL", 70, 2, false, "AI_EXTRACTED", 85, true)
        ));

        mockMvc.perform(post("/api/drives/drv_ai/skill-requirements/bulk")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bulk)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }
}
