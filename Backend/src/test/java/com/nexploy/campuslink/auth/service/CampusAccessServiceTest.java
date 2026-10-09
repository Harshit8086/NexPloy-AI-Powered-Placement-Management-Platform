package com.nexploy.campuslink.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import org.junit.jupiter.api.Test;

class CampusAccessServiceTest {

    private final CampusAccessService campusAccessService = new CampusAccessService();

    @Test
    void officerOnlyAccessesOwnCampus() {
        UserAccount officer = UserAccount.builder()
            .username("officer")
            .campusId("campus_demo")
            .role(UserRole.OFFICER)
            .enabled(true)
            .build();

        assertThat(campusAccessService.canAccessCampus(officer, "campus_demo")).isTrue();
        assertThat(campusAccessService.canAccessCampus(officer, "other_campus")).isFalse();
    }

    @Test
    void adminCanAccessAnyCampus() {
        UserAccount admin = UserAccount.builder()
            .username("admin")
            .campusId("campus_demo")
            .role(UserRole.ADMIN)
            .enabled(true)
            .build();

        assertThat(campusAccessService.canAccessCampus(admin, "campus_demo")).isTrue();
        assertThat(campusAccessService.canAccessCampus(admin, "other_campus")).isTrue();
    }

    @Test
    void recruiterOnlyAccessesOwnCampus() {
        UserAccount recruiter = UserAccount.builder()
            .username("recruiter")
            .campusId("campus_demo")
            .role(UserRole.RECRUITER)
            .enabled(true)
            .build();

        assertThat(campusAccessService.canAccessCampus(recruiter, "campus_demo")).isTrue();
        assertThat(campusAccessService.canAccessCampus(recruiter, "other_campus")).isFalse();
    }
}
