package com.nexploy.campuslink.recruiter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-recruiter preferences (notifications, UI preferences, privacy) that back the Recruiter
 * Settings page. Persisted so a recruiter's choices survive reload/login rather than living in
 * hardcoded frontend state. Created lazily with defaults on first read (see RecruiterService).
 * The primary key is the recruiter id (one settings row per recruiter).
 */
@Entity
@Table(name = "recruiter_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruiterSettings {

    @Id
    private String recruiterId;

    // Notifications
    private boolean notifyNewApplication;
    private boolean notifyShortlisted;
    private boolean notifyInterview;
    private boolean notifyOffer;
    private boolean notifyDrive;
    private boolean emailNotifications;
    private boolean inAppNotifications;

    // Preferences
    private String theme;
    private String language;
    private String timezone;
    private String dateFormat;

    // Privacy
    private String profileVisibility;
    private String contactVisibility;
    private boolean allowDataAnalytics;
    private boolean allowMarketingEmail;

    /** A fresh settings row with the same production-sensible defaults the migration declares. */
    public static RecruiterSettings defaults(String recruiterId) {
        return RecruiterSettings.builder()
            .recruiterId(recruiterId)
            .notifyNewApplication(true)
            .notifyShortlisted(true)
            .notifyInterview(true)
            .notifyOffer(true)
            .notifyDrive(true)
            .emailNotifications(true)
            .inAppNotifications(true)
            .theme("dark")
            .language("en")
            .timezone("Asia/Kolkata")
            .dateFormat("DD MMM YYYY")
            .profileVisibility("CAMPUS")
            .contactVisibility("CAMPUS")
            .allowDataAnalytics(true)
            .allowMarketingEmail(false)
            .build();
    }
}
