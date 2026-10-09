package com.nexploy.campuslink.student.controller;

import com.nexploy.campuslink.student.dto.CertificateUpsertRequest;
import com.nexploy.campuslink.student.dto.StudentCertificateResponse;
import com.nexploy.campuslink.student.service.StudentCertificateService;
import com.nexploy.campuslink.student.service.StudentCertificateService.CertificateFileView;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Self-service certificate management for the signed-in student. Every operation resolves the student
 * by email (== username), so a student can only read or change their own certificates; the file bytes
 * are streamed back only to their owner. Create/replace-file are multipart; metadata update is JSON.
 */
@RestController
@RequestMapping("/api/students/me/certificates")
@RequiredArgsConstructor
public class StudentCertificateController {

    private final StudentCertificateService certificateService;

    @GetMapping
    public List<StudentCertificateResponse> listCertificates() {
        return certificateService.listOwnCertificates().stream()
            .map(StudentCertificateResponse::from).toList();
    }

    @GetMapping("/{certificateId}")
    public StudentCertificateResponse getCertificate(@PathVariable String certificateId) {
        return StudentCertificateResponse.from(certificateService.getOwnCertificate(certificateId));
    }

    /** Creates a certificate from multipart form fields + the required file part. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StudentCertificateResponse createCertificate(
        @RequestParam("title") String title,
        @RequestParam("type") String type,
        @RequestParam("issuingOrganization") String issuingOrganization,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "credentialId", required = false) String credentialId,
        @RequestParam(value = "verificationUrl", required = false) String verificationUrl,
        @RequestParam(value = "issuerName", required = false) String issuerName,
        @RequestParam(value = "organizationWebsite", required = false) String organizationWebsite,
        @RequestParam("file") MultipartFile file
    ) {
        CertificateUpsertRequest request = new CertificateUpsertRequest(
            title, type, issuingOrganization, description, credentialId,
            verificationUrl, issuerName, organizationWebsite);
        return StudentCertificateResponse.from(certificateService.createOwnCertificate(request, file));
    }

    /** Updates a certificate's metadata (the stored file is unchanged). */
    @PutMapping("/{certificateId}")
    public StudentCertificateResponse updateCertificate(
        @PathVariable String certificateId,
        @Valid @RequestBody CertificateUpsertRequest request
    ) {
        return StudentCertificateResponse.from(
            certificateService.updateOwnCertificateMetadata(certificateId, request));
    }

    /** Replaces the uploaded file for an existing certificate. */
    @PostMapping(value = "/{certificateId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StudentCertificateResponse replaceCertificateFile(
        @PathVariable String certificateId,
        @RequestParam("file") MultipartFile file
    ) {
        return StudentCertificateResponse.from(
            certificateService.replaceOwnCertificateFile(certificateId, file));
    }

    @DeleteMapping("/{certificateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCertificate(@PathVariable String certificateId) {
        certificateService.deleteOwnCertificate(certificateId);
    }

    /** Streams the certificate's file inline (PDF/PNG/JPEG) to its owner for preview/download. */
    @GetMapping("/{certificateId}/file")
    public ResponseEntity<byte[]> getCertificateFile(@PathVariable String certificateId) {
        CertificateFileView file = certificateService.loadOwnCertificateFile(certificateId);
        MediaType mediaType = file.contentType() == null
            ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(file.contentType());
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(file.fileName() == null ? "certificate" : file.fileName())
                    .build().toString())
            .body(file.content());
    }
}
