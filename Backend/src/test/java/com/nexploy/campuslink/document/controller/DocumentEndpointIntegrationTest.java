package com.nexploy.campuslink.document.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.document.dto.RegisterDocumentRequest;
import com.nexploy.campuslink.document.dto.ReviewDocumentRequest;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.DocumentType;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.entity.ScanStatus;
import com.nexploy.campuslink.document.repository.DocumentFileRepository;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
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
 * End-to-end HTTP integration test for the document endpoints, focused on the SECURITY.md section 3
 * guards: restricted content types, and the rule that a document cannot be approved until its
 * malware scan is CLEAN. Runs through real routing + the security filter chain against in-memory
 * H2 (the "test" profile). Covers the register → scan → approve happy path, the not-CLEAN approval
 * block (409), unsupported content type (400), officer-only review (403), and campus scope.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DocumentEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentFileRepository documentFileRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        // File rows reference documents via the shared PK — clear children before parents.
        documentFileRepository.deleteAll();
        documentRepository.deleteAll();
        // Officer/student registration validates that the target student exists on an accessible
        // campus; ensure the fixture student is present (idempotent on its id).
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Student One").email("stu1@campus_a.edu").build());
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Document seed(String id, String campusId, ScanStatus scan) {
        return documentRepository.save(Document.builder()
            .id(id).campusId(campusId).studentId("stu_1").type(DocumentType.RESUME)
            .fileName("resume.pdf").contentType("application/pdf").sizeBytes(2048)
            .storageKey("s3://bucket/" + id).scanStatus(scan).reviewStatus(ReviewStatus.PENDING)
            .uploadedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void registerScanThenApproveHappyPath() throws Exception {
        RegisterDocumentRequest request = new RegisterDocumentRequest(
            "stu_1", DocumentType.RESUME, "resume.pdf", "application/pdf", 2048, "s3://bucket/doc");

        String body = mockMvc.perform(post("/api/documents")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scanStatus").value("PENDING"))
            .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
            // Registration is metadata-only; bytes arrive via POST /{id}/file.
            .andExpect(jsonPath("$.hasFile").value(false))
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mockMvc.perform(post("/api/documents/" + id + "/scan")
                .param("status", "CLEAN")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scanStatus").value("CLEAN"));

        ReviewDocumentRequest review = new ReviewDocumentRequest(ReviewStatus.APPROVED);
        mockMvc.perform(post("/api/documents/" + id + "/review")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(review)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
            .andExpect(jsonPath("$.reviewedBy").value("u-OFFICER-campus_a"));
    }

    @Test
    void approvingBeforeScanIsCleanIsRejected() throws Exception {
        seed("doc_1", "campus_a", ScanStatus.PENDING);
        ReviewDocumentRequest review = new ReviewDocumentRequest(ReviewStatus.APPROVED);

        // Scan not CLEAN -> DocumentNotReviewableException -> 409.
        mockMvc.perform(post("/api/documents/doc_1/review")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(review)))
            .andExpect(status().isConflict());
    }

    @Test
    void infectedDocumentCanBeRejectedButNeverApproved() throws Exception {
        seed("doc_1", "campus_a", ScanStatus.INFECTED);

        // Rejecting an infected document is allowed.
        mockMvc.perform(post("/api/documents/doc_1/review")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ReviewDocumentRequest(ReviewStatus.REJECTED))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));

        // But approving it is blocked.
        seed("doc_2", "campus_a", ScanStatus.INFECTED);
        mockMvc.perform(post("/api/documents/doc_2/review")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ReviewDocumentRequest(ReviewStatus.APPROVED))))
            .andExpect(status().isConflict());
    }

    @Test
    void unsupportedContentTypeIsRejected() throws Exception {
        RegisterDocumentRequest request = new RegisterDocumentRequest(
            "stu_1", DocumentType.OTHER, "malware.exe", "application/x-msdownload", 2048, "s3://bucket/x");

        mockMvc.perform(post("/api/documents")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void recruiterCannotReviewDocuments() throws Exception {
        seed("doc_1", "campus_a", ScanStatus.CLEAN);

        mockMvc.perform(post("/api/documents/doc_1/review")
                .with(authentication(as(UserRole.RECRUITER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ReviewDocumentRequest(ReviewStatus.APPROVED))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recruiterCannotReadDocumentFromAnotherCampus() throws Exception {
        seed("doc_1", "campus_a", ScanStatus.CLEAN);

        mockMvc.perform(get("/api/documents/doc_1")
                .with(authentication(as(UserRole.RECRUITER, "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recruiterCannotRegisterDocument() throws Exception {
        RegisterDocumentRequest request = new RegisterDocumentRequest(
            "stu_1", DocumentType.RESUME, "resume.pdf", "application/pdf", 2048, "s3://bucket/doc");

        mockMvc.perform(post("/api/documents")
                .with(authentication(as(UserRole.RECRUITER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentRegisteringIsPinnedToOwnRecord() throws Exception {
        studentRepository.save(Student.builder()
            .id("stu_self").campusId("campus_a").name("Self").email("u-STUDENT-campus_a").build());
        // Body names another student; the service must ignore it and pin to the caller's own record.
        RegisterDocumentRequest request = new RegisterDocumentRequest(
            "stu_1", DocumentType.RESUME, "resume.pdf", "application/pdf", 2048, "s3://bucket/doc");

        mockMvc.perform(post("/api/documents")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.studentId").value("stu_self"));
    }

    @Test
    void studentCannotRecordScan() throws Exception {
        seed("doc_1", "campus_a", ScanStatus.PENDING);

        mockMvc.perform(post("/api/documents/doc_1/scan")
                .param("status", "CLEAN")
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void attachFileThenStreamRoundTrip() throws Exception {
        Document seeded = seed("doc_1", "campus_a", ScanStatus.CLEAN);
        // Simulate a prior approval that the fresh bytes must invalidate.
        seeded.setReviewStatus(ReviewStatus.APPROVED);
        seeded.setReviewedBy("u-OFFICER-campus_a");
        documentRepository.save(seeded);
        byte[] pdfBytes = "%PDF-1.4 test document".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/documents/doc_1/file")
                .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes))
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasFile").value(true))
            .andExpect(jsonPath("$.scanStatus").value("PENDING"))
            .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
            .andExpect(jsonPath("$.reviewedBy").doesNotExist());

        // The officer can now see exactly what was submitted.
        mockMvc.perform(get("/api/documents/doc_1/file")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void getFileForMetadataOnlyDocumentIs404() throws Exception {
        seed("doc_1", "campus_a", ScanStatus.PENDING);

        // Legacy registration with no bytes attached — nothing to preview.
        mockMvc.perform(get("/api/documents/doc_1/file")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void attachUnsupportedFileTypeIsRejected() throws Exception {
        seed("doc_1", "campus_a", ScanStatus.PENDING);

        mockMvc.perform(multipart("/api/documents/doc_1/file")
                .file(new MockMultipartFile("file", "malware.exe", "application/x-msdownload", new byte[] {1}))
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotAttachFileToAnotherStudentsDocument() throws Exception {
        studentRepository.save(Student.builder()
            .id("stu_self").campusId("campus_a").name("Self").email("u-STUDENT-campus_a").build());
        seed("doc_1", "campus_a", ScanStatus.PENDING);

        // doc_1 belongs to stu_1; the signed-in student resolves to stu_self and must be denied.
        mockMvc.perform(multipart("/api/documents/doc_1/file")
                .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] {1}))
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isForbidden());
    }
}
