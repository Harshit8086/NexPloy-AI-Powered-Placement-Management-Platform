package com.nexploy.campuslink.document.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.document.dto.DocumentResponse;
import com.nexploy.campuslink.document.dto.RegisterDocumentRequest;
import com.nexploy.campuslink.document.dto.ReviewDocumentRequest;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.DocumentFile;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.entity.ScanStatus;
import com.nexploy.campuslink.document.event.DocumentReviewedEvent;
import com.nexploy.campuslink.document.exception.DocumentFileMissingException;
import com.nexploy.campuslink.document.exception.DocumentNotFoundException;
import com.nexploy.campuslink.document.exception.DocumentNotReviewableException;
import com.nexploy.campuslink.document.exception.UnsupportedContentTypeException;
import com.nexploy.campuslink.document.repository.DocumentFileRepository;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.shared.dto.PageResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Registration, file storage, and review of student documents. Metadata registration
 * ({@link #register}) and the actual bytes ({@link #attachFile}) are separate steps so legacy
 * metadata-only rows can receive their file later; listings expose whether a row really has
 * bytes via the {@code hasFile} flag.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "image/png",
        "image/jpeg"
    );

    /** Matches the 10 MB multipart ceiling in application.yml — a defensive second check. */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final DocumentFileRepository documentFileRepository;
    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see their own campus's documents.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        List<Document> documents;
        if (privileged) {
            documents = campusId == null || campusId.isBlank()
                ? documentRepository.findAll()
                : documentRepository.findByCampusId(campusId);
        } else {
            documents = documentRepository.findByCampusId(user.getCampusId());
        }
        Set<String> idsWithFile = fileIdsFor(documents);
        return documents.stream()
            .map(document -> DocumentResponse.from(document, idsWithFile.contains(document.getId())))
            .toList();
    }

    /** Paged variant of {@link #list(String)} — same campus-scoping, wrapped in a PageResponse envelope. */
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(String campusId, Pageable pageable) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see their own campus's documents.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        Page<Document> documents;
        if (privileged) {
            documents = campusId == null || campusId.isBlank()
                ? documentRepository.findAll(pageable)
                : documentRepository.findByCampusId(campusId, pageable);
        } else {
            documents = documentRepository.findByCampusId(user.getCampusId(), pageable);
        }
        Set<String> idsWithFile = fileIdsFor(documents.getContent());
        return PageResponse.of(documents,
            document -> DocumentResponse.from(document, idsWithFile.contains(document.getId())));
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(String documentId) {
        UserAccount user = campusAccessService.currentUser();
        Document document = loadAccessible(documentId, user);
        return DocumentResponse.from(document, documentFileRepository.existsById(document.getId()));
    }

    /**
     * Registers metadata for a document whose bytes arrive separately via {@link #attachFile}
     * (or never, for legacy metadata-only rows). Content type is restricted (SECURITY.md
     * section 3); size is bounded by the request DTO. The document starts scan-pending and
     * review-pending; it cannot be approved until a scan clears it.
     */
    @Transactional
    public DocumentResponse register(RegisterDocumentRequest request) {
        UserAccount user = campusAccessService.currentUser();
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
            throw new UnsupportedContentTypeException(request.contentType());
        }
        // Only the owning student, or an officer/admin on their behalf, may register a document.
        // A STUDENT is pinned to their own id regardless of the body's studentId (no cross-student
        // registration); recruiters/mentors have no business registering student documents.
        String studentId = resolveRegistrableStudent(user, request.studentId());

        Document document = documentRepository.save(Document.builder()
            .id(UUID.randomUUID().toString())
            .campusId(user.getCampusId())
            .studentId(studentId)
            .type(request.type())
            .fileName(request.fileName())
            .contentType(request.contentType())
            .sizeBytes(request.sizeBytes())
            .storageKey(request.storageKey())
            .scanStatus(ScanStatus.PENDING)
            .reviewStatus(ReviewStatus.PENDING)
            .uploadedAt(OffsetDateTime.now())
            .build());
        // Registration carries metadata only — bytes arrive via attachFile.
        return DocumentResponse.from(document, false);
    }

    /**
     * Attaches (or replaces) the uploaded file for a registered document. Fresh bytes invalidate
     * any prior scan/approval, so the workflow resets to scan-pending + review-pending and the
     * document must be re-reviewed — an approval can never survive a file swap. Only the owning
     * student, or an officer/admin on an accessible campus, may attach.
     */
    @Transactional
    public DocumentResponse attachFile(String documentId, MultipartFile file) {
        UserAccount user = campusAccessService.currentUser();
        byte[] bytes = validatedBytes(file);
        Document document = loadAccessible(documentId, user);
        requireFileAccess(user, document);

        documentFileRepository.save(DocumentFile.builder()
            .id(document.getId())
            .content(bytes)
            .contentType(file.getContentType())
            .sizeBytes((long) bytes.length)
            .build());

        // Replace whatever metadata the (possibly legacy) registration carried with the real upload's.
        document.setFileName(originalName(file));
        document.setContentType(file.getContentType());
        document.setSizeBytes(bytes.length);
        document.setUploadedAt(OffsetDateTime.now());
        document.setScanStatus(ScanStatus.PENDING);
        document.setReviewStatus(ReviewStatus.PENDING);
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        documentRepository.save(document);
        return DocumentResponse.from(document, true);
    }

    /**
     * Loads a document's file bytes for streaming. Access rules mirror {@link #attachFile};
     * a row with no bytes (legacy registration) raises {@link DocumentFileMissingException} → 404.
     */
    @Transactional(readOnly = true)
    public DocumentFileView loadFile(String documentId) {
        UserAccount user = campusAccessService.currentUser();
        Document document = loadAccessible(documentId, user);
        requireFileAccess(user, document);
        DocumentFile file = documentFileRepository.findById(documentId)
            .orElseThrow(() -> new DocumentFileMissingException(documentId));
        return new DocumentFileView(document.getFileName(), document.getContentType(), file.getContent());
    }

    /** The bytes + display metadata for a document file preview/download. */
    public record DocumentFileView(String fileName, String contentType, byte[] content) {
    }

    /**
     * Records the outcome of a malware scan. Placeholder for an external scanner callback;
     * an infected document is retained (for audit) but can never be approved. Restricted to
     * officers/admins — a student must not be able to self-clear their own document's scan and
     * thereby unlock approval.
     */
    @Transactional
    public DocumentResponse recordScan(String documentId, ScanStatus scanStatus) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Only officers and admins can record document scan results");
        }
        Document document = loadAccessible(documentId, user);
        document.setScanStatus(scanStatus);
        documentRepository.save(document);
        return DocumentResponse.from(document, documentFileRepository.existsById(document.getId()));
    }

    /**
     * Approves or rejects a document. Only officers and admins may review. A document
     * that is not scan-clean cannot be approved.
     */
    @Transactional
    public DocumentResponse review(String documentId, ReviewDocumentRequest request) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Only officers and admins can review documents");
        }
        Document document = loadAccessible(documentId, user);
        if (request.decision() == ReviewStatus.APPROVED && document.getScanStatus() != ScanStatus.CLEAN) {
            throw new DocumentNotReviewableException(
                "Document " + documentId + " cannot be approved until its scan is CLEAN (current: "
                    + document.getScanStatus() + ")");
        }

        document.setReviewStatus(request.decision());
        document.setReviewedBy(user.getUsername());
        document.setReviewedAt(OffsetDateTime.now());
        documentRepository.save(document);
        // Tell the owning student their document was approved / rejected (in-app via the consumer).
        eventPublisher.publishEvent(DocumentReviewedEvent.of(
            document.getCampusId(), document.getId(), document.getStudentId(),
            request.decision() == ReviewStatus.APPROVED, document.getReviewedAt()));
        return DocumentResponse.from(document, documentFileRepository.existsById(document.getId()));
    }

    // ---- internals ---------------------------------------------------------

    private Document loadAccessible(String documentId, UserAccount user) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
        campusAccessService.requireAccess(user, document.getCampusId());
        return document;
    }

    /**
     * File-level access on top of {@link #loadAccessible}'s campus check: officers/admins already
     * passed it; a STUDENT must additionally own the document (same-campus is not enough); any
     * other role is denied.
     */
    private void requireFileAccess(UserAccount user, Document document) {
        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.STUDENT) {
            Student student = studentRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
            if (!document.getStudentId().equals(student.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                    "Students can only access their own document files");
            }
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException(
            "Only the owning student or an officer/admin can access a document file");
    }

    /** Ids of the given documents that have a file row — one id-only query, no blob load, no N+1. */
    private Set<String> fileIdsFor(List<Document> documents) {
        if (documents.isEmpty()) {
            return Set.of();
        }
        List<String> ids = documents.stream().map(Document::getId).toList();
        return Set.copyOf(documentFileRepository.findIdsByIdIn(ids));
    }

    /**
     * Resolves whose document is being registered. Officers/admins may register on behalf of any
     * student on an accessible campus (the body's studentId is trusted, but campus access is
     * enforced). A STUDENT is always pinned to their own record — the body's studentId is ignored,
     * so a student cannot register a document against another student. Any other role is denied.
     */
    private String resolveRegistrableStudent(UserAccount user, String requestedStudentId) {
        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            Student student = studentRepository.findById(requestedStudentId)
                .orElseThrow(() -> new StudentNotFoundException(requestedStudentId));
            campusAccessService.requireAccess(user, student.getCampusId());
            return student.getId();
        }
        if (user.getRole() == UserRole.STUDENT) {
            Student student = studentRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
            return student.getId();
        }
        throw new org.springframework.security.access.AccessDeniedException(
            "Only the owning student or an officer/admin can register a document");
    }

    /** Same allowlist + ceiling as the certificate module (SECURITY.md section 3). */
    private byte[] validatedBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A document file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedContentTypeException(
                "Unsupported file type: " + contentType + ". Allowed: PDF, PNG, JPEG");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File exceeds the 10 MB limit");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }
    }

    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "document" : name;
    }
}
