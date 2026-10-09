package com.nexploy.campuslink.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlacementStatusServiceTest {

    private final ApplicationRepository applicationRepository = Mockito.mock(ApplicationRepository.class);
    private final OfferRepository offerRepository = Mockito.mock(OfferRepository.class);
    private final StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
    private final PlacementStatusService service =
        new PlacementStatusService(applicationRepository, offerRepository, studentRepository);

    @Test
    void placedWhenAnyOfferIsAccepted() {
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of(offer(OfferStatus.ACCEPTED)));
        when(applicationRepository.findByStudentId("stu_1")).thenReturn(List.of());

        assertThat(service.derive("stu_1")).isEqualTo("PLACED");
    }

    @Test
    void placedWhenApplicationInPostAcceptanceStage() {
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of());
        when(applicationRepository.findByStudentId("stu_1"))
            .thenReturn(List.of(application(ApplicationStatus.DOCS)));

        assertThat(service.derive("stu_1")).isEqualTo("PLACED");
    }

    @Test
    void offeredWhenOfferExtendedButNotAccepted() {
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of(offer(OfferStatus.EXTENDED)));
        when(applicationRepository.findByStudentId("stu_1"))
            .thenReturn(List.of(application(ApplicationStatus.OFFER)));

        assertThat(service.derive("stu_1")).isEqualTo("OFFERED");
    }

    @Test
    void inProcessWhenActiveApplicationWithoutOffer() {
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of());
        when(applicationRepository.findByStudentId("stu_1"))
            .thenReturn(List.of(application(ApplicationStatus.INTERVIEW)));

        assertThat(service.derive("stu_1")).isEqualTo("IN PROCESS");
    }

    @Test
    void unplacedWhenNoOffersAndOnlyClosedApplications() {
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of());
        when(applicationRepository.findByStudentId("stu_1"))
            .thenReturn(List.of(application(ApplicationStatus.REJECTED), application(ApplicationStatus.WITHDRAWN)));

        assertThat(service.derive("stu_1")).isEqualTo("UNPLACED");
    }

    @Test
    void unplacedWhenNothingOnRecord() {
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of());
        when(applicationRepository.findByStudentId("stu_1")).thenReturn(List.of());

        assertThat(service.derive("stu_1")).isEqualTo("UNPLACED");
    }

    @Test
    void recomputePersistsWhenDerivedStatusChanged() {
        Student student = Student.builder().id("stu_1").placementStatus("UNPLACED").build();
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student));
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of(offer(OfferStatus.ACCEPTED)));
        when(applicationRepository.findByStudentId("stu_1")).thenReturn(List.of());

        service.recompute("stu_1");

        assertThat(student.getPlacementStatus()).isEqualTo("PLACED");
        verify(studentRepository).save(student);
    }

    @Test
    void recomputeIsNoOpWhenStatusUnchanged() {
        Student student = Student.builder().id("stu_1").placementStatus("UNPLACED").build();
        when(studentRepository.findById("stu_1")).thenReturn(Optional.of(student));
        when(offerRepository.findByStudentId("stu_1")).thenReturn(List.of());
        when(applicationRepository.findByStudentId("stu_1")).thenReturn(List.of());

        service.recompute("stu_1");

        assertThat(student.getPlacementStatus()).isEqualTo("UNPLACED");
        verify(studentRepository, never()).save(any());
    }

    private Offer offer(OfferStatus status) {
        return Offer.builder().id("off_" + status).studentId("stu_1").status(status).build();
    }

    private Application application(ApplicationStatus status) {
        return Application.builder().id("app_" + status).studentId("stu_1").status(status).build();
    }
}
