package com.nexploy.campuslink.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.analytics.dto.PlacementSummaryResponse;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AnalyticsServiceTest {

    private final StudentRepository studentRepository = org.mockito.Mockito.mock(StudentRepository.class);
    private final DriveRepository driveRepository = org.mockito.Mockito.mock(DriveRepository.class);
    private final OfferRepository offerRepository = org.mockito.Mockito.mock(OfferRepository.class);
    private final ApplicationRepository applicationRepository =
        org.mockito.Mockito.mock(ApplicationRepository.class);
    private final com.nexploy.campuslink.campus.repository.CampusRepository campusRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.campus.repository.CampusRepository.class);
    private final com.nexploy.campuslink.student.repository.StudentSkillRepository studentSkillRepository =
        org.mockito.Mockito.mock(com.nexploy.campuslink.student.repository.StudentSkillRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final AnalyticsService service = new AnalyticsService(
        studentRepository, driveRepository, offerRepository, applicationRepository, campusAccessService,
        campusRepository, studentSkillRepository);

    @Test
    void aggregatesPlacementSummaryWithAcceptedOfferAsPlaced() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        // placementSummary is now fully set-based: it stubs the aggregate queries, not entity lists.
        when(studentRepository.countByCampusId("campus_demo")).thenReturn(3L);
        when(studentRepository.averageReadinessByCampusId("campus_demo")).thenReturn(70.0);
        when(studentRepository.countByPlacementStatusForCampus("campus_demo"))
            .thenReturn(List.<Object[]>of(row("ACTIVE", 3L)));
        when(driveRepository.countByCampusId("campus_demo")).thenReturn(3L);
        when(driveRepository.countByStatusForCampus("campus_demo")).thenReturn(List.<Object[]>of(
            row(DriveStatus.ACTIVE, 2L), row(DriveStatus.CLOSED, 1L)));
        when(offerRepository.countByCampusId("campus_demo")).thenReturn(3L);
        when(offerRepository.countPlacedStudentsByCampusId("campus_demo")).thenReturn(1L);
        when(offerRepository.countByStatusForCampus("campus_demo")).thenReturn(List.<Object[]>of(
            row(OfferStatus.ACCEPTED, 1L), row(OfferStatus.DECLINED, 1L), row(OfferStatus.EXTENDED, 1L)));
        when(applicationRepository.countByCampusId("campus_demo")).thenReturn(3L);
        when(applicationRepository.countByStatusForCampus("campus_demo")).thenReturn(List.<Object[]>of(
            row(ApplicationStatus.APPLIED, 2L), row(ApplicationStatus.SELECTED, 1L)));

        PlacementSummaryResponse summary = service.placementSummary("campus_demo");

        assertThat(summary.studentCount()).isEqualTo(3);
        // Only the accepted offer counts as placed.
        assertThat(summary.placedStudentCount()).isEqualTo(1);
        assertThat(summary.placementRate()).isEqualTo(0.3333);
        // Null readiness excluded from the average: (80 + 60) / 2.
        assertThat(summary.averageReadinessScore()).isEqualTo(70.0);
        assertThat(summary.driveCount()).isEqualTo(3);
        assertThat(summary.drivesByStatus()).containsEntry("ACTIVE", 2L).containsEntry("CLOSED", 1L);
        assertThat(summary.offerCount()).isEqualTo(3);
        assertThat(summary.offersByStatus())
            .containsEntry("ACCEPTED", 1L).containsEntry("DECLINED", 1L).containsEntry("EXTENDED", 1L);
        // Acceptance rate is over decided offers only (1 accepted / 2 decided).
        assertThat(summary.offerAcceptanceRate()).isEqualTo(0.5);
        assertThat(summary.applicationCount()).isEqualTo(3);
        assertThat(summary.applicationsByStatus()).containsEntry("APPLIED", 2L).containsEntry("SELECTED", 1L);
    }

    @Test
    void emptyCampusYieldsZeroRatesWithoutDivideByZero() {
        authenticate(user(UserRole.OFFICER, "campus_empty"));
        // Count/group-by mocks default to 0 / empty list; an empty cohort has no readiness average.
        when(studentRepository.averageReadinessByCampusId("campus_empty")).thenReturn(null);

        PlacementSummaryResponse summary = service.placementSummary("campus_empty");

        assertThat(summary.studentCount()).isZero();
        assertThat(summary.placementRate()).isZero();
        assertThat(summary.offerAcceptanceRate()).isZero();
        assertThat(summary.averageReadinessScore()).isNull();
    }

    @Test
    void recruiterCannotReadAnalyticsForAnotherCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));

        assertThatThrownBy(() -> service.placementSummary("campus_other"))
            .isInstanceOf(AccessDeniedException.class);
    }

    /** Only admins get the cross-campus roll-up; officers are pinned to their own campus. */
    @Test
    void instituteRollsUpAcrossAllCampusesForAdmin() {
        authenticate(user(UserRole.ADMIN, "cmp_a"));
        when(campusRepository.findAll()).thenReturn(List.of(
            campus("cmp_a", "Alpha", "CityA"), campus("cmp_b", "Beta", "CityB")));

        when(studentRepository.findByCampusId("cmp_a")).thenReturn(List.of(
            student("stu_a", 80), student("stu_b", 50)));
        when(driveRepository.findByCampusId("cmp_a")).thenReturn(List.of(drive(DriveStatus.ACTIVE)));
        when(offerRepository.findByCampusId("cmp_a")).thenReturn(List.of(
            offer("stu_a", OfferStatus.ACCEPTED)));

        when(studentRepository.findByCampusId("cmp_b")).thenReturn(List.of());
        when(driveRepository.findByCampusId("cmp_b")).thenReturn(List.of());
        when(offerRepository.findByCampusId("cmp_b")).thenReturn(List.of());

        var institute = service.institute();

        assertThat(institute.campuses()).hasSize(2);
        assertThat(institute.totals().totalStudents()).isEqualTo(2);
        assertThat(institute.totals().placementReady()).isEqualTo(1); // only readiness 80 >= 70
        assertThat(institute.totals().placed()).isEqualTo(1);         // one accepted offer
        assertThat(institute.totals().placementRate()).isEqualTo(50); // 1 of 2
        assertThat(institute.totals().recruiters()).isEqualTo(1);
    }

    @Test
    void officerInstituteIsScopedToOwnCampusOnly() {
        authenticate(user(UserRole.OFFICER, "cmp_a"));
        when(campusRepository.findById("cmp_a")).thenReturn(
            java.util.Optional.of(campus("cmp_a", "Alpha", "CityA")));
        when(studentRepository.findByCampusId("cmp_a")).thenReturn(List.of(student("stu_a", 90)));
        when(driveRepository.findByCampusId("cmp_a")).thenReturn(List.of());
        when(offerRepository.findByCampusId("cmp_a")).thenReturn(List.of());

        var institute = service.institute();

        assertThat(institute.campuses()).extracting("campusId").containsExactly("cmp_a");
        assertThat(institute.totals().totalStudents()).isEqualTo(1);
    }

    @Test
    void recruiterInstituteIsScopedToOwnCampusOnly() {
        authenticate(user(UserRole.RECRUITER, "cmp_a"));
        when(campusRepository.findById("cmp_a")).thenReturn(
            java.util.Optional.of(campus("cmp_a", "Alpha", "CityA")));
        when(studentRepository.findByCampusId("cmp_a")).thenReturn(List.of(student("stu_a", 90)));
        when(driveRepository.findByCampusId("cmp_a")).thenReturn(List.of());
        when(offerRepository.findByCampusId("cmp_a")).thenReturn(List.of());

        var institute = service.institute();

        assertThat(institute.campuses()).extracting("campusId").containsExactly("cmp_a");
        assertThat(institute.totals().totalStudents()).isEqualTo(1);
    }

    @Test
    void skillAnalyticsCountsActiveDriveDemandAgainstCohortCoverage() {
        authenticate(user(UserRole.OFFICER, "cmp_a"));
        Drive active = drive(DriveStatus.ACTIVE);
        active.setRequiredSkillsJson("[\"Java\",\"AWS\"]");
        Drive closed = drive(DriveStatus.CLOSED);
        closed.setRequiredSkillsJson("[\"Java\"]"); // ignored: not active
        when(driveRepository.findByCampusId("cmp_a")).thenReturn(List.of(active, closed));
        when(studentRepository.findByCampusId("cmp_a")).thenReturn(List.of(
            student("stu_a", 80), student("stu_b", 70)));
        when(studentSkillRepository.findByCampusId("cmp_a")).thenReturn(List.of(
            skill("stu_a", "java"), skill("stu_b", "Java"))); // both know Java, neither AWS

        var skills = service.skillAnalytics("cmp_a");

        assertThat(skills.demand()).extracting("skill").containsExactlyInAnyOrder("Java", "AWS");
        assertThat(skills.supply()).anySatisfy(s -> {
            assertThat(s.skill()).isEqualTo("Java");
            assertThat(s.cohortCoverage()).isEqualTo(100); // 2 of 2
        });
        assertThat(skills.supply()).anySatisfy(s -> {
            assertThat(s.skill()).isEqualTo("AWS");
            assertThat(s.cohortCoverage()).isZero();
        });
    }

    private Student student(String id, Integer readiness) {
        return Student.builder()
            .id(id).campusId("campus_demo").name("Demo").email(id + "@e.example")
            .rollNumber(id).branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.0")).backlogs(0).readinessScore(readiness)
            .placementStatus("ACTIVE").build();
    }

    private Drive drive(DriveStatus status) {
        return Drive.builder().id("drive_" + System.nanoTime()).campusId("campus_demo")
            .recruiterId("rec_1").companyName("Acme").role("SDE").location("Remote")
            .packageLpa(new BigDecimal("12.0")).minimumCgpa(new BigDecimal("7.0"))
            .maximumBacklogs(0).graduationYear(2026).status(status).build();
    }

    private Offer offer(String studentId, OfferStatus status) {
        return Offer.builder().id("offer_" + System.nanoTime()).campusId("campus_demo")
            .studentId(studentId).driveId("drive_1").recruiterId("rec_1").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(status).build();
    }

    /** A [key, count] aggregate row as the GROUP BY @Query methods return. */
    private Object[] row(Object key, long count) {
        return new Object[] {key, count};
    }

    private com.nexploy.campuslink.campus.entity.Campus campus(String id, String name, String city) {
        return com.nexploy.campuslink.campus.entity.Campus.builder()
            .id(id).name(name).city(city).state("ST").active(true).build();
    }

    private com.nexploy.campuslink.student.entity.StudentSkill skill(String studentId, String label) {
        return com.nexploy.campuslink.student.entity.StudentSkill.builder()
            .id("sk_" + System.nanoTime()).studentId(studentId).campusId("cmp_a")
            .skill(label).proficiency(70).source("SELF_REPORTED").build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder().username("user-" + role).password("{noop}s")
            .campusId(campusId).role(role).enabled(true).build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
