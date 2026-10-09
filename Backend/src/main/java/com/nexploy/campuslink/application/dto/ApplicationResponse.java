package com.nexploy.campuslink.application.dto;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import java.time.LocalDate;

/** Read model for an application, returned by the apply endpoint. */
public record ApplicationResponse(
    String id,
    String studentId,
    String driveId,
    LocalDate appliedOn,
    ApplicationStatus status,
    String coverNote,
    String resumeDocumentId
) {
    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
            application.getId(),
            application.getStudentId(),
            application.getDriveId(),
            application.getAppliedOn(),
            application.getStatus(),
            application.getCoverNote(),
            application.getResumeDocumentId()
        );
    }
}
