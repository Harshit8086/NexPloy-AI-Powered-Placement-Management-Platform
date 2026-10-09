package com.nexploy.campuslink.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.notification.channel.NotificationSender;
import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test for {@link NotificationDispatchService}. It verifies the sweep hands each PENDING row to
 * the matching sender and records the outcome via {@link NotificationService#markDelivery} — SENT on
 * a clean send, FAILED-path on a throwing send — and that with no senders the sweep is a pure no-op
 * (no queue read, no delivery calls), which is the always-on state in the test profile.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;

    private static Notification emailRow(String id) {
        return Notification.builder()
            .id(id)
            .campusId("campus_a")
            .recipientUsername("student@uni.edu")
            .channel(NotificationChannel.EMAIL)
            .status(NotificationStatus.PENDING)
            .subject("Subject")
            .body("Body")
            .attemptCount(0)
            .build();
    }

    /** Builds the service and injects the @Value-backed tuning fields (unset by a bare constructor). */
    private NotificationDispatchService configured(NotificationSender... senders) {
        NotificationDispatchService service = new NotificationDispatchService(
            notificationRepository, notificationService, List.of(senders));
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "retryBackoffSeconds", 300L);
        ReflectionTestUtils.setField(service, "batchSize", 100);
        return service;
    }

    @Test
    void noSendersMakesSweepANoOp() {
        NotificationDispatchService service =
            new NotificationDispatchService(notificationRepository, notificationService, List.of());

        service.dispatchPending();

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void successfulSendIsRecordedAsDelivered() {
        NotificationSender emailSender = new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.EMAIL;
            }

            @Override
            public void send(String recipientUsername, String subject, String body) {
                // succeed silently
            }
        };
        NotificationDispatchService service = configured(emailSender);
        when(notificationRepository.findDispatchable(
                eq(NotificationStatus.PENDING), any(), any(), any()))
            .thenReturn(List.of(emailRow("n1")));

        service.dispatchPending();

        verify(notificationService).markDelivery(eq("n1"), eq(true), any(), anyInt());
        verify(notificationService, never()).markDelivery(any(), eq(false), any(), anyInt());
    }

    @Test
    void failedSendIsRecordedAsFailure() {
        NotificationSender emailSender = new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.EMAIL;
            }

            @Override
            public void send(String recipientUsername, String subject, String body) {
                throw new RuntimeException("smtp down");
            }
        };
        NotificationDispatchService service = configured(emailSender);
        when(notificationRepository.findDispatchable(
                eq(NotificationStatus.PENDING), any(), any(), any()))
            .thenReturn(List.of(emailRow("n1")));

        service.dispatchPending();

        verify(notificationService).markDelivery(eq("n1"), eq(false), eq("smtp down"), anyInt());
        verify(notificationService, never()).markDelivery(any(), eq(true), any(), anyInt());
    }

    @Test
    void sweepQueriesWithRetryBackoffCutoffAndBatchCap() {
        NotificationSender emailSender = new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return NotificationChannel.EMAIL;
            }

            @Override
            public void send(String recipientUsername, String subject, String body) {
                // succeed silently
            }
        };
        NotificationDispatchService service = configured(emailSender);
        OffsetDateTime before = OffsetDateTime.now();
        when(notificationRepository.findDispatchable(
                eq(NotificationStatus.PENDING), any(), any(), any()))
            .thenReturn(List.of());

        service.dispatchPending();

        ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findDispatchable(
            eq(NotificationStatus.PENDING), any(), cutoff.capture(), pageable.capture());
        // cutoff is ~300s in the past (retry-backoff-seconds), so recently-attempted rows are skipped.
        assertThat(cutoff.getValue()).isBefore(before.minusSeconds(299));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
