package com.nexploy.campuslink.application.service;

import com.nexploy.campuslink.application.dto.ApplicationResponse;
import com.nexploy.campuslink.application.dto.ApplyToDriveRequest;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.entity.ApplicationStatusChange;
import com.nexploy.campuslink.application.event.ApplicationAdvancedEvent;
import com.nexploy.campuslink.application.exception.AlreadyAppliedException;
import com.nexploy.campuslink.application.exception.DriveClosedException;
import com.nexploy.campuslink.application.exception.InvalidApplicationTransitionException;
import com.nexploy.campuslink.application.exception.NotEligibleException;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.application.repository.ApplicationStatusChangeRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.exception.DocumentNotFoundException;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.matching.event.ShortlistDecidedEvent;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.service.PlacementStatusService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service application to a drive. The student is resolved by email (== username), so a
 * student can only ever apply as themselves. An application is only created when every gate
 * passes: the drive is open (ACTIVE, before its deadline), the student is eligible per the
 * deterministic {@link EligibilityService}, and they have not already applied. The unique
 * DB constraint on (student_id, drive_id) is the final backstop against a concurrent duplicate.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusChangeRepository statusChangeRepository;
    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;
    private final DocumentRepository documentRepository;
    private final EligibilityService eligibilityService;
    private final CampusAccessService campusAccessService;
    private final RecruiterRepository recruiterRepository;
    private final PlacementStatusService placementStatusService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Legal officer/recruiter-driven funnel transitions. Student withdrawal (-> WITHDRAWN) is
     * handled separately in {@link #withdraw}. Post-offer stages (ACCEPTED -> DOCS -> JOINED) are
     * driven by the offer lifecycle in {@code OfferService} via {@link #advance}: the student
     * accepts (-> ACCEPTED), then an officer verifies documents (-> DOCS) and confirms joining
     * (-> JOINED). JOINED / REJECTED / WITHDRAWN are terminal.
     */
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
        ApplicationStatus.APPLIED, Set.of(ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED),
        ApplicationStatus.SHORTLISTED,
            Set.of(ApplicationStatus.ASSESSMENT, ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
        ApplicationStatus.ASSESSMENT, Set.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
        ApplicationStatus.INTERVIEW, Set.of(ApplicationStatus.SELECTED, ApplicationStatus.REJECTED),
        ApplicationStatus.SELECTED, Set.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED),
        ApplicationStatus.OFFER, Set.of(ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED),
        ApplicationStatus.ACCEPTED, Set.of(ApplicationStatus.DOCS, ApplicationStatus.REJECTED),
        ApplicationStatus.DOCS, Set.of(ApplicationStatus.JOINED, ApplicationStatus.REJECTED)
    );

    @Transactional
    public ApplicationResponse applyToDrive(String driveId, ApplyToDriveRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        campusAccessService.requireAccess(user, student.getCampusId());

        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        campusAccessService.requireAccess(user, drive.getCampusId());

        if (drive.getStatus() != DriveStatus.ACTIVE) {
            throw new DriveClosedException("Drive " + driveId + " is not accepting applications (status "
                + drive.getStatus() + ")");
        }
        if (drive.getDeadline() != null && LocalDate.now().isAfter(drive.getDeadline())) {
            throw new DriveClosedException("The application deadline for drive " + driveId
                + " has passed (" + drive.getDeadline() + ")");
        }

        // One application per (student, drive). Checked here for a clear 409; the unique
        // constraint still guards against a race between two concurrent requests.
        applicationRepository.findByStudentIdAndDriveId(student.getId(), driveId)
            .ifPresent(existing -> {
                throw new AlreadyAppliedException(driveId);
            });

        EligibilityResponse eligibility = eligibilityService.evaluate(driveId, student.getId());
        if (!eligibility.eligible()) {
            throw new NotEligibleException(eligibility.failureReasons());
        }

        String resumeDocumentId = resolveResumeDocumentId(request, student);

        Application application = applicationRepository.save(Application.builder()
            .id("app_" + UUID.randomUUID())
            .studentId(student.getId())
            .driveId(driveId)
            .appliedOn(LocalDate.now())
            .status(ApplicationStatus.APPLIED)
            .coverNote(blankToNull(request.coverNote()))
            .resumeDocumentId(resumeDocumentId)
            .build());
        // The student now has a live application — refresh their derived placement status.
        placementStatusService.recompute(student.getId());
        return ApplicationResponse.from(application);
    }

    /**
     * Validates an optionally-attached resume: it must exist and belong to the applying student.
     * Returns null when no document was attached.
     */
    private String resolveResumeDocumentId(ApplyToDriveRequest request, Student student) {
        String documentId = blankToNull(request.resumeDocumentId());
        if (documentId == null) {
            return null;
        }
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!student.getId().equals(document.getStudentId())) {
            throw new IllegalArgumentException("Resume document " + documentId + " does not belong to you");
        }
        return documentId;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // ---- Placement funnel: status transitions + audit history -------------------

    /**
     * Advances the (student, drive) application to {@code target}, writing an immutable
     * {@link ApplicationStatusChange} row. No-op if there is no such application or it is
     * already at the target status, so event-driven callers are idempotent. Returns the
     * resulting application, or empty when none exists for the pair.
     */
    @Transactional
    public Optional<Application> advance(String studentId, String driveId,
                                         ApplicationStatus target, String changedBy) {
        Optional<Application> found = applicationRepository.findByStudentIdAndDriveId(studentId, driveId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Application application = found.get();
        if (application.getStatus() == target) {
            return Optional.of(application);
        }
        ApplicationStatus previous = application.getStatus();
        application.setStatus(target);
        Application saved = applicationRepository.save(application);
        audit(saved.getId(), previous, target, changedBy);
        placementStatusService.recompute(studentId);
        return Optional.of(saved);
    }

    /** Records one immutable transition row (mirrors {@code OfferService.audit}). */
    private void audit(String applicationId, ApplicationStatus previous, ApplicationStatus next,
                       String changedBy) {
        statusChangeRepository.save(ApplicationStatusChange.builder()
            .id("apphist_" + UUID.randomUUID())
            .applicationId(applicationId)
            .previousStatus(previous)
            .newStatus(next)
            .changedBy(changedBy == null ? "system" : changedBy)
            .changedAt(OffsetDateTime.now())
            .build());
    }

    // ---- Manual status control (officer/recruiter) + student withdrawal ---------

    /**
     * Officer/recruiter moves an application through the funnel. Officers/admins may act on any
     * application on an accessible campus; a recruiter only on applications to their own drives.
     * The transition must be legal (else 409); each change is audited.
     */
    @Transactional
    public ApplicationResponse changeStatus(String applicationId, ApplicationStatus target) {
        UserAccount user = campusAccessService.currentUser();
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        Drive drive = driveRepository.findById(application.getDriveId())
            .orElseThrow(() -> new DriveNotFoundException(application.getDriveId()));
        requireApplicationManager(user, drive);

        ApplicationStatus current = application.getStatus();
        if (current == target) {
            return ApplicationResponse.from(application);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new InvalidApplicationTransitionException(
                "Application " + applicationId + " cannot move from " + current + " to " + target);
        }
        application.setStatus(target);
        Application saved = applicationRepository.save(application);
        audit(saved.getId(), current, target, user.getUsername());
        placementStatusService.recompute(saved.getStudentId());
        // A funnel shortlist/reject is the same decision the Career-Matches flow records via
        // ShortlistService — publish the same event so the student is notified either way
        // (in-app + email via the notification listener).
        if (target == ApplicationStatus.SHORTLISTED || target == ApplicationStatus.REJECTED) {
            eventPublisher.publishEvent(ShortlistDecidedEvent.of(
                drive.getCampusId(), saved.getDriveId(), saved.getStudentId(),
                target == ApplicationStatus.REJECTED, OffsetDateTime.now()));
        } else {
            // Every other funnel advance (ASSESSMENT, INTERVIEW, SELECTED, OFFER, ...) tells the
            // student their application moved forward — the same after-commit listener creates
            // the in-app + email notification.
            eventPublisher.publishEvent(ApplicationAdvancedEvent.of(
                drive.getCampusId(), saved.getDriveId(), saved.getStudentId(),
                target.name(), OffsetDateTime.now()));
        }
        return ApplicationResponse.from(saved);
    }

    /**
     * The signed-in student withdraws their own application. Allowed from any non-terminal
     * status; terminal states (JOINED / REJECTED / WITHDRAWN) cannot be withdrawn.
     */
    @Transactional
    public ApplicationResponse withdraw(String applicationId) {
        UserAccount user = campusAccessService.currentUser();
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        if (!student.getId().equals(application.getStudentId())) {
            throw new AccessDeniedException("You can only withdraw your own applications");
        }

        ApplicationStatus current = application.getStatus();
        if (current == ApplicationStatus.JOINED || current == ApplicationStatus.REJECTED
            || current == ApplicationStatus.WITHDRAWN) {
            throw new InvalidApplicationTransitionException(
                "Application " + applicationId + " is " + current + " and cannot be withdrawn");
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        Application saved = applicationRepository.save(application);
        audit(saved.getId(), current, ApplicationStatus.WITHDRAWN, user.getUsername());
        placementStatusService.recompute(saved.getStudentId());
        return ApplicationResponse.from(saved);
    }

    /**
     * Authorizes a status change against the application's drive. Officers/admins need campus
     * access; a recruiter must own the drive (username == email). Public so other read/decision
     * flows over an application's drive (e.g. the recommendation endpoints) reuse the exact same
     * recruiter-owns-drive / officer / admin rule rather than duplicating it.
     */
    public void requireApplicationManager(UserAccount user, Drive drive) {
        campusAccessService.requireAccess(user, drive.getCampusId());
        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.RECRUITER) {
            Recruiter own = recruiterRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new AccessDeniedException("No recruiter profile for " + user.getUsername()));
            if (!own.getId().equals(drive.getRecruiterId())) {
                throw new AccessDeniedException("Recruiters can only manage applications to their own drives");
            }
            return;
        }
        throw new AccessDeniedException("Only officers, admins and recruiters can change application status");
    }
}
