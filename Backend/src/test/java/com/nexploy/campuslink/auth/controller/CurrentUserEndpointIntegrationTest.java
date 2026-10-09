package com.nexploy.campuslink.auth.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
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
class CurrentUserEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meReturnsTheAuthenticatedPrincipalsIdentity() throws Exception {
        UserAccount mentor = UserAccount.builder()
            .username("mentor-anita")
            .password("{noop}secret")
            .campusId("campus_demo")
            .role(UserRole.MENTOR)
            .enabled(true)
            .build();

        mockMvc.perform(get("/api/auth/me")
                .with(authentication(new UsernamePasswordAuthenticationToken(mentor, null, mentor.getAuthorities()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("mentor-anita"))
            .andExpect(jsonPath("$.role").value("MENTOR"))
            .andExpect(jsonPath("$.campusId").value("campus_demo"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isForbidden());
    }
}
