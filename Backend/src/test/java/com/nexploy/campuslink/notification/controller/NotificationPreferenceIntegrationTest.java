package com.nexploy.campuslink.notification.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.repository.NotificationPreferenceRepository;
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
 * Verifies the self-scoped notification-preference endpoints over HTTP: a user reads a full,
 * all-enabled default set, disables one type via PUT, and sees exactly that type come back disabled.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NotificationPreferenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @BeforeEach
    void reset() {
        preferenceRepository.deleteAll();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void preferencesDefaultToEveryTypeEnabled() throws Exception {
        mockMvc.perform(get("/api/notifications/preferences")
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(NotificationType.values().length))
            .andExpect(jsonPath("$[?(@.type == 'OFFER_ACCEPTED')].enabled").value(true))
            .andExpect(jsonPath("$[?(@.type == 'RISK_DETECTED')].enabled").value(true));
    }

    @Test
    void disablingATypePersistsAndIsReadBack() throws Exception {
        mockMvc.perform(put("/api/notifications/preferences")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content("{\"preferences\":[{\"type\":\"OFFER_ACCEPTED\",\"enabled\":false}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'OFFER_ACCEPTED')].enabled").value(false));

        mockMvc.perform(get("/api/notifications/preferences")
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'OFFER_ACCEPTED')].enabled").value(false))
            .andExpect(jsonPath("$[?(@.type == 'OFFER_EXTENDED')].enabled").value(true));
    }
}
