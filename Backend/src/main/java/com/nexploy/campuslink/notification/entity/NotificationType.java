package com.nexploy.campuslink.notification.entity;

/**
 * Semantic type of a notification — the business fact it represents. Distinct from
 * {@link NotificationChannel} (how it is delivered) and {@link NotificationStatus} (its delivery
 * state). The type drives client-side deep-linking (a type + {@code referenceId} tells the UI which
 * page to open) and per-category preference filtering. Manually-created notifications may carry no
 * type; event-driven ones always do.
 */
public enum NotificationType {

    // Offer lifecycle
    OFFER_EXTENDED,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    OFFER_REVOKED,

    // Application / shortlist
    APPLICATION_ADVANCED,
    SHORTLISTED,
    SHORTLIST_REJECTED,

    // Interview lifecycle
    INTERVIEW_SCHEDULED,
    INTERVIEW_RESCHEDULED,
    INTERVIEW_CANCELLED,
    INTERVIEW_REMINDER,
    INTERVIEW_STARTING_SOON,  // student: interview begins within the hour (closer follow-up reminder)
    INTERVIEW_SELECTED,   // student cleared the interview (result recorded as selected)
    INTERVIEW_REJECTED,   // student was not selected in the interview

    // Documents
    DOCUMENT_APPROVED,
    DOCUMENT_REJECTED,

    // Drives
    DRIVE_PUBLISHED,

    // Time-based reminders (scheduler-driven)
    OFFER_DEADLINE,
    DOCUMENT_REMINDER,        // student: your uploaded document is still awaiting review
    DOCUMENT_REVIEW_PENDING,  // officer: N documents on your campus are awaiting review
    OFFER_FOLLOWUP,           // recruiter: an offer you extended is still awaiting a response

    // Support / risk (AI-driven)
    RISK_DETECTED,
    INTERVENTION_ASSIGNED
}
