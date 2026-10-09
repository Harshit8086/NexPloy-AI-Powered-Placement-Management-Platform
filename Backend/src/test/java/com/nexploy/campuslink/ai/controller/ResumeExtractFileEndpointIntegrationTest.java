package com.nexploy.campuslink.ai.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP integration test for the resume file-upload extraction endpoint. Runs with the default
 * (rule-based) provider so it is deterministic and makes no LLM call: it verifies multipart
 * routing, server-side text extraction, the security chain, and the schema-shaped response.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ResumeExtractFileEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private Authentication as(UserRole role) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role).password("{noop}s").campusId("campus_a")
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void extractsSkillsFromAnUploadedTextResume() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain",
            "Backend engineer skilled in Java, Spring Boot and PostgreSQL. B.Tech in CSE."
                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/ai/resume/extract-file")
                .file(file)
                .with(authentication(as(UserRole.OFFICER))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skills[*].normalizedValue").value(org.hamcrest.Matchers.hasItem("Java")))
            .andExpect(jsonPath("$.metadata.provider").value("rule-based"));
    }

    @Test
    void rejectsUnsupportedFileTypeAsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/ai/resume/extract-file")
                .file(file)
                .with(authentication(as(UserRole.OFFICER))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedUploadIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain", "Java".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/ai/resume/extract-file").file(file))
            .andExpect(status().is4xxClientError());
    }
}
