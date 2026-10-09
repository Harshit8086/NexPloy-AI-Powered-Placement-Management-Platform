package com.nexploy.campuslink.notification.template;

import com.nexploy.campuslink.notification.entity.NotificationType;
import java.util.Map;

/**
 * Reusable message templates for notifications (Notification-system Step 5). Each
 * {@link NotificationType} maps 1:1 to a subject + body template; {@code {placeholder}} tokens are
 * filled from a variables map at render time. This centralises the wording that used to be built
 * inline in {@code NotificationEventListener}, so every channel (in-app today; email/SMS later)
 * renders from the same source and the copy is edited in one place.
 *
 * <p>Rendering is deliberately trivial (literal {@code {key}} substitution, no expression language)
 * — it runs in an after-commit path and must never fail. An unknown token is left untouched rather
 * than throwing; a template with no tokens simply ignores the variables.
 */
public enum NotificationTemplate {

    OFFER_EXTENDED_TPL(NotificationType.OFFER_EXTENDED,
        "New Offer Extended — {role}",
        "Great news! You have received an offer for the {role} position. "
            + "Please review the offer details and respond at your earliest convenience."),
    OFFER_ACCEPTED_TPL(NotificationType.OFFER_ACCEPTED,
        "Offer Accepted — Reference {offerId}",
        "A candidate has accepted your offer (reference: {offerId}). "
            + "You can now proceed with the next steps in your hiring pipeline."),
    OFFER_DECLINED_TPL(NotificationType.OFFER_DECLINED,
        "Offer Declined — Reference {offerId}",
        "A candidate has declined your offer (reference: {offerId}). "
            + "You may wish to review your shortlist for other suitable candidates."),
    OFFER_REVOKED_TPL(NotificationType.OFFER_REVOKED,
        "Offer Withdrawn",
        "We regret to inform you that an offer extended to you has been withdrawn. "
            + "Please contact your placement office for further details."),
    APPLICATION_ADVANCED_TPL(NotificationType.APPLICATION_ADVANCED,
        "Application Advanced — {stage} Stage",
        "Congratulations! Your application has advanced to the {stage} stage. "
            + "Keep an eye on your dashboard for further updates."),
    SHORTLISTED_TPL(NotificationType.SHORTLISTED,
        "You've Been Shortlisted!",
        "Congratulations! You have been shortlisted for a placement drive. "
            + "Watch your dashboard for the next steps."),
    SHORTLIST_REJECTED_TPL(NotificationType.SHORTLIST_REJECTED,
        "Application Update",
        "Thank you for applying. Unfortunately, you were not shortlisted for this drive. "
            + "Keep applying — more opportunities await."),
    INTERVIEW_SCHEDULED_TPL(NotificationType.INTERVIEW_SCHEDULED,
        "Interview Scheduled{company}",
        "Your{round} interview{company} has been scheduled{time}. "
            + "Please be prepared and arrive on time."),
    INTERVIEW_RESCHEDULED_TPL(NotificationType.INTERVIEW_RESCHEDULED,
        "Interview Rescheduled{company}",
        "Your{round} interview{company} has been moved{time}. "
            + "Please note the updated schedule."),
    INTERVIEW_CANCELLED_TPL(NotificationType.INTERVIEW_CANCELLED,
        "Interview Cancelled{company}",
        "Your{round} interview{company} has been cancelled. "
            + "Please contact your placement office if you have any questions."),
    INTERVIEW_REMINDER_TPL(NotificationType.INTERVIEW_REMINDER,
        "Interview Reminder{company}",
        "Reminder: your{round} interview{company} is coming up{time}. "
            + "Please make sure you are prepared."),
    INTERVIEW_STARTING_SOON_TPL(NotificationType.INTERVIEW_STARTING_SOON,
        "Interview Starting Soon{company}",
        "Your{round} interview{company} starts within the hour{time}. Get ready!"),
    INTERVIEW_SELECTED_TPL(NotificationType.INTERVIEW_SELECTED,
        "Congratulations — You've Been Selected!",
        "Congratulations! You have been selected in your{round} interview{company}. "
            + "Watch your dashboard for the next steps."),
    INTERVIEW_REJECTED_TPL(NotificationType.INTERVIEW_REJECTED,
        "Interview Result{company}",
        "Your{round} interview result{company} is in — you were not selected this time. "
            + "Keep going, more opportunities await."),
    DOCUMENT_APPROVED_TPL(NotificationType.DOCUMENT_APPROVED,
        "Document Approved",
        "One of your documents has been approved. No further action is required."),
    DOCUMENT_REJECTED_TPL(NotificationType.DOCUMENT_REJECTED,
        "Document Needs Attention",
        "One of your documents was not approved. Please review the feedback and re-upload it."),
    DRIVE_PUBLISHED_TPL(NotificationType.DRIVE_PUBLISHED,
        "New Drive Published",
        "A new placement drive has been published. Review it and apply if you're eligible."),
    OFFER_DEADLINE_TPL(NotificationType.OFFER_DEADLINE,
        "Offer Response Due Soon{company}",
        "Your offer{company} is awaiting your response. Please respond by {deadline}."),
    DOCUMENT_REMINDER_TPL(NotificationType.DOCUMENT_REMINDER,
        "Document Awaiting Review",
        "Your {type} document has been awaiting review for more than {hours} hours."),
    DOCUMENT_REVIEW_PENDING_TPL(NotificationType.DOCUMENT_REVIEW_PENDING,
        "Documents Pending Review",
        "{count} document(s) are pending review across your campus."),
    OFFER_FOLLOWUP_TPL(NotificationType.OFFER_FOLLOWUP,
        "Offer Awaiting Response{company}",
        "Your offer{company} (reference: {offerId}) is still awaiting the candidate's response. "
            + "Consider following up."),
    RISK_DETECTED_TPL(NotificationType.RISK_DETECTED,
        "Student Needs Support — {band} Band",
        "Student {studentId} has been flagged in the {band} support band. Consider reaching out."),
    INTERVENTION_ASSIGNED_TPL(NotificationType.INTERVENTION_ASSIGNED,
        "Intervention Plan Assigned",
        "An intervention plan has been assigned to you for student {studentId}. "
            + "Please review it at your earliest convenience.");

    private final NotificationType type;
    private final String subjectTemplate;
    private final String bodyTemplate;

    NotificationTemplate(NotificationType type, String subjectTemplate, String bodyTemplate) {
        this.type = type;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
    }

    public NotificationType type() {
        return type;
    }

    /** The template for a type; every {@link NotificationType} has exactly one. */
    public static NotificationTemplate forType(NotificationType type) {
        for (NotificationTemplate template : values()) {
            if (template.type == type) {
                return template;
            }
        }
        throw new IllegalArgumentException("No notification template for type " + type);
    }

    /** Renders the subject + body for this template with the given variables. */
    public RenderedNotification render(Map<String, String> variables) {
        return new RenderedNotification(
            substitute(subjectTemplate, variables),
            substitute(bodyTemplate, variables));
    }

    /** Replaces every {@code {key}} in {@code template} with its value; unknown tokens are left as-is. */
    private static String substitute(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}",
                entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
