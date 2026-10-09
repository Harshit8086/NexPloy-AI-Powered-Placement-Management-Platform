package com.nexploy.campuslink.application.dto;

import jakarta.validation.constraints.Size;

/**
 * Body for POST /api/drives/{driveId}/apply. Both fields are optional: a student may apply
 * with just a click. The drive id comes from the path, and the student is the signed-in user,
 * so neither is accepted here.
 */
public record ApplyToDriveRequest(
    @Size(max = 2000, message = "cover note must be at most 2000 characters") String coverNote,
    @Size(max = 64, message = "resume document id must be at most 64 characters") String resumeDocumentId
) {
}
