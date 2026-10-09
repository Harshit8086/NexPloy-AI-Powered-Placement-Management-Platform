package com.nexploy.campuslink.drive.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.ChangeDriveStatusRequest;
import com.nexploy.campuslink.drive.dto.CreateDriveRequest;
import com.nexploy.campuslink.drive.dto.DriveResponse;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.dto.UpdateDriveRequest;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.DriveService;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.shared.dto.PageRequests;
import com.nexploy.campuslink.shared.dto.PageResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drives")
@RequiredArgsConstructor
public class DriveController {

    private final DriveRepository driveRepository;
    private final DriveService driveService;
    private final EligibilityService eligibilityService;
    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public ResponseEntity<?> listDrives(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see every campus's drives; officers are scoped to their own campus.
        boolean staffWide = user.getRole() == UserRole.ADMIN;

        // Opt-in pagination: legacy array shape preserved when no page/size is supplied.
        if (PageRequests.requested(page, size)) {
            Pageable pageable = PageRequests.of(page, size, sort);
            Page<Drive> result = staffWide
                ? driveRepository.findAll(pageable)
                : driveRepository.findByCampusId(user.getCampusId(), pageable);
            return ResponseEntity.ok(PageResponse.of(result, driveService::toResponse));
        }

        List<DriveResponse> all = (staffWide
            ? driveRepository.findAll()
            : driveRepository.findByCampusId(user.getCampusId()))
            .stream().map(driveService::toResponse).toList();
        return ResponseEntity.ok(all);
    }

    @GetMapping("/{driveId}")
    public DriveResponse getDrive(@PathVariable String driveId) {
        UserAccount user = campusAccessService.currentUser();
        var drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));

        campusAccessService.requireAccess(user, drive.getCampusId());
        return driveService.toResponse(drive);
    }

    @GetMapping("/{driveId}/eligibility/{studentId}")
    public EligibilityResponse evaluateEligibility(
        @PathVariable String driveId,
        @PathVariable String studentId
    ) {
        UserAccount user = campusAccessService.currentUser();
        var drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        campusAccessService.requireAccess(user, drive.getCampusId());
        campusAccessService.requireAccess(user, student.getCampusId());
        return eligibilityService.evaluate(driveId, studentId);
    }

    /** POST /api/drives — officer/recruiter creates a drive (starts in DRAFT). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriveResponse createDrive(@Valid @RequestBody CreateDriveRequest request) {
        return driveService.create(request);
    }

    /** PUT /api/drives/{id} — officer/recruiter replaces a drive's editable fields. */
    @PutMapping("/{driveId}")
    public DriveResponse updateDrive(
        @PathVariable String driveId,
        @Valid @RequestBody UpdateDriveRequest request
    ) {
        return driveService.update(driveId, request);
    }

    /** PATCH /api/drives/{id}/status — officer/recruiter moves the drive's lifecycle state. */
    @PatchMapping("/{driveId}/status")
    public DriveResponse changeDriveStatus(
        @PathVariable String driveId,
        @Valid @RequestBody ChangeDriveStatusRequest request
    ) {
        return driveService.changeStatus(driveId, request.status());
    }
}

