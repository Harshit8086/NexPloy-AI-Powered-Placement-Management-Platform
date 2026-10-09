package com.nexploy.campuslink.offer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.offer.dto.CreateOfferRequest;
import com.nexploy.campuslink.offer.dto.OfferResponse;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.entity.OfferStatusChange;
import com.nexploy.campuslink.offer.exception.DocumentsNotVerifiedException;
import com.nexploy.campuslink.offer.exception.InvalidOfferTransitionException;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.offer.repository.OfferStatusChangeRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OfferServiceTest {

    private final OfferRepository offerRepository = org.mockito.Mockito.mock(OfferRepository.class);
    private final OfferStatusChangeRepository statusChangeRepository =
        org.mockito.Mockito.mock(OfferStatusChangeRepository.class);
    private final com.nexploy.campuslink.student.repository.StudentRepository studentRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.student.repository.StudentRepository.class);
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
        org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
    private final com.nexploy.campuslink.application.service.ApplicationService applicationService =
        org.mockito.Mockito.mock(com.nexploy.campuslink.application.service.ApplicationService.class);
    private final com.nexploy.campuslink.drive.repository.DriveRepository driveRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.drive.repository.DriveRepository.class);
    private final com.nexploy.campuslink.document.repository.DocumentRepository documentRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.document.repository.DocumentRepository.class);
    private final com.nexploy.campuslink.student.service.PlacementStatusService placementStatusService =
        org.mockito.Mockito.mock(com.nexploy.campuslink.student.service.PlacementStatusService.class);
    private final com.nexploy.campuslink.notification.service.NotificationService notificationService =
        org.mockito.Mockito.mock(com.nexploy.campuslink.notification.service.NotificationService.class);
    private final com.nexploy.campuslink.auth.repository.UserAccountRepository userAccountRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.auth.repository.UserAccountRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final OfferService service = new OfferService(
        offerRepository, statusChangeRepository, studentRepository, campusAccessService,
        eventPublisher, applicationService, driveRepository, documentRepository, placementStatusService,
        notificationService, userAccountRepository);
 
    @Test
    void extendCreatesOfferAndWritesAuditRecord() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));
        when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = service.extend(new CreateOfferRequest(
            "stu_demo", "drv_technova", "rec_technova", "Backend Developer", new BigDecimal("12.00"),
            null, null, null, null, null, null));

        assertThat(response.status()).isEqualTo(OfferStatus.EXTENDED);
        assertThat(response.campusId()).isEqualTo("campus_demo");
        ArgumentCaptor<OfferStatusChange> captor = ArgumentCaptor.forClass(OfferStatusChange.class);
        org.mockito.Mockito.verify(statusChangeRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousStatus()).isNull();
        assertThat(captor.getValue().getNewStatus()).isEqualTo(OfferStatus.EXTENDED);
        assertThat(captor.getValue().getChangedBy()).isEqualTo("user-OFFICER");
    }

    @Test
    void recruiterExtensionDelegatesToApplicationManagerRule() {
        // OfferService no longer rejects recruiters itself: ownership is decided by
        // ApplicationService.requireApplicationManager (a no-op on the mock -> allowed).
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));
        when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = service.extend(new CreateOfferRequest(
            "stu_demo", "drv_technova", "rec_technova", "Backend Developer", new BigDecimal("10"),
            null, null, null, null, null, null));

        assertThat(response.status()).isEqualTo(OfferStatus.EXTENDED);
    }

    @Test
    void recruiterDeniedWhenApplicationManagerRuleRejects() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));
        org.mockito.Mockito.doThrow(new AccessDeniedException(
                "Recruiters can only manage applications to their own drives"))
            .when(applicationService).requireApplicationManager(any(), any());

        assertThatThrownBy(() -> service.extend(new CreateOfferRequest(
            "stu_demo", "drv_technova", "rec_technova", "Backend Developer", new BigDecimal("10"),
            null, null, null, null, null, null)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void extendWithUnknownDriveThrows() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(driveRepository.findById("drv_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extend(new CreateOfferRequest(
            "stu_demo", "drv_missing", "rec_technova", "Backend Developer", new BigDecimal("10"),
            null, null, null, null, null, null)))
            .isInstanceOf(com.nexploy.campuslink.drive.exception.DriveNotFoundException.class);
    }

    @Test
    void acceptTransitionsExtendedOfferAndRecordsDecider() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = service.accept("off_1");

        assertThat(response.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(response.decidedBy()).isEqualTo("user-OFFICER");
        assertThat(offer.getDecidedAt()).isNotNull();
        // Student acceptance advances the placement funnel to ACCEPTED (not straight to JOINED).
        org.mockito.Mockito.verify(applicationService)
            .advance("stu_demo", "drv_technova", ApplicationStatus.ACCEPTED, "user-OFFICER");
    }

    @Test
    void acceptingAlreadyDecidedOfferIsRejected() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        offer.setStatus(OfferStatus.ACCEPTED);
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.accept("off_1"))
            .isInstanceOf(InvalidOfferTransitionException.class);
    }

    @Test
    void recruiterCannotAccessOfferOutsideCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(extendedOffer("campus_other")));

        assertThatThrownBy(() -> service.get("off_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void studentCanAcceptTheirOwnOffer() {
        authenticate(user(UserRole.STUDENT, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentRepository.findByEmail("user-STUDENT"))
            .thenReturn(Optional.of(com.nexploy.campuslink.student.entity.Student.builder()
                .id("stu_demo").campusId("campus_demo").email("user-STUDENT").build()));

        OfferResponse response = service.accept("off_1");

        assertThat(response.status()).isEqualTo(OfferStatus.ACCEPTED);
    }

    @Test
    void studentCannotAcceptAnotherStudentsOffer() {
        authenticate(user(UserRole.STUDENT, "campus_demo"));
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(extendedOffer("campus_demo")));
        when(studentRepository.findByEmail("user-STUDENT"))
            .thenReturn(Optional.of(com.nexploy.campuslink.student.entity.Student.builder()
                .id("stu_other").campusId("campus_demo").email("user-STUDENT").build()));

        assertThatThrownBy(() -> service.accept("off_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void verifyDocumentsAdvancesToDocsWhenAllApproved() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        offer.setStatus(OfferStatus.ACCEPTED);
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.findByStudentId("stu_demo"))
            .thenReturn(List.of(document(ReviewStatus.APPROVED), document(ReviewStatus.APPROVED)));

        OfferResponse response = service.verifyDocuments("off_1");

        assertThat(response.documentsVerified()).isTrue();
        org.mockito.Mockito.verify(applicationService)
            .advance("stu_demo", "drv_technova", ApplicationStatus.DOCS, "user-OFFICER");
    }

    @Test
    void verifyDocumentsRejectedWhenADocumentIsNotApproved() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        offer.setStatus(OfferStatus.ACCEPTED);
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));
        when(documentRepository.findByStudentId("stu_demo"))
            .thenReturn(List.of(document(ReviewStatus.APPROVED), document(ReviewStatus.PENDING)));

        assertThatThrownBy(() -> service.verifyDocuments("off_1"))
            .isInstanceOf(DocumentsNotVerifiedException.class);
    }

    @Test
    void verifyDocumentsRejectedWhenStudentHasNoDocuments() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        offer.setStatus(OfferStatus.ACCEPTED);
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));
        when(documentRepository.findByStudentId("stu_demo")).thenReturn(List.of());

        assertThatThrownBy(() -> service.verifyDocuments("off_1"))
            .isInstanceOf(DocumentsNotVerifiedException.class);
    }

    @Test
    void verifyDocumentsRequiresAcceptedOffer() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(extendedOffer("campus_demo")));

        assertThatThrownBy(() -> service.verifyDocuments("off_1"))
            .isInstanceOf(InvalidOfferTransitionException.class);
    }

    @Test
    void recruiterCannotVerifyDocuments() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));

        assertThatThrownBy(() -> service.verifyDocuments("off_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void markJoinedAdvancesToJoinedWhenDocsVerified() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setDocumentsVerified(true);
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));
        when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = service.markJoined("off_1");

        assertThat(response.joined()).isTrue();
        org.mockito.Mockito.verify(applicationService)
            .advance("stu_demo", "drv_technova", ApplicationStatus.JOINED, "user-OFFICER");
    }

    @Test
    void markJoinedRejectedWhenDocsNotVerified() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        offer.setStatus(OfferStatus.ACCEPTED);
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.markJoined("off_1"))
            .isInstanceOf(InvalidOfferTransitionException.class);
    }

    @Test
    void recruiterCannotAcceptOffer() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(extendedOffer("campus_demo")));

        assertThatThrownBy(() -> service.accept("off_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void officerCanRevokeExtendedOffer() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        Offer offer = extendedOffer("campus_demo");
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(offer));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));
        when(offerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = service.revoke("off_1");

        assertThat(response.status()).isEqualTo(OfferStatus.REVOKED);
    }

    @Test
    void revokeDeniedWhenApplicationManagerRuleRejects() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));
        when(offerRepository.findById("off_1")).thenReturn(Optional.of(extendedOffer("campus_demo")));
        when(driveRepository.findById("drv_technova")).thenReturn(Optional.of(technovaDrive()));
        org.mockito.Mockito.doThrow(new AccessDeniedException(
                "Recruiters can only manage applications to their own drives"))
            .when(applicationService).requireApplicationManager(any(), any());

        assertThatThrownBy(() -> service.revoke("off_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    private com.nexploy.campuslink.drive.entity.Drive technovaDrive() {
        return com.nexploy.campuslink.drive.entity.Drive.builder()
            .id("drv_technova")
            .campusId("campus_demo")
            .recruiterId("rec_technova")
            .build();
    }

    private Document document(ReviewStatus reviewStatus) {
        return Document.builder()
            .id("doc_" + reviewStatus)
            .campusId("campus_demo")
            .studentId("stu_demo")
            .reviewStatus(reviewStatus)
            .build();
    }

    private Offer extendedOffer(String campusId) {
        return Offer.builder()
            .id("off_1")
            .campusId(campusId)
            .studentId("stu_demo")
            .driveId("drv_technova")
            .recruiterId("rec_technova")
            .role("Backend Developer")
            .packageLpa(new BigDecimal("12.00"))
            .status(OfferStatus.EXTENDED)
            .extendedAt(OffsetDateTime.parse("2026-09-23T10:00:00Z"))
            .build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder()
            .username("user-" + role)
            .password("{noop}secret")
            .campusId(campusId)
            .role(role)
            .enabled(true)
            .build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
