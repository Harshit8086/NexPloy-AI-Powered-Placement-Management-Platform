package com.nexploy.campuslink.document.controller;

import com.nexploy.campuslink.document.dto.DocumentResponse;
import com.nexploy.campuslink.document.dto.RegisterDocumentRequest;
import com.nexploy.campuslink.document.dto.ReviewDocumentRequest;
import com.nexploy.campuslink.document.entity.ScanStatus;
import com.nexploy.campuslink.document.service.DocumentService;
import com.nexploy.campuslink.document.service.DocumentService.DocumentFileView;
import com.nexploy.campuslink.shared.dto.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<?> listDocuments(
        @RequestParam(required = false) String campusId,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        if (PageRequests.requested(page, size)) {
            return ResponseEntity.ok(documentService.list(campusId, PageRequests.of(page, size, sort)));
        }
        return ResponseEntity.ok(documentService.list(campusId));
    }

    @GetMapping("/{documentId}")
    public DocumentResponse getDocument(@PathVariable String documentId) {
        return documentService.get(documentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse registerDocument(@Valid @RequestBody RegisterDocumentRequest request) {
        return documentService.register(request);
    }

    /** Attaches (or replaces) the file bytes for a registered document; resets the review workflow. */
    @PostMapping(value = "/{documentId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse attachFile(
        @PathVariable String documentId,
        @RequestParam("file") MultipartFile file
    ) {
        return documentService.attachFile(documentId, file);
    }

    /** Streams the document's file inline (PDF/PNG/JPEG) for preview/download. */
    @GetMapping("/{documentId}/file")
    public ResponseEntity<byte[]> getDocumentFile(@PathVariable String documentId) {
        DocumentFileView file = documentService.loadFile(documentId);
        MediaType mediaType = file.contentType() == null
            ? MediaType.APPLICATION_OCTET_STREAM
            : MediaType.parseMediaType(file.contentType());
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(file.fileName() == null ? "document" : file.fileName())
                    .build().toString())
            .body(file.content());
    }

    @PostMapping("/{documentId}/scan")
    public DocumentResponse recordScan(
        @PathVariable String documentId,
        @RequestParam ScanStatus status
    ) {
        return documentService.recordScan(documentId, status);
    }

    @PostMapping("/{documentId}/review")
    public DocumentResponse reviewDocument(
        @PathVariable String documentId,
        @Valid @RequestBody ReviewDocumentRequest request
    ) {
        return documentService.review(documentId, request);
    }
}
