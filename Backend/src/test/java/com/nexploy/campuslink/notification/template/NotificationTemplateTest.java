package com.nexploy.campuslink.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexploy.campuslink.notification.entity.NotificationType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Asserts the rendered copy of the professional templates (upgraded 2026-10-05): token substitution,
 * clause-based interview wording, and the exact subject/body strings the event handlers and the
 * reminder sweeps assert downstream.
 */
class NotificationTemplateTest {

    @Test
    void substitutesNamedVariables() {
        RenderedNotification rendered = NotificationTemplate.OFFER_EXTENDED_TPL
            .render(Map.of("role", "SDE"));

        assertThat(rendered.subject()).isEqualTo("New Offer Extended — SDE");
        assertThat(rendered.body()).isEqualTo(
            "Great news! You have received an offer for the SDE position. "
                + "Please review the offer details and respond at your earliest convenience.");
    }

    @Test
    void unknownTokensAreLeftUntouched() {
        // {type}/{hours} are the only tokens here; an unrelated variable changes nothing.
        RenderedNotification rendered = NotificationTemplate.DOCUMENT_REMINDER_TPL
            .render(Map.of("ignored", "value"));

        assertThat(rendered.body())
            .isEqualTo("Your {type} document has been awaiting review for more than {hours} hours.");
    }

    @Test
    void interviewClausesAreSubstitutedInPlace() {
        RenderedNotification rendered = NotificationTemplate.INTERVIEW_SCHEDULED_TPL
            .render(Map.of("round", " Technical Round 1", "company", " with Acme",
                "time", " on 2026-01-01T10:00Z"));

        assertThat(rendered.subject()).isEqualTo("Interview Scheduled with Acme");
        assertThat(rendered.body()).isEqualTo(
            "Your Technical Round 1 interview with Acme has been scheduled on 2026-01-01T10:00Z. "
                + "Please be prepared and arrive on time.");
    }

    @Test
    void emptyClausesRenderNaturally() {
        RenderedNotification rendered = NotificationTemplate.INTERVIEW_CANCELLED_TPL
            .render(Map.of("round", "", "company", ""));

        assertThat(rendered.subject()).isEqualTo("Interview Cancelled");
        assertThat(rendered.body()).isEqualTo(
            "Your interview has been cancelled. "
                + "Please contact your placement office if you have any questions.");
    }

    @Test
    void reminderTemplatesSubstituteTheirTokens() {
        RenderedNotification doc = NotificationTemplate.DOCUMENT_REMINDER_TPL
            .render(Map.of("type", "Marksheet", "hours", "24"));
        assertThat(doc.body())
            .isEqualTo("Your Marksheet document has been awaiting review for more than 24 hours.");

        RenderedNotification digest = NotificationTemplate.DOCUMENT_REVIEW_PENDING_TPL
            .render(Map.of("count", "7"));
        assertThat(digest.body()).isEqualTo("7 document(s) are pending review across your campus.");

        RenderedNotification followup = NotificationTemplate.OFFER_FOLLOWUP_TPL
            .render(Map.of("company", " with Acme", "offerId", "offer_1"));
        assertThat(followup.body())
            .isEqualTo("Your offer with Acme (reference: offer_1) is still awaiting the candidate's "
                + "response. Consider following up.");
    }

    @Test
    void everyNotificationTypeHasExactlyOneTemplate() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(NotificationTemplate.forType(type)).isNotNull();
        }
    }

    @ParameterizedTest
    @EnumSource(NotificationTemplate.class)
    void everyTemplateRendersNonBlankSubjectAndBodyWithNoVariables(NotificationTemplate template) {
        RenderedNotification rendered = template.render(Map.of());
        assertThat(rendered.subject()).isNotBlank();
        assertThat(rendered.body()).isNotBlank();
    }

    @Test
    void nullVariablesMapIsTolerated() {
        RenderedNotification rendered = NotificationTemplate.OFFER_REVOKED_TPL.render(null);
        assertThat(rendered.subject()).isEqualTo("Offer Withdrawn");
    }
}