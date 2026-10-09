package com.nexploy.campuslink.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.company.dto.CompanyDriveSummary;
import com.nexploy.campuslink.company.dto.CompanyResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyServiceTest {

    private final DriveRepository driveRepository = mock(DriveRepository.class);
    private final CompanyService service = new CompanyService(driveRepository);

    @Test
    void aggregatesRealDrivesIntoCompanyProfile() {
        Recruiter recruiter = Recruiter.builder()
            .id("rec_1")
            .campusId("campus_a")
            .companyName("Acme Corp")
            .companyIndustry("Software")
            .companyWebsite("acme.test")
            .companyLocation("Bengaluru")
            .active(true)
            .build();

        when(driveRepository.findByRecruiterId("rec_1")).thenReturn(List.of(
            Drive.builder().id("drv_b").recruiterId("rec_1").role("Backend Engineer")
                .status(DriveStatus.ACTIVE).location("Bengaluru")
                .packageLpa(new BigDecimal("18.5")).deadline(LocalDate.of(2026, 10, 1)).build(),
            Drive.builder().id("drv_a").recruiterId("rec_1").role("Data Analyst")
                .status(DriveStatus.CLOSED).build()));

        CompanyResponse response = service.toResponse(recruiter);

        // Identity + company columns pass through unchanged.
        assertThat(response.id()).isEqualTo("rec_1");
        assertThat(response.name()).isEqualTo("Acme Corp");
        assertThat(response.industry()).isEqualTo("Software");
        assertThat(response.location()).isEqualTo("Bengaluru");
        // Counts derived from real drives; only ACTIVE counts as active.
        assertThat(response.totalDriveCount()).isEqualTo(2);
        assertThat(response.activeDriveCount()).isEqualTo(1);
        // Summaries sorted by drive id, fields copied straight from the drive row.
        assertThat(response.drives()).extracting(CompanyDriveSummary::driveId).containsExactly("drv_a", "drv_b");
        assertThat(response.drives().get(1).role()).isEqualTo("Backend Engineer");
        assertThat(response.drives().get(1).status()).isEqualTo("ACTIVE");
        assertThat(response.drives().get(1).packageLpa()).isEqualByComparingTo("18.5");
        assertThat(response.drives().get(1).deadline()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void companyWithNoDrivesYieldsZeroCountsAndEmptyList() {
        Recruiter recruiter = Recruiter.builder().id("rec_empty").campusId("campus_a")
            .companyName("New Co").build();
        when(driveRepository.findByRecruiterId("rec_empty")).thenReturn(List.of());

        CompanyResponse response = service.toResponse(recruiter);

        assertThat(response.totalDriveCount()).isZero();
        assertThat(response.activeDriveCount()).isZero();
        assertThat(response.drives()).isEmpty();
        // Unknown company columns stay null rather than being fabricated.
        assertThat(response.industry()).isNull();
        assertThat(response.website()).isNull();
    }
}
