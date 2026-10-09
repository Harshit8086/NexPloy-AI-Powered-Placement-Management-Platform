package com.nexploy.campuslink.notification.controller;

import com.nexploy.campuslink.notification.dto.CreateNotificationRequest;
import com.nexploy.campuslink.notification.dto.DeliveryAttemptResponse;
import com.nexploy.campuslink.notification.dto.NotificationPreferenceResponse;
import com.nexploy.campuslink.notification.dto.NotificationResponse;
import com.nexploy.campuslink.notification.dto.UpdateNotificationPreferencesRequest;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.service.NotificationPreferenceService;
import com.nexploy.campuslink.notification.service.NotificationService;
import com.nexploy.campuslink.shared.dto.PageRequests;
import com.nexploy.campuslink.shared.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<?> listNotifications(
        @RequestParam(required = false) String campusId,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        if (PageRequests.requested(page, size)) {
            return ResponseEntity.ok(notificationService.list(campusId, PageRequests.of(page, size, sort)));
        }
        return ResponseEntity.ok(notificationService.list(campusId));
    }

    @GetMapping("/{notificationId}")
    public NotificationResponse getNotification(@PathVariable String notificationId) {
        return notificationService.get(notificationId);
    }

    /**
     * Admin-console roster (officer/admin only): every notification matching the optional
     * campus/status/channel/type filters, newest-first and paged. Spans all channels and delivery
     * states so an operator can find stuck email/SMS rows.
     */
    @GetMapping("/admin")
    public PageResponse<NotificationResponse> adminSearch(
        @RequestParam(required = false) String campusId,
        @RequestParam(required = false) NotificationStatus status,
        @RequestParam(required = false) NotificationChannel channel,
        @RequestParam(required = false) NotificationType type,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        return notificationService.adminSearch(campusId, status, channel, type,
            PageRequests.of(page, size, sort));
    }

    /** The delivery-attempt trail for one notification (officer/admin only, campus-scoped). */
    @GetMapping("/{notificationId}/attempts")
    public List<DeliveryAttemptResponse> listAttempts(@PathVariable String notificationId) {
        return notificationService.listAttempts(notificationId);
    }

    /** Re-queues a FAILED outbound (email/SMS) notification for the dispatcher (officer/admin only). */
    @PostMapping("/{notificationId}/retry")
    public NotificationResponse retry(@PathVariable String notificationId) {
        return notificationService.retry(notificationId);
    }

    /** Unread count for the signed-in user's own feed — powers the bell badge without fetching the list. */
    @GetMapping("/unread-count")
    public java.util.Map<String, Long> unreadCount() {
        return java.util.Map.of("count", notificationService.unreadCount());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse createNotification(@Valid @RequestBody CreateNotificationRequest request) {
        return notificationService.create(request);
    }

    @PostMapping("/{notificationId}/attempts")
    public NotificationResponse recordAttempt(
        @PathVariable String notificationId,
        @RequestParam boolean succeeded,
        @RequestParam(required = false) String detail
    ) {
        return notificationService.recordAttempt(notificationId, succeeded, detail);
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable String notificationId) {
        return notificationService.markRead(notificationId);
    }

    /** Marks every unread notification in the signed-in user's own feed as read. */
    @PostMapping("/read-all")
    public java.util.Map<String, Integer> markAllRead() {
        return java.util.Map.of("updated", notificationService.markAllRead());
    }

    /** The signed-in user's own delivery preferences — one entry per notification type. */
    @GetMapping("/preferences")
    public List<NotificationPreferenceResponse> getPreferences() {
        return preferenceService.list();
    }

    /** Replaces the signed-in user's preferences for the listed types; returns the full resolved set. */
    @PutMapping("/preferences")
    public List<NotificationPreferenceResponse> updatePreferences(
        @Valid @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        return preferenceService.replace(request);
    }
}
