// ============================================================
// NOTIFICATIONS API — live only.
// GET /api/notifications (server-scoped — a recipient sees their own,
// officers/admins see their campus), GET /api/notifications/unread-count
// (own feed), POST /api/notifications/{id}/read, POST /api/notifications/read-all.
// ============================================================

import { apiRequest } from "@/lib/api/client";
import {
  mapAdminNotification,
  mapDeliveryAttempt,
  mapNotification,
  type DeliveryAttemptResponseDto,
  type NotificationResponseDto,
} from "@/lib/api/mappers";
import type {
  AdminNotification,
  DeliveryAttempt,
  NotificationChannel,
  NotificationItem,
  NotificationPreference,
  NotificationStatus,
  NotificationType,
  PageResult,
} from "@/types";

/** GET /api/notifications — newest first. */
export async function listNotifications(): Promise<NotificationItem[]> {
  const dtos = await apiRequest<NotificationResponseDto[]>("/notifications");
  return sortNewestFirst(dtos.map(mapNotification));
}

/** POST /api/notifications/{id}/read — only the recipient may mark their own read. */
export async function markNotificationRead(id: string): Promise<NotificationItem> {
  return mapNotification(
    await apiRequest<NotificationResponseDto>(`/notifications/${id}/read`, { method: "POST" }),
  );
}

/**
 * GET /api/notifications/unread-count — unread count for the signed-in user's own feed only.
 * Cheap enough to poll for the topbar badge without pulling the whole list.
 */
export async function getUnreadCount(): Promise<number> {
  const res = await apiRequest<{ count: number }>("/notifications/unread-count");
  return res.count ?? 0;
}

/**
 * POST /api/notifications/read-all — marks every unread notification in the signed-in user's own
 * feed as read in one call, returning how many rows changed. Replaces the old per-item loop.
 */
export async function markAllNotificationsRead(): Promise<number> {
  const res = await apiRequest<{ updated: number }>("/notifications/read-all", { method: "POST" });
  return res.updated ?? 0;
}

/** Server returns ISO createdAt; sort newest first for display. */
function sortNewestFirst(items: NotificationItem[]): NotificationItem[] {
  return [...items].sort((a, b) => (a.timestamp < b.timestamp ? 1 : -1));
}

/**
 * GET /api/notifications/preferences — the signed-in user's own delivery preferences, one entry
 * per notification type (absent overrides resolve to enabled).
 */
export async function getNotificationPreferences(): Promise<NotificationPreference[]> {
  return apiRequest<NotificationPreference[]>("/notifications/preferences");
}

/**
 * PUT /api/notifications/preferences — replaces the signed-in user's choices for the listed types
 * and returns the full resolved set. Self-scoped; the server keys rows by the caller's username.
 */
export async function updateNotificationPreferences(
  preferences: NotificationPreference[],
): Promise<NotificationPreference[]> {
  return apiRequest<NotificationPreference[]>("/notifications/preferences", {
    method: "PUT",
    body: { preferences },
  });
}

/**
 * Formats a notification timestamp for display. Live values are ISO-8601 (createdAt) → rendered as
 * local date/time; a non-date string passes through unchanged. Purely presentational.
 */
export function formatNotificationTimestamp(timestamp: string): string {
  const parsed = Date.parse(timestamp);
  if (Number.isNaN(parsed)) return timestamp;
  return new Date(parsed).toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// ============================================================
// ADMIN CONSOLE (officer/admin only) — delivery operations.
// GET /api/notifications/admin (filtered, paged roster spanning all channels/states),
// GET /api/notifications/{id}/attempts (delivery-attempt trail),
// POST /api/notifications/{id}/retry (re-queue a FAILED outbound row).
// ============================================================

/** Optional filters for the admin roster; a blank/undefined value drops that predicate server-side. */
export interface AdminNotificationFilters {
  campusId?: string;
  status?: NotificationStatus;
  channel?: NotificationChannel;
  type?: NotificationType;
}

/**
 * GET /api/notifications/admin — the officer/admin delivery-operations roster: every notification
 * matching the optional filters, newest-first and paged. Returns the stable {@link PageResult}
 * envelope with rows mapped to {@link AdminNotification}.
 */
export async function adminSearchNotifications(
  filters: AdminNotificationFilters,
  page: number,
  size: number,
): Promise<PageResult<AdminNotification>> {
  const res = await apiRequest<PageResult<NotificationResponseDto>>("/notifications/admin", {
    params: {
      campusId: filters.campusId,
      status: filters.status,
      channel: filters.channel,
      type: filters.type,
      page,
      size,
    },
  });
  return { ...res, content: res.content.map(mapAdminNotification) };
}

/** GET /api/notifications/{id}/attempts — the delivery-attempt trail for one notification. */
export async function getDeliveryAttempts(id: string): Promise<DeliveryAttempt[]> {
  const dtos = await apiRequest<DeliveryAttemptResponseDto[]>(`/notifications/${id}/attempts`);
  return dtos.map(mapDeliveryAttempt);
}

/**
 * POST /api/notifications/{id}/retry — re-queues a FAILED outbound (email/SMS) notification for the
 * scheduled dispatcher. The server rejects in-app or non-FAILED rows with 409.
 */
export async function retryNotification(id: string): Promise<AdminNotification> {
  return mapAdminNotification(
    await apiRequest<NotificationResponseDto>(`/notifications/${id}/retry`, { method: "POST" }),
  );
}
