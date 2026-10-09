package com.nexploy.campuslink.notification.repository;

import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.entity.NotificationType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByCampusId(String campusId);

    /** Paged campus-scoped listing for staff (officer/admin) notification roster. */
    Page<Notification> findByCampusId(String campusId, Pageable pageable);

    /** Every row addressed to a recipient, across all channels (event wiring/audit). */
    List<Notification> findByRecipientUsername(String recipientUsername);

    /**
     * The recipient's own feed on one channel. The user-facing feed passes {@code IN_APP} so the
     * email/SMS rows created by multi-channel fan-out stay delivery mechanics and never surface
     * in the app.
     */
    List<Notification> findByRecipientUsernameAndChannel(
        String recipientUsername, NotificationChannel channel);

    /** Paged variant of {@link #findByRecipientUsernameAndChannel(String, NotificationChannel)}. */
    Page<Notification> findByRecipientUsernameAndChannel(
        String recipientUsername, NotificationChannel channel, Pageable pageable);

    /** Count of a recipient's feed rows not yet marked read — powers the bell's unread badge. */
    long countByRecipientUsernameAndChannelAndStatusNot(String recipientUsername,
        NotificationChannel channel,
        com.nexploy.campuslink.notification.entity.NotificationStatus status);

    /**
     * Idempotency guard for event-driven creation: a given source event produces at most one
     * notification per recipient, so a replayed or redelivered event never duplicates.
     */
    boolean existsBySourceEventIdAndRecipientUsername(String sourceEventId, String recipientUsername);

    /**
     * Per-channel idempotency guard for multi-channel fan-out: a given source event produces at
     * most one notification per recipient <em>per channel</em>, so a replay never duplicates the
     * in-app row, the email, or the SMS independently.
     */
    boolean existsBySourceEventIdAndRecipientUsernameAndChannel(
        String sourceEventId, String recipientUsername, NotificationChannel channel);

    /**
     * The oldest still-unsent rows on the given channels that are due for a (re)send — the work
     * queue drained by the scheduled email/SMS dispatcher. A row is due when it has never been
     * attempted ({@code lastAttemptAt} null) or its last attempt is older than the retry-backoff
     * cutoff, so a transient failure is retried on a spaced schedule rather than every sweep.
     * Caller passes a {@link Pageable} (size cap) so one sweep can't load an unbounded backlog.
     */
    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.channel IN :channels "
        + "AND (n.lastAttemptAt IS NULL OR n.lastAttemptAt < :cutoff) ORDER BY n.createdAt ASC")
    List<Notification> findDispatchable(
        @Param("status") NotificationStatus status,
        @Param("channels") Collection<NotificationChannel> channels,
        @Param("cutoff") OffsetDateTime cutoff,
        Pageable pageable);

    /**
     * A recipient's still-unread rows on one channel — marks the in-app feed read in one pass
     * without touching the email/SMS delivery rows.
     */
    List<Notification> findByRecipientUsernameAndChannelAndStatusNot(String recipientUsername,
        NotificationChannel channel,
        com.nexploy.campuslink.notification.entity.NotificationStatus status);

    /**
     * Filtered roster for the admin console (officer/admin). Every filter is optional: a null
     * argument drops that predicate, so callers can slice by any combination of campus, status,
     * channel, and type. Ordered newest-first and paged.
     */
    @Query("SELECT n FROM Notification n WHERE "
        + "(:campusId IS NULL OR n.campusId = :campusId) AND "
        + "(:status IS NULL OR n.status = :status) AND "
        + "(:channel IS NULL OR n.channel = :channel) AND "
        + "(:type IS NULL OR n.type = :type) "
        + "ORDER BY n.createdAt DESC")
    Page<Notification> search(
        @Param("campusId") String campusId,
        @Param("status") NotificationStatus status,
        @Param("channel") NotificationChannel channel,
        @Param("type") NotificationType type,
        Pageable pageable);
}
