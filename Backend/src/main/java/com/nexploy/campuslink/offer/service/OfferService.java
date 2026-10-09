package com.nexploy.campuslink.offer.service;

import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.service.ApplicationService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.offer.dto.CreateOfferRequest;
import com.nexploy.campuslink.offer.dto.OfferResponse;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.entity.OfferStatusChange;
import com.nexploy.campuslink.offer.exception.DocumentsNotVerifiedException;
import com.nexploy.campuslink.offer.exception.InvalidOfferTransitionException;
import com.nexploy.campuslink.offer.exception.OfferNotFoundException;
import com.nexploy.campuslink.offer.event.OfferAcceptedEvent;
import com.nexploy.campuslink.offer.event.OfferDeclinedEvent;
import com.nexploy.campuslink.offer.event.OfferExtendedEvent;
import com.nexploy.campuslink.offer.event.OfferRevokedEvent;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.offer.repository.OfferStatusChangeRepository;
import com.nexploy.campuslink.shared.dto.PageResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.service.NotificationService;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.service.PlacementStatusService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository offerRepository;
    private final OfferStatusChangeRepository statusChangeRepository;
    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationService applicationService;
    private final DriveRepository driveRepository;
    private final DocumentRepository documentRepository;
    private final PlacementStatusService placementStatusService;
    private final NotificationService notificationService;
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public List<OfferResponse> list(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see their own campus's offers.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        List<Offer> offers;
        if (privileged) {
            offers = campusId == null || campusId.isBlank()
                ? offerRepository.findAll()
                : offerRepository.findByCampusId(campusId);
        } else {
            offers = offerRepository.findByCampusId(user.getCampusId());
        }
        return offers.stream().map(OfferResponse::from).toList();
    }

    /** Paged variant of {@link #list(String)} — same campus-scoping, wrapped in a PageResponse envelope. */
    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> list(String campusId, Pageable pageable) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see their own campus's offers.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        Page<Offer> offers;
        if (privileged) {
            offers = campusId == null || campusId.isBlank()
                ? offerRepository.findAll(pageable)
                : offerRepository.findByCampusId(campusId, pageable);
        } else {
            offers = offerRepository.findByCampusId(user.getCampusId(), pageable);
        }
        return PageResponse.of(offers, OfferResponse::from);
    }

    @Transactional(readOnly = true)
    public OfferResponse get(String offerId) {
        UserAccount user = campusAccessService.currentUser();
        Offer offer = loadAccessible(offerId, user);
        return OfferResponse.from(offer);
    }

    /** A student's offers. Resolves the student, enforces campus access, then returns their offers. */
    @Transactional(readOnly = true)
    public List<OfferResponse> listByStudent(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return offerRepository.findByStudentId(studentId).stream().map(OfferResponse::from).toList();
    }

    /**
     * Extends a new offer. Officers/admins may extend on any accessible campus; a recruiter only
     * for their own drives (the same recruiter-owns-drive rule as application status changes and
     * interview scheduling). The offer's campus is derived from the drive. The new offer starts in
     * EXTENDED and its creation is recorded in the audit trail.
     */
    @Transactional
    public OfferResponse extend(CreateOfferRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Drive drive = driveRepository.findById(request.driveId())
            .orElseThrow(() -> new DriveNotFoundException(request.driveId()));
        applicationService.requireApplicationManager(user, drive);

        String campusId = drive.getCampusId();
        OffsetDateTime now = OffsetDateTime.now();
        Offer offer = offerRepository.save(Offer.builder()
            .id(UUID.randomUUID().toString())
            .campusId(campusId)
            .studentId(request.studentId())
            .driveId(request.driveId())
            .recruiterId(request.recruiterId())
            .companyName(request.companyName())
            .role(request.role())
            .packageLpa(request.packageLpa())
            .offerType(request.offerType() == null ? "Full-Time" : request.offerType())
            .location(request.location())
            .joiningDate(request.joiningDate())
            .acceptanceDeadline(request.acceptanceDeadline())
            .fromInternship(request.fromInternship())
            .documentsVerified(false)
            .joined(false)
            .status(OfferStatus.EXTENDED)
            .extendedAt(now)
            .build());
        audit(offer.getId(), null, OfferStatus.EXTENDED, user.getUsername(), now);
        // An extended offer is the canonical placement signal for the student — publish the domain
        // event so the notification consumer turns it into an in-app notification (no manual step).
        eventPublisher.publishEvent(OfferExtendedEvent.of(
            offer.getCampusId(), offer.getId(), offer.getStudentId(), offer.getRecruiterId(),
            offer.getRole(), now));
        // Advance the placement funnel to OFFER (no-op if the applicant has no application row).
        applicationService.advance(offer.getStudentId(), offer.getDriveId(),
            ApplicationStatus.OFFER, user.getUsername());
        // An extended offer means the student is at least OFFERED even without an application row.
        placementStatusService.recompute(offer.getStudentId());
        return OfferResponse.from(offer);
    }

    /** A student accepting or declining their own offer, or an officer recording it. */
    @Transactional
    public OfferResponse accept(String offerId) {
        return transition(offerId, OfferStatus.ACCEPTED);
    }

    @Transactional
    public OfferResponse decline(String offerId) {
        return transition(offerId, OfferStatus.DECLINED);
    }

    /**
     * Revoking an offer is an officer/admin action, or the owning recruiter's for their own
     * drive's offers (the same recruiter-owns-drive rule as extend).
     */
    @Transactional
    public OfferResponse revoke(String offerId) {
        UserAccount user = campusAccessService.currentUser();
        Offer offer = offerRepository.findById(offerId)
            .orElseThrow(() -> new OfferNotFoundException(offerId));
        Drive drive = driveRepository.findById(offer.getDriveId())
            .orElseThrow(() -> new DriveNotFoundException(offer.getDriveId()));
        applicationService.requireApplicationManager(user, drive);
        return applyTransition(offer, OfferStatus.REVOKED, user);
    }

    /**
     * Recruiter or officer triggers an explicit follow-up prompt (nudge) for an offer awaiting decision.
     */
    @Transactional
    public OfferResponse nudge(String offerId) {
        UserAccount user = campusAccessService.currentUser();
        Offer offer = loadAccessible(offerId, user);

        Student student = studentRepository.findById(offer.getStudentId())
            .orElseThrow(() -> new StudentNotFoundException(offer.getStudentId()));

        String company = offer.getCompanyName() != null && !offer.getCompanyName().isBlank()
            ? offer.getCompanyName()
            : "the recruiter";
        String deadlineStr = offer.getAcceptanceDeadline() != null
            ? " by " + offer.getAcceptanceDeadline()
            : " soon";

        String subject = "Action Required: Offer Response Pending — " + company;
        String body = "Your offer for " + offer.getRole() + " with " + company
            + " is awaiting your response. Please review and confirm your decision" + deadlineStr + ".";

        String recipient = student.getEmail();
        if (notificationService != null && userAccountRepository != null
                && recipient != null && !recipient.isBlank() && userAccountRepository.existsById(recipient)) {
            notificationService.createFromEvent(
                "nudge_" + UUID.randomUUID(),
                offer.getCampusId(),
                recipient,
                NotificationType.OFFER_DEADLINE,
                offer.getId(),
                subject,
                body
            );
        }

        return OfferResponse.from(offer);
    }

    /**
     * Recruiter or officer escalates a pending offer approaching deadline to Campus Placement Officers.
     */
    @Transactional
    public OfferResponse escalate(String offerId) {
        UserAccount user = campusAccessService.currentUser();
        Offer offer = loadAccessible(offerId, user);

        Student student = studentRepository.findById(offer.getStudentId())
            .orElseThrow(() -> new StudentNotFoundException(offer.getStudentId()));

        String company = offer.getCompanyName() != null && !offer.getCompanyName().isBlank()
            ? offer.getCompanyName()
            : "Corporate Partner";

        String subject = "Escalation: Pending Offer Response — " + student.getName();
        String body = "Recruiter for " + company + " has escalated that candidate "
            + student.getName() + " (" + student.getRollNumber() + ") has not responded to their "
            + offer.getRole() + " offer (Deadline: " + (offer.getAcceptanceDeadline() != null ? offer.getAcceptanceDeadline() : "approaching") + ").";

        if (notificationService != null && userAccountRepository != null) {
            List<UserAccount> officers = userAccountRepository.findByRoleAndCampusIdAndEnabledTrue(
                UserRole.OFFICER, offer.getCampusId());

            for (UserAccount officer : officers) {
                notificationService.createFromEvent(
                    "escalate_" + UUID.randomUUID(),
                    offer.getCampusId(),
                    officer.getUsername(),
                    NotificationType.OFFER_FOLLOWUP,
                    offer.getId(),
                    subject,
                    body
                );
            }
        }

        return OfferResponse.from(offer);
    }

    private OfferResponse transition(String offerId, OfferStatus target) {
        UserAccount user = campusAccessService.currentUser();
        Offer offer = loadAccessible(offerId, user);
        requireOfferActor(user, offer);
        return applyTransition(offer, target, user);
    }

    /**
     * Authorizes an accept/decline. Officers/admins may record the decision on the student's
     * behalf; a STUDENT may act only on their own offer (email == username). Any other role, or a
     * student acting on someone else's offer, is denied — campus membership alone is not enough.
     */
    private void requireOfferActor(UserAccount user, Offer offer) {
        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.STUDENT) {
            Student student = studentRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
            if (student.getId().equals(offer.getStudentId())) {
                return;
            }
        }
        throw new org.springframework.security.access.AccessDeniedException(
            "You can only accept or decline your own offer");
    }

    private OfferResponse applyTransition(Offer offer, OfferStatus target, UserAccount user) {
        if (offer.getStatus() != OfferStatus.EXTENDED) {
            throw new InvalidOfferTransitionException(
                "Offer " + offer.getId() + " is " + offer.getStatus() + " and cannot move to " + target);
        }
        OffsetDateTime now = OffsetDateTime.now();
        OfferStatus previous = offer.getStatus();
        offer.setStatus(target);
        offer.setDecidedAt(now);
        offer.setDecidedBy(user.getUsername());
        offerRepository.save(offer);
        audit(offer.getId(), previous, target, user.getUsername(), now);
        if (target == OfferStatus.ACCEPTED) {
            eventPublisher.publishEvent(OfferAcceptedEvent.of(
                offer.getCampusId(), offer.getId(), offer.getStudentId(), offer.getRecruiterId(), now));
            // Student accepted the offer: advance the funnel to ACCEPTED. Document verification
            // (-> DOCS) and joining (-> JOINED) are officer-driven follow-ups below.
            applicationService.advance(offer.getStudentId(), offer.getDriveId(),
                ApplicationStatus.ACCEPTED, user.getUsername());
        } else if (target == OfferStatus.DECLINED) {
            // The recruiter who made the offer wants to know their candidate declined.
            eventPublisher.publishEvent(OfferDeclinedEvent.of(
                offer.getCampusId(), offer.getId(), offer.getStudentId(), offer.getRecruiterId(), now));
        } else if (target == OfferStatus.REVOKED) {
            // The student whose offer was withdrawn wants to know.
            eventPublisher.publishEvent(OfferRevokedEvent.of(
                offer.getCampusId(), offer.getId(), offer.getStudentId(), now));
        }
        // Any offer decision (accepted / declined / revoked) can change the derived placement status.
        placementStatusService.recompute(offer.getStudentId());
        return OfferResponse.from(offer);
    }

    /**
     * Officer/admin verifies the student's documents and advances the funnel to DOCS. Ties into
     * the Documents module: the student must have at least one uploaded document and every one
     * of them must be APPROVED (else 409). The offer must already be ACCEPTED by the student.
     */
    @Transactional
    public OfferResponse verifyDocuments(String offerId) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user, "verify documents");
        Offer offer = loadAccessible(offerId, user);
        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new InvalidOfferTransitionException(
                "Offer " + offerId + " is " + offer.getStatus()
                    + " — documents can only be verified after the student accepts");
        }
        requireApprovedDocuments(offer.getStudentId());
        offer.setDocumentsVerified(true);
        offerRepository.save(offer);
        applicationService.advance(offer.getStudentId(), offer.getDriveId(),
            ApplicationStatus.DOCS, user.getUsername());
        return OfferResponse.from(offer);
    }

    /**
     * Officer/admin confirms the student has joined and advances the funnel to its terminal
     * JOINED stage. Requires the offer to be ACCEPTED with documents already verified.
     */
    @Transactional
    public OfferResponse markJoined(String offerId) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user, "confirm joining");
        Offer offer = loadAccessible(offerId, user);
        if (offer.getStatus() != OfferStatus.ACCEPTED || !offer.isDocumentsVerified()) {
            throw new InvalidOfferTransitionException(
                "Offer " + offerId + " cannot be marked joined before it is accepted and its"
                    + " documents are verified");
        }
        offer.setJoined(true);
        offerRepository.save(offer);
        applicationService.advance(offer.getStudentId(), offer.getDriveId(),
            ApplicationStatus.JOINED, user.getUsername());
        return OfferResponse.from(offer);
    }

    /**
     * Enforces the DOCS gate against the Documents module: the student must have uploaded at
     * least one document and all of their documents must have review status APPROVED.
     */
    private void requireApprovedDocuments(String studentId) {
        List<Document> documents = documentRepository.findByStudentId(studentId);
        if (documents.isEmpty()) {
            throw new DocumentsNotVerifiedException(
                "Student " + studentId + " has no documents to verify");
        }
        boolean allApproved = documents.stream()
            .allMatch(document -> document.getReviewStatus() == ReviewStatus.APPROVED);
        if (!allApproved) {
            throw new DocumentsNotVerifiedException(
                "Student " + studentId + " has documents that are not yet approved");
        }
    }

    private Offer loadAccessible(String offerId, UserAccount user) {
        Offer offer = offerRepository.findById(offerId)
            .orElseThrow(() -> new OfferNotFoundException(offerId));
        campusAccessService.requireAccess(user, offer.getCampusId());
        return offer;
    }

    private void requireOfficer(UserAccount user, String action) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Only officers and admins can " + action);
        }
    }

    private void audit(String offerId, OfferStatus previous, OfferStatus next, String changedBy, OffsetDateTime at) {
        statusChangeRepository.save(OfferStatusChange.builder()
            .id(UUID.randomUUID().toString())
            .offerId(offerId)
            .previousStatus(previous)
            .newStatus(next)
            .changedBy(changedBy)
            .changedAt(at)
            .build());
    }
}
