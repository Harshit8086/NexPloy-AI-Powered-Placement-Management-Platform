package com.nexploy.campuslink.recruiter.dto;

import jakarta.validation.constraints.Size;

/**
 * Recruiter settings update (PUT /api/recruiters/me/settings). Every field is optional: a null
 * means "leave unchanged", so the client may PATCH a single section (e.g. just Notifications).
 * Booleans are boxed so null is distinguishable from false. Enumerated string fields
 * (visibility, theme) are validated by size here and normalized/clamped in the service.
 */
public record RecruiterSettingsRequest(
    Boolean notifyNewApplication,
    Boolean notifyShortlisted,
    Boolean notifyInterview,
    Boolean notifyOffer,
    Boolean notifyDrive,
    Boolean emailNotifications,
    Boolean inAppNotifications,

    @Size(max = 16) String theme,
    @Size(max = 16) String language,
    @Size(max = 64) String timezone,
    @Size(max = 24) String dateFormat,

    @Size(max = 24) String profileVisibility,
    @Size(max = 24) String contactVisibility,
    Boolean allowDataAnalytics,
    Boolean allowMarketingEmail
) {
}
