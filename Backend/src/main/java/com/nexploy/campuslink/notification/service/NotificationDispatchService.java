package com.nexploy.campuslink.notification.service;

import com.nexploy.campuslink.notification.channel.NotificationSender;
import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Drains the outbound email/SMS queue (Notification-system Steps 6-7). Event-driven creation enqueues
 * EMAIL/SMS notifications as {@code PENDING}; this scheduled sweep hands each to the matching
 * {@link NotificationSender}, then records the outcome via
 * {@link NotificationService#markDelivery}. Doing the network I/O here (outside the originating
 * business transaction) keeps request latency flat and means a provider outage never rolls back the
 * action that triggered the notification.
 *
 * <p>Senders are injected as the set of <em>enabled</em> channels only (each is
 * {@code @ConditionalOnProperty}). When none is enabled — the default, and always in tests — the
 * sender map is empty and every sweep is an immediate no-op, so nothing is sent and no queue is read.
 *
 * <p>Hardening (Step 10): each sweep is bounded to {@link #batchSize} rows (a rate guard against a
 * large backlog hammering a paid provider), and a failed row is not retried until
 * {@code retry-backoff-seconds} have passed since its last attempt (spaced retry, not every-minute
 * hammering). A failed send leaves the row {@code PENDING} (attempt count incremented) so a later
 * sweep retries, until {@link #maxAttempts} attempts have failed, after which it becomes
 * {@code FAILED} and is left for inspection via its delivery-attempt trail. Every sweep that does
 * any work logs a one-line summary (sent/failed counts) for operability.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final Map<NotificationChannel, NotificationSender> senders;

    /** Give up (mark FAILED) after this many failed attempts on a single notification. */
    @Value("${nexploy.notifications.max-attempts:3}")
    private int maxAttempts;

    /** Do not retry a failed row until this many seconds have elapsed since its last attempt. */
    @Value("${nexploy.notifications.retry-backoff-seconds:300}")
    private long retryBackoffSeconds;

    /** Upper bound on rows processed per sweep — a rate guard against a large backlog. */
    @Value("${nexploy.notifications.dispatch-batch-size:100}")
    private int batchSize;

    public NotificationDispatchService(NotificationRepository notificationRepository,
                                       NotificationService notificationService,
                                       List<NotificationSender> senders) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.senders = senders.stream()
            .collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
    }

    @Scheduled(cron = "${nexploy.notifications.dispatch-cron:0 * * * * *}")
    public void dispatchPending() {
        if (senders.isEmpty()) {
            return; // no outbound channel enabled — nothing to do
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(retryBackoffSeconds);
        List<Notification> pending = notificationRepository.findDispatchable(
            NotificationStatus.PENDING, senders.keySet(), cutoff, PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }

        int sent = 0;
        int failed = 0;
        for (Notification notification : pending) {
            NotificationSender sender = senders.get(notification.getChannel());
            if (sender == null) {
                continue; // channel not enabled this run
            }
            try {
                sender.send(notification.getRecipientUsername(),
                    notification.getSubject(), notification.getBody());
                notificationService.markDelivery(notification.getId(), true,
                    notification.getChannel() + " delivered", maxAttempts);
                sent++;
            } catch (Exception e) {
                log.warn("Delivery failed for notification {} on {}: {}",
                    notification.getId(), notification.getChannel(), e.getMessage());
                notificationService.markDelivery(notification.getId(), false,
                    describe(e), maxAttempts);
                failed++;
            }
        }
        log.info("Notification dispatch sweep: {} sent, {} failed (of {} due)",
            sent, failed, pending.size());
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
