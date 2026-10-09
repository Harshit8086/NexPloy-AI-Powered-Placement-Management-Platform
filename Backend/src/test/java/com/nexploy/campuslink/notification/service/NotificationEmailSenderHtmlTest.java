package com.nexploy.campuslink.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.notification.entity.NotificationType;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Verifies the HTML email rendering in {@link NotificationEmailSender}: the sent message is
 * multipart/alternative (text + HTML), carries the branded layout, and escapes body content.
 */
class NotificationEmailSenderHtmlTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private NotificationEmailSender sender(boolean enabled) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return new NotificationEmailSender(enabled, "noreply@campuslink.local", provider);
    }

    @Test
    void sendsAMultipartEmailWithBrandedHtml() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        NotificationEmailSender sender = sender(true);

        sender.send("student@uni.edu", "Interview Scheduled with Acme",
            "Your Technical Round 1 interview with Acme is scheduled for 1 Feb 2026.",
            NotificationType.INTERVIEW_SCHEDULED);

        MimeMessage sent = captureSent();
        sent.saveChanges(); // populate headers (content type, recipients) before inspection
        assertThat(sent.getSubject()).isEqualTo("Interview Scheduled with Acme");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("student@uni.edu");
        assertThat(sent.getContentType()).startsWith("multipart/alternative");

        String content = contentOf(sent);
        assertThat(content).contains("CampusLink");                    // brand header
        assertThat(content).contains("Interview</span>");               // category chip
        assertThat(content).contains("Interview Scheduled with Acme");  // heading
        assertThat(content).contains("Technical Round 1");              // body copy
        assertThat(content).contains("This is an automated message");   // footer
    }

    @Test
    void escapesHtmlInSubjectAndBody() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        NotificationEmailSender sender = sender(true);

        sender.send("student@uni.edu", "A <b>bold</b> subject",
            "Body with <script>alert(1)</script> markup & entities",
            NotificationType.DOCUMENT_APPROVED);

        MimeMessage sent = captureSent();
        sent.saveChanges();
        String content = contentOf(sent);
        assertThat(content).doesNotContain("<script>");
        assertThat(content).contains("&lt;script&gt;");
        assertThat(content).contains("&amp; entities");
    }

    @Test
    void disabledSenderDoesNotTouchTheMailSender() {
        NotificationEmailSender sender = sender(false);

        sender.send("student@uni.edu", "Subject", "Body", NotificationType.SHORTLISTED);

        verify(mailSender, never()).createMimeMessage();
    }

    /**
     * Flattens every part of the sent multipart into one string for content assertions.
     * {@code message.getContent()} returns the {@link MimeMultipart} container, whose
     * {@code toString()} is not the MIME payload; each body part's {@code getContent()} yields the
     * decoded text (no quoted-printable soft line-break artifacts).
     */
    private static String contentOf(MimeMessage message) throws Exception {
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            content.append(multipart.getBodyPart(i).getContent());
        }
        return content.toString();
    }

    private MimeMessage captureSent() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }
}
