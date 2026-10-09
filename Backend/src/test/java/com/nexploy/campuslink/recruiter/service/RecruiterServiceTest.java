package com.nexploy.campuslink.recruiter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.recruiter.dto.RecruiterResponse;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecruiterServiceTest {

    private final DriveRepository driveRepository = mock(DriveRepository.class);
    private final OfferRepository offerRepository = mock(OfferRepository.class);
    private final com.nexploy.campuslink.recruiter.repository.RecruiterRepository recruiterRepository =
        mock(com.nexploy.campuslink.recruiter.repository.RecruiterRepository.class);
    private final com.nexploy.campuslink.recruiter.repository.RecruiterSettingsRepository recruiterSettingsRepository =
        mock(com.nexploy.campuslink.recruiter.repository.RecruiterSettingsRepository.class);
    private final com.nexploy.campuslink.auth.service.CampusAccessService campusAccessService =
        mock(com.nexploy.campuslink.auth.service.CampusAccessService.class);
    private final RecruiterService service =
        new RecruiterService(driveRepository, offerRepository, recruiterRepository, recruiterSettingsRepository,
            campusAccessService);

    @Test
    void derivesActiveDrivesAndJoinedHiresFromRealData() {
        Recruiter recruiter = Recruiter.builder()
            .id("rec_1")
            .campusId("campus_a")
            .name("Talent Team")
            .companyName("Acme Corp")
            .email("talent@acme.test")
            .phone("+91-99999-99999")
            .companyWebsite("acme.test")
            .companyIndustry("Software")
            .companyLocation("Bengaluru")
            .designation("Lead Recruiter")
            .active(true)
            .build();

        when(driveRepository.findByRecruiterId("rec_1")).thenReturn(List.of(
            Drive.builder().id("drv_active").recruiterId("rec_1").status(DriveStatus.ACTIVE).build(),
            Drive.builder().id("drv_closed").recruiterId("rec_1").status(DriveStatus.CLOSED).build()));
        when(offerRepository.findByRecruiterId("rec_1")).thenReturn(List.of(
            Offer.builder().id("off_1").recruiterId("rec_1").joined(true).build(),
            Offer.builder().id("off_2").recruiterId("rec_1").joined(true).build(),
            Offer.builder().id("off_3").recruiterId("rec_1").joined(false).build()));

        RecruiterResponse response = service.toResponse(recruiter);

        // Only ACTIVE drives count toward activeDriveIds.
        assertThat(response.activeDriveIds()).containsExactly("drv_active");
        // studentsHired = offers a student actually joined.
        assertThat(response.studentsHired()).isEqualTo(2);
        // Identity + company columns pass through unchanged.
        assertThat(response.id()).isEqualTo("rec_1");
        assertThat(response.companyName()).isEqualTo("Acme Corp");
        assertThat(response.phone()).isEqualTo("+91-99999-99999");
        assertThat(response.companyIndustry()).isEqualTo("Software");
        assertThat(response.companyLocation()).isEqualTo("Bengaluru");
        assertThat(response.designation()).isEqualTo("Lead Recruiter");
    }

    @Test
    void unknownRecruiterWithNoDrivesOrOffersYieldsEmptyAndZero() {
        Recruiter recruiter = Recruiter.builder().id("rec_empty").campusId("campus_a").companyName("New Co").build();
        when(driveRepository.findByRecruiterId("rec_empty")).thenReturn(List.of());
        when(offerRepository.findByRecruiterId("rec_empty")).thenReturn(List.of());

        RecruiterResponse response = service.toResponse(recruiter);

        assertThat(response.activeDriveIds()).isEmpty();
        assertThat(response.studentsHired()).isZero();
    }
}
