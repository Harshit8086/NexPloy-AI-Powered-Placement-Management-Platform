package com.nexploy.campuslink.student.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentCertificateFileRepository;
import com.nexploy.campuslink.student.repository.StudentCertificateRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for the self-service student certificate module: multipart create/update with a
 * document upload, list, owner-scoped file download, delete, ownership isolation (foreign id -> 404),
 * and validation. Runs against in-memory H2. Mirrors {@link StudentSkillEndpointIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StudentCertificateEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";
    private static final byte[] PDF = "%PDF-1.4 fake certificate bytes".getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentCertificateRepository certificateRepository;
    @Autowired private StudentCertificateFileRepository certificateFileRepository;

    @BeforeEach
    void seed() {
        certificateFileRepository.deleteAll();
        certificateRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .placementStatus("ACTIVE").build());
    }

    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Authentication asOtherStudent() {
        UserAccount user = UserAccount.builder()
            .username("other@campus.example").password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "cert.pdf", "application/pdf", PDF);
    }

    /** Creates a certificate for the signed-in student and returns its generated id. */
    private String createCertificate() throws Exception {
        String body = mockMvc.perform(multipart("/api/students/me/certificates")
                .file(pdf())
                .param("title", "AWS Certified Cloud Practitioner")
                .param("type", "PROFESSIONAL_CERTIFICATION")
                .param("issuingOrganization", "Amazon Web Services")
                .param("credentialId", "ABC-123")
                .with(authentication(asStudent())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("AWS Certified Cloud Practitioner"))
            .andExpect(jsonPath("$.type").value("PROFESSIONAL_CERTIFICATION"))
            .andExpect(jsonPath("$.issuingOrganization").value("Amazon Web Services"))
            .andExpect(jsonPath("$.credentialId").value("ABC-123"))
            .andExpect(jsonPath("$.fileName").value("cert.pdf"))
            .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void createsListsAndDownloadsCertificate() throws Exception {
        String id = createCertificate();

        mockMvc.perform(get("/api/students/me/certificates").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id));

        byte[] downloaded = mockMvc.perform(
                get("/api/students/me/certificates/" + id + "/file").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);
    }

    @Test
    void updatesMetadataViaJsonWithoutTouchingFile() throws Exception {
        String id = createCertificate();

        mockMvc.perform(put("/api/students/me/certificates/" + id)
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"AWS CCP (renamed)\",\"type\":\"PROFESSIONAL_CERTIFICATION\","
                    + "\"issuingOrganization\":\"AWS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("AWS CCP (renamed)"))
            .andExpect(jsonPath("$.fileName").value("cert.pdf"));

        // The original document is still downloadable (metadata-only update kept the bytes).
        mockMvc.perform(get("/api/students/me/certificates/" + id + "/file").with(authentication(asStudent())))
            .andExpect(status().isOk());
    }

    @Test
    void replacesFileViaFileEndpoint() throws Exception {
        String id = createCertificate();
        byte[] replacement = "%PDF-1.4 replacement bytes".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/students/me/certificates/" + id + "/file")
                .file(new MockMultipartFile("file", "new.pdf", "application/pdf", replacement))
                .with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileName").value("new.pdf"));

        byte[] downloaded = mockMvc.perform(
                get("/api/students/me/certificates/" + id + "/file").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(replacement);
    }

    @Test
    void deletesOwnCertificate() throws Exception {
        String id = createCertificate();

        mockMvc.perform(delete("/api/students/me/certificates/" + id).with(authentication(asStudent())))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/students/me/certificates").with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/students/me/certificates/" + id + "/file").with(authentication(asStudent())))
            .andExpect(status().isNotFound());
    }

    @Test
    void otherStudentCannotSeeOrDownloadOrDeleteForeignCertificate() throws Exception {
        String id = createCertificate();

        // Another student's own listing is empty (self-scoped).
        mockMvc.perform(get("/api/students/me/certificates").with(authentication(asOtherStudent())))
            .andExpect(status().isNotFound());
        // Foreign id resolves to 404 for both file download and delete (no existence leak).
        mockMvc.perform(get("/api/students/me/certificates/" + id + "/file").with(authentication(asOtherStudent())))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/students/me/certificates/" + id).with(authentication(asOtherStudent())))
            .andExpect(status().isNotFound());
    }

    @Test
    void missingTitleIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/students/me/certificates")
                .file(pdf())
                .param("type", "OTHER")
                .param("issuingOrganization", "Org")
                .with(authentication(asStudent())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/students/me/certificates")
                .param("title", "No file")
                .param("type", "OTHER")
                .param("issuingOrganization", "Org")
                .with(authentication(asStudent())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/students/me/certificates")
                .file(new MockMultipartFile("file", "note.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8)))
                .param("title", "Bad type")
                .param("type", "OTHER")
                .param("issuingOrganization", "Org")
                .with(authentication(asStudent())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/certificates"))
            .andExpect(status().is4xxClientError());
    }
}
