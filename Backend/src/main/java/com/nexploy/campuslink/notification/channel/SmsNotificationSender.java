package com.nexploy.campuslink.notification.channel;

import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sends notifications over SMS via Twilio (Notification-system Step 7). Registered only when
 * {@code nexploy.notifications.sms.enabled=true}; otherwise the bean is absent, no credentials are
 * read, and no Twilio call is ever made. Only high-signal types reach this channel (see the SMS
 * policy in {@code NotificationService}) to conserve paid message credit.
 *
 * <p>The recipient username is an email; the phone number is resolved from the student profile.
 * SMS recipients are always students in the critical set, so a single {@link StudentRepository}
 * lookup suffices. A missing/blank phone throws, which the dispatcher records as a failed attempt.
 */
@Component
@ConditionalOnProperty(prefix = "nexploy.notifications.sms", name = "enabled", havingValue = "true")
public class SmsNotificationSender implements NotificationSender {

    private final StudentRepository studentRepository;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public SmsNotificationSender(StudentRepository studentRepository,
                                 @Value("${twilio.account-sid:}") String accountSid,
                                 @Value("${twilio.auth-token:}") String authToken,
                                 @Value("${nexploy.notifications.sms.from:}") String fromNumber) {
        this.studentRepository = studentRepository;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @PostConstruct
    void init() {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            throw new IllegalStateException(
                "SMS channel is enabled but twilio.account-sid / twilio.auth-token are not configured");
        }
        Twilio.init(accountSid, authToken);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(String recipientUsername, String subject, String body) {
        String phone = studentRepository.findByEmail(recipientUsername)
            .map(Student::getPhone)
            .filter(p -> p != null && !p.isBlank())
            .orElseThrow(() -> new IllegalStateException("No phone number on file for " + recipientUsername));
        Message.creator(new PhoneNumber(phone), new PhoneNumber(fromNumber), body).create();
    }
}
