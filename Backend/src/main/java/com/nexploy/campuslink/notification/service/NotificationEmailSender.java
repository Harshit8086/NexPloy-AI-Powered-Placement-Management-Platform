package com.nexploy.campuslink.notification.service;

import com.nexploy.campuslink.notification.channel.NotificationSender;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationType;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends notifications over SMTP as branded HTML email (Notification-system Step 6; upgraded from
 * the plain-text {@code EmailNotificationSender} on 2026-10-05). Registered only when
 * {@code nexploy.notifications.email.enabled=true}; otherwise the bean is absent and the channel is
 * inert. The recipient username is already the email address in this system, so it is used directly
 * as the "to".
 *
 * <p>Each message is {@code multipart/alternative}: a plain-text rendering for text-only clients
 * plus a branded HTML rendering (CampusLink header, a category chip derived from the notification
 * type, the subject as a heading, the body, and an automated-message footer). Subject and body are
 * HTML-escaped before either part is built, so notification copy can never inject markup into the
 * email. The {@link JavaMailSender} is resolved lazily through an {@link ObjectProvider} (the same
 * pattern {@code OtpService} uses), so a deployment that enables the channel without a configured
 * mail host skips with a warning instead of failing startup. A build/send failure propagates for
 * the dispatcher to record as a failed attempt.
 */
@Component
@ConditionalOnProperty(prefix = "nexploy.notifications.email", name = "enabled", havingValue = "true")
public class NotificationEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailSender.class);

    private static final String FOOTER =
        "This is an automated message from CampusLink. Please do not reply to this email.";

    private final boolean enabled;
    private final String from;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public NotificationEmailSender(
            @Value("${nexploy.notifications.email.enabled:false}") boolean enabled,
            @Value("${spring.mail.username:}") String from,
            ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.enabled = enabled;
        this.from = from;
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    /** The dispatcher's channel contract — delivers with a generic category chip. */
    @Override
    public void send(String recipientUsername, String subject, String body) {
        send(recipientUsername, subject, body, null);
    }

    /** Typed send: the notification's type supplies the category chip in the branded layout. */
    public void send(String recipientUsername, String subject, String body, NotificationType type) {
        if (!enabled) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Email channel is enabled but no JavaMailSender is configured; "
                + "skipping notification email to {}", recipientUsername);
            return;
        }
        try {
            mailSender.send(buildMessage(mailSender, recipientUsername, subject, body, type));
        } catch (MessagingException e) {
            throw new MailPreparationException(
                "Failed to build notification email for " + recipientUsername, e);
        }
    }

    private MimeMessage buildMessage(JavaMailSender mailSender, String to, String subject,
                                     String body, NotificationType type) throws MessagingException {
        String safeSubject = escapeHtml(subject);
        String safeBody = escapeHtml(body);

        MimeMessage message = mailSender.createMimeMessage();
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        if (from != null && !from.isBlank()) {
            message.setFrom(new InternetAddress(from));
        }
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeMultipart multipart = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        // Both alternatives carry the escaped strings so neither part contains raw markup.
        textPart.setText(safeSubject + "\n\n" + safeBody + "\n\n" + FOOTER,
            StandardCharsets.UTF_8.name());
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(renderHtml(safeSubject, safeBody, categoryLabel(type)),
            "text/html; charset=UTF-8");
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(htmlPart);

        message.setContent(multipart);
        message.saveChanges();
        return message;
    }

    /** The header chip label for a notification type — one word a recipient can scan. */
    private static String categoryLabel(NotificationType type) {
        if (type == null) {
            return "Update";
        }
        return switch (type) {
            case OFFER_EXTENDED, OFFER_ACCEPTED, OFFER_DECLINED, OFFER_REVOKED,
                 OFFER_DEADLINE, OFFER_FOLLOWUP -> "Offer";
            case APPLICATION_ADVANCED, SHORTLISTED, SHORTLIST_REJECTED -> "Application";
            case INTERVIEW_SCHEDULED, INTERVIEW_RESCHEDULED, INTERVIEW_CANCELLED,
                 INTERVIEW_REMINDER, INTERVIEW_STARTING_SOON, INTERVIEW_SELECTED,
                 INTERVIEW_REJECTED -> "Interview";
            case DOCUMENT_APPROVED, DOCUMENT_REJECTED, DOCUMENT_REMINDER,
                 DOCUMENT_REVIEW_PENDING -> "Document";
            case DRIVE_PUBLISHED -> "Drive";
            case RISK_DETECTED, INTERVENTION_ASSIGNED -> "Support";
        };
    }

    /** Branded, email-client-safe HTML layout. All dynamic values arrive pre-escaped. */
    private static String renderHtml(String safeSubject, String safeBody, String category) {
        return "<!DOCTYPE html>"
            + "<html lang=\"en\">"
            + "<head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
            + "<title>" + safeSubject + "</title></head>"
            + "<body style=\"margin:0; padding:0; background-color:#f1f5f9;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"background-color:#f1f5f9; padding:24px 0;\">"
            + "<tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"max-width:600px; width:100%; background-color:#ffffff; "
            + "border:1px solid #e2e8f0; border-radius:12px; overflow:hidden;\">"
            // Brand header
            + "<tr><td style=\"background-color:#4338ca; padding:20px 32px;\">"
            + "<span style=\"font-family:Arial, Helvetica, sans-serif; font-size:20px; "
            + "font-weight:bold; color:#ffffff; letter-spacing:0.5px;\">CampusLink</span>"
            + "</td></tr>"
            // Message
            + "<tr><td style=\"padding:32px; font-family:Arial, Helvetica, sans-serif;\">"
            + "<span style=\"display:inline-block; background-color:#eef2ff; color:#4338ca; "
            + "font-size:11px; font-weight:bold; letter-spacing:1px; text-transform:uppercase; "
            + "padding:4px 12px; border-radius:999px;\">" + category + "</span>"
            + "<h1 style=\"font-size:20px; line-height:1.4; color:#0f172a; margin:16px 0 8px;\">"
            + safeSubject + "</h1>"
            + "<p style=\"font-size:15px; line-height:1.6; color:#334155; margin:0;\">"
            + safeBody + "</p>"
            + "</td></tr>"
            // Footer
            + "<tr><td style=\"background-color:#f8fafc; border-top:1px solid #e2e8f0; "
            + "padding:20px 32px; font-family:Arial, Helvetica, sans-serif;\">"
            + "<p style=\"font-size:12px; line-height:1.5; color:#64748b; margin:0;\">"
            + FOOTER + "</p>"
            + "<p style=\"font-size:12px; color:#94a3b8; margin:8px 0 0;\">&copy; CampusLink</p>"
            + "</td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    /** Minimal HTML escaping — enough to keep notification copy from injecting markup or entities. */
    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
