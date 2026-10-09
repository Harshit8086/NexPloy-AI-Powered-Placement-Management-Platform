package com.nexploy.campuslink.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.document.dto.DocumentResponse;
import com.nexploy.campuslink.document.dto.RegisterDocumentRequest;
import com.nexploy.campuslink.document.dto.ReviewDocumentRequest;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.DocumentType;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.entity.ScanStatus;
import com.nexploy.campuslink.document.exception.DocumentFileMissingException;
import com.nexploy.campuslink.document.exception.DocumentNotReviewableException;
import com.nexploy.campuslink.document.exception.UnsupportedContentTypeException;
import com.nexploy.campuslink.document.repository.DocumentFileRepository;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DocumentServiceTest {

    private final DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
    private final DocumentFileRepository documentFileRepository = org.mockito.Mockito.mock(DocumentFileRepository.class);
    private final StudentRepository studentRepository = org.mockito.Mockito.mock(StudentRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final DocumentService service =
        new DocumentService(documentRepository, documentFileRepository, studentRepository, campusAccessService,
            org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));

    @Test
    void registerStartsScanAndReviewPending() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("stu_demo", "campus_demo")));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.register(new RegisterDocumentRequest(
            "stu_demo", DocumentType.RESUME, "cv.pdf", "application/pdf", 1024, "key/cv.pdf"));

        assertThat(response.scanStatus()).isEqualTo(ScanStatus.PENDING);
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(response.campusId()).isEqualTo("campus_demo");
    }

    @Test
    void studentRegisteringIsPinnedToTheirOwnRecord() {
        authenticate(user(UserRole.STUDENT, "campus_demo"));
        when(studentRepository.findByEmail("user-STUDENT")).thenReturn(Optional.of(student("stu_self", "campus_demo")));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Body asks for someone else's id; the service must ignore it and pin to the caller.
        DocumentResponse response = service.register(new RegisterDocumentRequest(
            "stu_victim", DocumentType.RESUME, "cv.pdf", "application/pdf", 1024, "key/cv.pdf"));

        assertThat(response.studentId()).isEqualTo("stu_self");
    }

    @Test
    void recruiterCannotRegisterDocument() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));

        assertThatThrownBy(() -> service.register(new RegisterDocumentRequest(
            "stu_demo", DocumentType.RESUME, "cv.pdf", "application/pdf", 1024, "key/cv.pdf")))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void studentCannotRecordScan() {
        authenticate(user(UserRole.STUDENT, "campus_demo"));

        assertThatThrownBy(() -> service.recordScan("doc_1", ScanStatus.CLEAN))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void officerCanRecordScan() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Document document = document("campus_demo", ScanStatus.PENDING);
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.recordScan("doc_1", ScanStatus.CLEAN);

        assertThat(response.scanStatus()).isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    void registerRejectsUnsupportedContentType() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));

        assertThatThrownBy(() -> service.register(new RegisterDocumentRequest(
            "stu_demo", DocumentType.OTHER, "malware.exe", "application/x-msdownload", 1024, "key/x")))
            .isInstanceOf(UnsupportedContentTypeException.class);
    }

    @Test
    void approvingUnscannedDocumentIsRejected() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Document document = document("campus_demo", ScanStatus.PENDING);
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.review("doc_1", new ReviewDocumentRequest(ReviewStatus.APPROVED)))
            .isInstanceOf(DocumentNotReviewableException.class);
    }

    @Test
    void approvingCleanDocumentSucceeds() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Document document = document("campus_demo", ScanStatus.CLEAN);
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.review("doc_1", new ReviewDocumentRequest(ReviewStatus.APPROVED));

        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo("user-OFFICER");
    }

    @Test
    void rejectingInfectedDocumentIsAllowed() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Document document = document("campus_demo", ScanStatus.INFECTED);
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.review("doc_1", new ReviewDocumentRequest(ReviewStatus.REJECTED));

        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void recruiterCannotReview() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));

        assertThatThrownBy(() -> service.review("doc_1", new ReviewDocumentRequest(ReviewStatus.APPROVED)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void recruiterCannotAccessDocumentOutsideCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document("campus_other", ScanStatus.CLEAN)));

        assertThatThrownBy(() -> service.get("doc_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void attachFileResetsScanAndReviewWorkflow() {
        authenticate(user(UserRole.STUDENT, "campus_demo"));
        Document document = document("campus_demo", ScanStatus.CLEAN);
        // Simulate a prior approval — fresh bytes must invalidate it.
        document.setReviewStatus(ReviewStatus.APPROVED);
        document.setReviewedBy("user-OFFICER");
        document.setReviewedAt(OffsetDateTime.parse("2026-09-20T12:00:00Z"));
        when(studentRepository.findByEmail("user-STUDENT")).thenReturn(Optional.of(student("stu_demo", "campus_demo")));
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentFileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.attachFile("doc_1", new MockMultipartFile(
            "file", "cv-updated.pdf", "application/pdf", new byte[] {1, 2, 3}));

        assertThat(response.scanStatus()).isEqualTo(ScanStatus.PENDING);
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(response.reviewedBy()).isNull();
        assertThat(response.reviewedAt()).isNull();
        assertThat(response.hasFile()).isTrue();
        // Metadata now reflects the real upload, not the registration payload.
        assertThat(response.fileName()).isEqualTo("cv-updated.pdf");
        assertThat(response.sizeBytes()).isEqualTo(3);
    }

    @Test
    void studentCannotAttachFileToAnotherStudentsDocument() {
        authenticate(user(UserRole.STUDENT, "campus_demo"));
        when(studentRepository.findByEmail("user-STUDENT")).thenReturn(Optional.of(student("stu_self", "campus_demo")));
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document("campus_demo", ScanStatus.PENDING)));

        // doc_1 belongs to stu_demo; the caller is stu_self — same campus is not enough.
        assertThatThrownBy(() -> service.attachFile("doc_1", new MockMultipartFile(
            "file", "cv.pdf", "application/pdf", new byte[] {1})))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void recruiterCannotAttachFile() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document("campus_demo", ScanStatus.PENDING)));

        assertThatThrownBy(() -> service.attachFile("doc_1", new MockMultipartFile(
            "file", "cv.pdf", "application/pdf", new byte[] {1})))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void loadingFileForDocumentWithoutBytesThrows() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(documentRepository.findById("doc_1")).thenReturn(Optional.of(document("campus_demo", ScanStatus.PENDING)));
        when(documentFileRepository.findById("doc_1")).thenReturn(Optional.empty());

        // Legacy metadata-only registration — nothing to preview.
        assertThatThrownBy(() -> service.loadFile("doc_1"))
            .isInstanceOf(DocumentFileMissingException.class);
    }

    private Document document(String campusId, ScanStatus scanStatus) {
        return Document.builder()
            .id("doc_1")
            .campusId(campusId)
            .studentId("stu_demo")
            .type(DocumentType.RESUME)
            .fileName("cv.pdf")
            .contentType("application/pdf")
            .sizeBytes(1024)
            .storageKey("key/cv.pdf")
            .scanStatus(scanStatus)
            .reviewStatus(ReviewStatus.PENDING)
            .uploadedAt(OffsetDateTime.parse("2026-09-20T11:30:00Z"))
            .build();
    }

    private Student student(String id, String campusId) {
        return Student.builder().id(id).campusId(campusId).email("user-STUDENT").build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder()
            .username("user-" + role)
            .password("{noop}secret")
            .campusId(campusId)
            .role(role)
            .enabled(true)
            .build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
