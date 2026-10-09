package com.nexploy.campuslink.notification.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.notification.dto.CreateNotificationRequest;
import com.nexploy.campuslink.notification.dto.DeliveryAttemptResponse;
import com.nexploy.campuslink.notification.dto.NotificationResponse;
import com.nexploy.campuslink.notification.entity.DeliveryAttempt;
import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.exception.NotificationNotFoundException;
import com.nexploy.campuslink.notification.exception.NotificationNotRetryableException;
import com.nexploy.campuslink.notification.repository.DeliveryAttemptRepository;
import com.nexploy.campuslink.notification.repository.NotificationRepository;
import com.nexploy.campuslink.shared.dto.PageResponse;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    /**
     * Types worth a paid SMS (Notification-system Step 7). SMS is reserved for the high-signal,
     * time-critical set the user approved; every other type stays in-app/email only.
     */
    private static final Set<NotificationType> SMS_CRITICAL = EnumSet.of(
        NotificationType.OFFER_EXTENDED,
        NotificationType.OFFER_DEADLINE,
        NotificationType.INTERVIEW_SCHEDULED,
        NotificationType.INTERVIEW_REMINDER,
        NotificationType.INTERVIEW_STARTING_SOON);

    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final CampusAccessService campusAccessService;
    private final NotificationPreferenceService preferenceService;

    /**
     * Outbound-channel toggles, field-injected (not constructor params) so the existing
     * constructor signature — depended on by unit tests — is preserved. Default false, so in the
     * test profile and any unconfigured deployment only the in-app row is created.
     */
    @Value("${nexploy.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${nexploy.notifications.sms.enabled:false}")
    private boolean smsEnabled;

    /**
     * Lists notifications. Admins see the requested campus scope; every other role,
     * officers included, sees only notifications addressed to them — and only the in-app
     * channel, so the email/SMS delivery rows created by multi-channel fan-out stay in the
     * admin console and never surface in the user's feed.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see just notifications addressed to them.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        List<Notification> notifications;
        if (privileged) {
            notifications = campusId == null || campusId.isBlank()
                ? notificationRepository.findAll()
                : notificationRepository.findByCampusId(campusId);
        } else {
            // In-app feed only: email/SMS rows are delivery mechanics for the admin console.
            notifications = notificationRepository.findByRecipientUsernameAndChannel(
                user.getUsername(), NotificationChannel.IN_APP);
        }
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    /** Paged variant of {@link #list(String)} — same visibility rules, wrapped in a PageResponse envelope. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String campusId, Pageable pageable) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see just notifications addressed to them.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        Page<Notification> notifications;
        if (privileged) {
            notifications = campusId == null || campusId.isBlank()
                ? notificationRepository.findAll(pageable)
                : notificationRepository.findByCampusId(campusId, pageable);
        } else {
            notifications = notificationRepository.findByRecipientUsernameAndChannel(
                user.getUsername(), NotificationChannel.IN_APP, pageable);
        }
        return PageResponse.of(notifications, NotificationResponse::from);
    }

    /**
     * Admin-console roster (officer/admin only): every notification matching the optional
     * campus/status/channel/type filters, newest-first and paged. Unlike {@link #list(String,
     * Pageable)} this is a delivery-operations view — it spans all channels and delivery states so
     * an operator can find stuck email/SMS rows and inspect their attempt trail. Admins may filter
     * by any campus (or none, for all campuses); an officer's roster is pinned to their own campus.
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> adminSearch(String campusId, NotificationStatus status,
            NotificationChannel channel, NotificationType type, Pageable pageable) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        // Only admins range across campuses; an officer's roster is pinned to their own campus
        // regardless of the requested campus filter.
        String campusFilter = user.getRole() == UserRole.ADMIN
            ? (campusId == null || campusId.isBlank() ? null : campusId)
            : user.getCampusId();
        Page<Notification> page = notificationRepository.search(campusFilter, status, channel, type, pageable);
        return PageResponse.of(page, NotificationResponse::from);
    }

    /**
     * The delivery-attempt trail for one notification (officer/admin only, campus-scoped). Read-only
     * audit view backing the admin console's per-row drill-down.
     */
    @Transactional(readOnly = true)
    public List<DeliveryAttemptResponse> listAttempts(String notificationId) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Notification notification = loadVisible(notificationId, user);
        return deliveryAttemptRepository
            .findByNotificationIdOrderByAttemptNumberAsc(notification.getId()).stream()
            .map(DeliveryAttemptResponse::from)
            .toList();
    }

    /**
     * Re-queues a failed outbound (email/SMS) notification for the scheduled dispatcher to try
     * again (officer/admin only, campus-scoped). Only a FAILED email/SMS row is retryable — in-app
     * rows have no external delivery, and PENDING/SENT rows need no manual push — so anything else
     * is a {@link NotificationNotRetryableException} (409). The attempt counter is reset so the row
     * gets a fresh run of the dispatcher's retry budget; the historical attempt trail is preserved.
     */
    @Transactional
    public NotificationResponse retry(String notificationId) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Notification notification = loadVisible(notificationId, user);

        if (notification.getChannel() == NotificationChannel.IN_APP) {
            throw new NotificationNotRetryableException(
                "In-app notifications have no external delivery to retry");
        }
        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new NotificationNotRetryableException(
                "Only a FAILED notification can be retried; this one is " + notification.getStatus());
        }

        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttemptCount(0);
        notification.setLastAttemptAt(null);
        notificationRepository.save(notification);
        return NotificationResponse.from(notification);
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(String notificationId) {
        UserAccount user = campusAccessService.currentUser();
        return NotificationResponse.from(loadVisible(notificationId, user));
    }

    /**
     * Queues a notification. Only officers and admins may create notifications for a
     * recipient; the notification starts PENDING with no delivery attempts yet.
     */
    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);

        Notification notification = notificationRepository.save(Notification.builder()
            .id(UUID.randomUUID().toString())
            .campusId(user.getCampusId())
            .recipientUsername(request.recipientUsername())
            .channel(request.channel())
            .subject(request.subject())
            .body(request.body())
            .status(NotificationStatus.PENDING)
            .attemptCount(0)
            .createdAt(OffsetDateTime.now())
            .build());
        return NotificationResponse.from(notification);
    }

    /**
     * Creates a notification in response to a domain event, bypassing the officer-only guard on
     * {@link #create} because the caller is the system (an after-commit event listener), not a
     * signed-in user. Idempotent per (source event, recipient): a replayed or redelivered event
     * never produces a duplicate. Returns quietly without creating anything when the recipient could
     * not be resolved, so a missing recipient never breaks the originating business transaction.
     */
    @Transactional
    public void createFromEvent(String sourceEventId, String campusId, String recipientUsername,
                               NotificationType type, String referenceId, String subject, String body) {
        if (recipientUsername == null || recipientUsername.isBlank()) {
            return;
        }
        // Respect the recipient's opt-out choice for this type (applies to every channel).
        if (!preferenceService.isEnabled(recipientUsername, type)) {
            return;
        }
        // In-app is always delivered: the persisted row IS the notification, so it starts SENT.
        enqueue(sourceEventId, campusId, recipientUsername, type, referenceId, subject, body,
            NotificationChannel.IN_APP, NotificationStatus.SENT);

        // Fan out to enabled outbound channels as PENDING rows for the scheduled dispatcher to send.
        if (emailEnabled) {
            enqueue(sourceEventId, campusId, recipientUsername, type, referenceId, subject, body,
                NotificationChannel.EMAIL, NotificationStatus.PENDING);
        }
        if (smsEnabled && SMS_CRITICAL.contains(type)) {
            enqueue(sourceEventId, campusId, recipientUsername, type, referenceId, subject, body,
                NotificationChannel.SMS, NotificationStatus.PENDING);
        }
    }

    /**
     * Saves one notification row on a single channel, idempotent per (source event, recipient,
     * channel) so a replayed event never duplicates the in-app row, the email, or the SMS
     * independently.
     */
    private void enqueue(String sourceEventId, String campusId, String recipientUsername,
                         NotificationType type, String referenceId, String subject, String body,
                         NotificationChannel channel, NotificationStatus status) {
        if (sourceEventId != null && notificationRepository
            .existsBySourceEventIdAndRecipientUsernameAndChannel(sourceEventId, recipientUsername, channel)) {
            return;
        }
        notificationRepository.save(Notification.builder()
            .id(UUID.randomUUID().toString())
            .campusId(campusId)
            .recipientUsername(recipientUsername)
            .channel(channel)
            .type(type)
            .referenceId(referenceId)
            .sourceEventId(sourceEventId)
            .subject(subject)
            .body(body)
            .status(status)
            .attemptCount(0)
            .createdAt(OffsetDateTime.now())
            .build());
    }

    /** Count of the current user's own in-app notifications not yet read — powers the bell's unread badge. */
    @Transactional(readOnly = true)
    public long unreadCount() {
        UserAccount user = campusAccessService.currentUser();
        return notificationRepository.countByRecipientUsernameAndChannelAndStatusNot(
            user.getUsername(), NotificationChannel.IN_APP, NotificationStatus.READ);
    }

    /** Marks all of the current user's own unread in-app notifications as read in one pass. */
    @Transactional
    public int markAllRead() {
        UserAccount user = campusAccessService.currentUser();
        OffsetDateTime now = OffsetDateTime.now();
        List<Notification> unread = notificationRepository.findByRecipientUsernameAndChannelAndStatusNot(
            user.getUsername(), NotificationChannel.IN_APP, NotificationStatus.READ);
        for (Notification notification : unread) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    /**
     * Records a delivery attempt against a notification and updates its status. Each attempt
     * is persisted as an immutable audit record (SECURITY.md section 4). Officer/admin action.
     */
    @Transactional
    public NotificationResponse recordAttempt(String notificationId, boolean succeeded, String detail) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Notification notification = loadVisible(notificationId, user);

        OffsetDateTime now = OffsetDateTime.now();
        int attemptNumber = notification.getAttemptCount() + 1;
        NotificationStatus outcome = succeeded ? NotificationStatus.SENT : NotificationStatus.FAILED;

        notification.setAttemptCount(attemptNumber);
        notification.setLastAttemptAt(now);
        notification.setStatus(outcome);
        notificationRepository.save(notification);

        deliveryAttemptRepository.save(DeliveryAttempt.builder()
            .id(UUID.randomUUID().toString())
            .notificationId(notificationId)
            .attemptNumber(attemptNumber)
            .outcome(outcome)
            .detail(detail)
            .attemptedAt(now)
            .build());
        return NotificationResponse.from(notification);
    }

    /**
     * Records the outcome of one automated delivery attempt made by the scheduled dispatcher
     * (Notification-system Steps 6-7) — the system-level counterpart to {@link #recordAttempt},
     * with no signed-in user and no officer guard. On success the row becomes SENT; on failure it
     * stays PENDING for the next sweep to retry, until {@code maxAttempts} have failed, after which
     * it becomes FAILED. Every attempt is persisted as an immutable {@link DeliveryAttempt} record.
     */
    @Transactional
    public void markDelivery(String notificationId, boolean succeeded, String detail, int maxAttempts) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        OffsetDateTime now = OffsetDateTime.now();
        int attemptNumber = notification.getAttemptCount() + 1;
        NotificationStatus outcome;
        if (succeeded) {
            outcome = NotificationStatus.SENT;
        } else {
            outcome = attemptNumber >= maxAttempts ? NotificationStatus.FAILED : NotificationStatus.PENDING;
        }

        notification.setAttemptCount(attemptNumber);
        notification.setLastAttemptAt(now);
        notification.setStatus(outcome);
        notificationRepository.save(notification);

        // The attempt trail records the true per-attempt result (SENT/FAILED), even when the
        // notification itself stays PENDING for a later retry.
        deliveryAttemptRepository.save(DeliveryAttempt.builder()
            .id(UUID.randomUUID().toString())
            .notificationId(notificationId)
            .attemptNumber(attemptNumber)
            .outcome(succeeded ? NotificationStatus.SENT : NotificationStatus.FAILED)
            .detail(detail)
            .attemptedAt(now)
            .build());
    }

    /** A recipient marks their own notification as read. */
    @Transactional
    public NotificationResponse markRead(String notificationId) {
        UserAccount user = campusAccessService.currentUser();
        Notification notification = loadVisible(notificationId, user);
        if (!notification.getRecipientUsername().equals(user.getUsername())) {
            throw new AccessDeniedException("Only the recipient can mark a notification as read");
        }
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(OffsetDateTime.now());
        notificationRepository.save(notification);
        return NotificationResponse.from(notification);
    }

    /**
     * Loads a notification the current user may see: the addressed recipient always may,
     * and officers/admins may within an accessible campus.
     */
    private Notification loadVisible(String notificationId, UserAccount user) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (notification.getRecipientUsername().equals(user.getUsername())) {
            return notification;
        }
        campusAccessService.requireAccess(user, notification.getCampusId());
        return notification;
    }

    private void requireOfficer(UserAccount user) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage notifications");
        }
    }
}
