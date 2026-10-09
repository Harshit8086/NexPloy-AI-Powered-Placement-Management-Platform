package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.document.exception.UnsupportedContentTypeException;
import com.nexploy.campuslink.student.dto.CertificateUpsertRequest;
import com.nexploy.campuslink.student.entity.CertificateType;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentCertificate;
import com.nexploy.campuslink.student.entity.StudentCertificateFile;
import com.nexploy.campuslink.student.exception.CertificateNotFoundException;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentCertificateFileRepository;
import com.nexploy.campuslink.student.repository.StudentCertificateRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service management of the signed-in student's showcase certificates. The student is resolved
 * by email (== username), so a student only ever reads or mutates their own certificate rows; a
 * certificate id that belongs to someone else (or does not exist) resolves to
 * {@link CertificateNotFoundException} → 404, exactly like {@code StudentSkillService}.
 *
 * <p>The uploaded file's bytes are persisted in Postgres via {@link StudentCertificateFile} (a
 * separate, shared-PK row) so a listing never loads the blob. Accepted file types and the size ceiling
 * mirror the document module ({@code application/pdf}, {@code image/png}, {@code image/jpeg}; ≤ 10 MB).
 */
@Service
@RequiredArgsConstructor
public class StudentCertificateService {

    /** Same accepted set as the document module. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "image/png",
        "image/jpeg"
    );

    /** Matches the 10 MB multipart ceiling in application.yml — a defensive second check. */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final StudentRepository studentRepository;
    private final StudentCertificateRepository certificateRepository;
    private final StudentCertificateFileRepository certificateFileRepository;
    private final CampusAccessService campusAccessService;

    @Transactional(readOnly = true)
    public List<StudentCertificate> listOwnCertificates() {
        Student student = currentStudent();
        return certificateRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
    }

    @Transactional(readOnly = true)
    public StudentCertificate getOwnCertificate(String certificateId) {
        Student student = currentStudent();
        return ownedCertificate(certificateId, student);
    }

    @Transactional
    public StudentCertificate createOwnCertificate(CertificateUpsertRequest request, MultipartFile file) {
        Student student = currentStudent();
        validateMetadata(request);
        byte[] bytes = validatedBytes(file);

        OffsetDateTime now = OffsetDateTime.now();
        String id = "cert_" + UUID.randomUUID();
        StudentCertificate certificate = StudentCertificate.builder()
            .id(id)
            .studentId(student.getId())
            .campusId(student.getCampusId())
            .createdAt(now)
            .build();
        applyMetadata(certificate, request);
        certificate.setFileName(originalName(file));
        certificate.setContentType(file.getContentType());
        certificate.setFileSizeBytes((long) bytes.length);
        certificate.setUpdatedAt(now);
        StudentCertificate saved = certificateRepository.save(certificate);

        certificateFileRepository.save(StudentCertificateFile.builder()
            .id(id)
            .content(bytes)
            .contentType(file.getContentType())
            .sizeBytes((long) bytes.length)
            .build());
        return saved;
    }

    /** Updates the metadata only; the stored file is left untouched (use {@link #replaceOwnCertificateFile}). */
    @Transactional
    public StudentCertificate updateOwnCertificateMetadata(String certificateId, CertificateUpsertRequest request) {
        Student student = currentStudent();
        validateMetadata(request);
        StudentCertificate certificate = ownedCertificate(certificateId, student);
        applyMetadata(certificate, request);
        certificate.setUpdatedAt(OffsetDateTime.now());
        return certificateRepository.save(certificate);
    }

    /** Replaces the uploaded file bytes for an existing certificate the student owns. */
    @Transactional
    public StudentCertificate replaceOwnCertificateFile(String certificateId, MultipartFile file) {
        Student student = currentStudent();
        StudentCertificate certificate = ownedCertificate(certificateId, student);
        byte[] bytes = validatedBytes(file);

        certificate.setFileName(originalName(file));
        certificate.setContentType(file.getContentType());
        certificate.setFileSizeBytes((long) bytes.length);
        certificate.setUpdatedAt(OffsetDateTime.now());

        certificateFileRepository.save(StudentCertificateFile.builder()
            .id(certificate.getId())
            .content(bytes)
            .contentType(file.getContentType())
            .sizeBytes((long) bytes.length)
            .build());
        return certificateRepository.save(certificate);
    }

    @Transactional
    public void deleteOwnCertificate(String certificateId) {
        Student student = currentStudent();
        StudentCertificate certificate = ownedCertificate(certificateId, student);
        // Delete the blob first, then the metadata row (the DB FK also cascades as a backstop).
        certificateFileRepository.deleteById(certificate.getId());
        certificateRepository.delete(certificate);
    }

    /** Loads the owned certificate's file bytes for streaming. Owner-checked; 404 otherwise. */
    @Transactional(readOnly = true)
    public CertificateFileView loadOwnCertificateFile(String certificateId) {
        Student student = currentStudent();
        StudentCertificate certificate = ownedCertificate(certificateId, student);
        StudentCertificateFile file = certificateFileRepository.findById(certificate.getId())
            .orElseThrow(() -> new CertificateNotFoundException(certificateId));
        return new CertificateFileView(certificate.getFileName(), certificate.getContentType(), file.getContent());
    }

    /** The bytes + display metadata for a certificate file download/preview. */
    public record CertificateFileView(String fileName, String contentType, byte[] content) {
    }

    // ---- internals ---------------------------------------------------------

    private StudentCertificate ownedCertificate(String certificateId, Student student) {
        return certificateRepository.findById(certificateId)
            .filter(c -> c.getStudentId().equals(student.getId()))
            .orElseThrow(() -> new CertificateNotFoundException(certificateId));
    }

    private void applyMetadata(StudentCertificate certificate, CertificateUpsertRequest request) {
        certificate.setTitle(request.title().trim());
        certificate.setType(CertificateType.fromString(request.type()));
        certificate.setIssuingOrganization(request.issuingOrganization().trim());
        certificate.setDescription(blankToNull(request.description()));
        certificate.setCredentialId(blankToNull(request.credentialId()));
        certificate.setVerificationUrl(blankToNull(request.verificationUrl()));
        certificate.setIssuerName(blankToNull(request.issuerName()));
        certificate.setOrganizationWebsite(blankToNull(request.organizationWebsite()));
    }

    /** Required-field + length checks. Mirrors the Jakarta constraints on the request record so the
     *  multipart create path (which bypasses {@code @Valid}) enforces the same rules. */
    private void validateMetadata(CertificateUpsertRequest request) {
        requireText(request.title(), "title", 200);
        requireText(request.issuingOrganization(), "issuingOrganization", 200);
        if (request.type() == null || request.type().isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        limit(request.description(), "description", 2000);
        limit(request.credentialId(), "credentialId", 200);
        limit(request.verificationUrl(), "verificationUrl", 500);
        limit(request.issuerName(), "issuerName", 200);
        limit(request.organizationWebsite(), "organizationWebsite", 500);
    }

    private byte[] validatedBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A certificate file is required");
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
        return (name == null || name.isBlank()) ? "certificate" : name;
    }

    private static void requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        limit(value, field, max);
    }

    private static void limit(String value, String field, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Resolves the signed-in student's own record (username == email for STUDENT accounts). */
    private Student currentStudent() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        campusAccessService.requireAccess(user, student.getCampusId());
        return student;
    }
}
