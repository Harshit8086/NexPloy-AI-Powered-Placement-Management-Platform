package com.nexploy.campuslink.document.dto;

import com.nexploy.campuslink.document.entity.DocumentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Registers metadata for a document already uploaded to external storage. The caller
 * provides the storage key; the service validates content type and size before persisting.
 */
public record RegisterDocumentRequest(
    @NotBlank String studentId,
    @NotNull DocumentType type,
    @NotBlank String fileName,
    @NotBlank String contentType,
    @Positive @Max(value = 10_485_760, message = "File exceeds the 10 MB limit") long sizeBytes,
    @NotBlank String storageKey
) {
}
