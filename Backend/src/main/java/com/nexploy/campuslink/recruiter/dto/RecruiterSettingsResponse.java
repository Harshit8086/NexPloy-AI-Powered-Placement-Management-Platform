package com.nexploy.campuslink.recruiter.dto;

import com.nexploy.campuslink.recruiter.entity.RecruiterSettings;

/**
 * The recruiter's persisted settings as served to the frontend Settings page. Mirrors the
 * {@link RecruiterSettings} entity one-for-one. Used for both GET (current values) and as the
 * shape returned after a PUT.
 */
public record RecruiterSettingsResponse(
    boolean notifyNewApplication,
    boolean notifyShortlisted,
    boolean notifyInterview,
    boolean notifyOffer,
    boolean notifyDrive,
    boolean emailNotifications,
    boolean inAppNotifications,
    String theme,
    String language,
    String timezone,
    String dateFormat,
    String profileVisibility,
    String contactVisibility,
    boolean allowDataAnalytics,
    boolean allowMarketingEmail
) {

    public static RecruiterSettingsResponse from(RecruiterSettings s) {
        return new RecruiterSettingsResponse(
            s.isNotifyNewApplication(),
            s.isNotifyShortlisted(),
            s.isNotifyInterview(),
            s.isNotifyOffer(),
            s.isNotifyDrive(),
            s.isEmailNotifications(),
            s.isInAppNotifications(),
            s.getTheme(),
            s.getLanguage(),
            s.getTimezone(),
            s.getDateFormat(),
            s.getProfileVisibility(),
            s.getContactVisibility(),
            s.isAllowDataAnalytics(),
            s.isAllowMarketingEmail()
        );
    }
}
