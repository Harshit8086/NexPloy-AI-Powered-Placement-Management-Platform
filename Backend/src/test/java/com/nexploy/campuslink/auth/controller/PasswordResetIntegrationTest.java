package com.nexploy.campuslink.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.otp.entity.OtpVerification;
import com.nexploy.campuslink.auth.otp.repository.OtpVerificationRepository;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "nexploy.auth.otp.enabled=true")
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    private static final String EMAIL = "password-reset@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void unknownEmailGetsSameGenericResponseWithoutSendingMail() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(
                "If an account exists for this email, a verification code has been sent."));
        verifyNoInteractions(mailSender);
    }

    @BeforeEach
    void seedAccount() {
        otpRepository.deleteAll();
        userAccountRepository.deleteAll();
        campusRepository.findById("password_reset_campus").orElseGet(() -> campusRepository.save(Campus.builder()
            .id("password_reset_campus").name("Reset Campus")
            .city("Bengaluru").state("KA").active(true).build()));
        userAccountRepository.save(UserAccount.builder()
            .username(EMAIL)
            .password(passwordEncoder.encode("old-password"))
            .campusId("password_reset_campus")
            .role(UserRole.STUDENT)
            .enabled(true)
            .build());
    }

    @Test
    void emailsOneTimeCodeAndChangesPassword() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(
                "If an account exists for this email, a verification code has been sent."));

        org.mockito.ArgumentCaptor<SimpleMailMessage> messageCaptor =
            org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        String body = messageCaptor.getValue().getText();
        java.util.regex.Matcher codeMatcher = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        assertThat(codeMatcher.find()).isTrue();
        String code = codeMatcher.group(1);
        assertThat(otpRepository.findAll())
            .singleElement()
            .extracting(OtpVerification::getPurpose)
            .isEqualTo("PASSWORD_RESET");

        mockMvc.perform(post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","otp":"%s","newPassword":"new-password"}
                    """.formatted(EMAIL, code)))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        UserAccount updated = userAccountRepository.findByUsernameIgnoreCase(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches("new-password", updated.getPassword())).isTrue();
        assertThat(otpRepository.findAll()).singleElement()
            .extracting(OtpVerification::getConsumed).isEqualTo(true);
    }
}
