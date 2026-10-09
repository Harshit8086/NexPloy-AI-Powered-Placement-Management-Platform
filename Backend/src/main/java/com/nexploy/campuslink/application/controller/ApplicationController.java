package com.nexploy.campuslink.application.controller;

import com.nexploy.campuslink.application.dto.ApplicationResponse;
import com.nexploy.campuslink.application.dto.ApplyToDriveRequest;
import com.nexploy.campuslink.application.dto.ChangeApplicationStatusRequest;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.application.service.ApplicationService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.shared.dto.PageRequests;
import com.nexploy.campuslink.shared.dto.PageResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;
    private final CampusAccessService campusAccessService;

    @GetMapping("/applications")
    public ResponseEntity<?> listApplications(
        @RequestParam(required = false) String studentId,
        @RequestParam(required = false) String driveId,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        UserAccount user = campusAccessService.currentUser();
        boolean paged = PageRequests.requested(page, size);

        if (studentId != null) {
            Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
            campusAccessService.requireAccess(user, student.getCampusId());
            return respond(applicationRepository.findByStudentId(studentId), paged, page, size, sort);
        }

        if (driveId != null) {
            Drive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new IllegalArgumentException("Drive not found: " + driveId));
            campusAccessService.requireAccess(user, drive.getCampusId());
            return respond(applicationRepository.findByDriveId(driveId), paged, page, size, sort);
        }

        if (user.getRole() == UserRole.ADMIN) {
            // Admins see every application; page at the database level to avoid
            // materializing the whole table.
            if (paged) {
                Pageable pageable = PageRequests.of(page, size, sort);
                return ResponseEntity.ok(PageResponse.of(applicationRepository.findAll(pageable), a -> a));
            }
            return ResponseEntity.ok(applicationRepository.findAll());
        }

        List<Application> visible = applicationRepository.findAll().stream()
            .filter(application -> {
                Student student = studentRepository.findById(application.getStudentId()).orElse(null);
                Drive drive = driveRepository.findById(application.getDriveId()).orElse(null);
                return student != null && drive != null
                    && campusAccessService.canAccessCampus(user, student.getCampusId())
                    && campusAccessService.canAccessCampus(user, drive.getCampusId());
            })
            .toList();
        return respond(visible, paged, page, size, sort);
    }

    /**
     * Returns {@code applications} as either the legacy bare array or, when pagination was
     * requested, a {@link PageResponse} envelope sliced in memory (the campus-filtered and
     * id-filtered result sets cannot be paged at the database level).
     */
    private ResponseEntity<?> respond(List<Application> applications, boolean paged,
                                      Integer page, Integer size, String sort) {
        if (paged) {
            return ResponseEntity.ok(PageResponse.ofList(applications, PageRequests.of(page, size, sort)));
        }
        return ResponseEntity.ok(applications);
    }

    /** POST /api/drives/{driveId}/apply — the signed-in student applies to a drive. */
    @PostMapping("/drives/{driveId}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse applyToDrive(
        @PathVariable String driveId,
        @Valid @RequestBody(required = false) ApplyToDriveRequest request
    ) {
        return applicationService.applyToDrive(
            driveId,
            request == null ? new ApplyToDriveRequest(null, null) : request);
    }

    /** PATCH /api/applications/{id}/status — officer/recruiter advances the funnel. */
    @PatchMapping("/applications/{applicationId}/status")
    public ApplicationResponse changeStatus(
        @PathVariable String applicationId,
        @Valid @RequestBody ChangeApplicationStatusRequest request
    ) {
        return applicationService.changeStatus(applicationId, request.status());
    }

    /** PATCH /api/applications/{id}/withdraw — the signed-in student withdraws their application. */
    @PatchMapping("/applications/{applicationId}/withdraw")
    public ApplicationResponse withdraw(@PathVariable String applicationId) {
        return applicationService.withdraw(applicationId);
    }
}

