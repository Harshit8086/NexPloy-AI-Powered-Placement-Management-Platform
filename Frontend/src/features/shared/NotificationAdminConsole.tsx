"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, RefreshCw, RotateCw } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  adminSearchNotifications,
  getDeliveryAttempts,
  retryNotification,
  formatNotificationTimestamp,
  type AdminNotificationFilters,
} from "@/lib/api/notifications";
import { ApiError } from "@/lib/api/client";
import { cn } from "@/lib/utils";
import type {
  AdminNotification,
  NotificationChannel,
  NotificationStatus,
  NotificationType,
  PageResult,
} from "@/types";

const PAGE_SIZE = 20;

const STATUSES: NotificationStatus[] = ["PENDING", "SENT", "FAILED", "READ"];
const CHANNELS: NotificationChannel[] = ["IN_APP", "EMAIL", "SMS"];
const TYPES: NotificationType[] = [
  "OFFER_EXTENDED", "OFFER_ACCEPTED", "OFFER_DECLINED", "OFFER_REVOKED",
  "APPLICATION_ADVANCED", "SHORTLISTED", "SHORTLIST_REJECTED",
  "INTERVIEW_SCHEDULED", "INTERVIEW_RESCHEDULED", "INTERVIEW_CANCELLED",
  "INTERVIEW_REMINDER", "OFFER_DEADLINE", "DOCUMENT_APPROVED", "DOCUMENT_REJECTED",
  "DRIVE_PUBLISHED", "RISK_DETECTED", "INTERVENTION_ASSIGNED",
];

const EMPTY_PAGE: PageResult<AdminNotification> = {
  content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0,
};

/** Status → Badge variant; mirrors the delivery lifecycle so FAILED rows stand out. */
function statusVariant(status: NotificationStatus): "success" | "risk" | "warning" | "muted" {
  switch (status) {
    case "SENT": return "success";
    case "FAILED": return "risk";
    case "PENDING": return "warning";
    default: return "muted";
  }
}

/** A FAILED outbound (email/SMS) row is the only retryable shape — mirrors the server guard. */
function isRetryable(n: AdminNotification): boolean {
  return n.status === "FAILED" && n.channel !== "IN_APP";
}

/** A small labelled select used for each roster filter. */
function FilterSelect<T extends string>(props: {
  label: string;
  value: T | "all";
  options: readonly T[];
  onChange: (value: T | "all") => void;
}) {
  return (
    <label className="flex flex-col gap-1 text-xs text-muted-foreground">
      <span className="font-medium uppercase tracking-wide">{props.label}</span>
      <select
        value={props.value}
        onChange={(e) => props.onChange(e.target.value as T | "all")}
        className="h-8 rounded-md border border-border bg-background px-2 text-xs text-foreground"
      >
        <option value="all">All</option>
        {props.options.map((opt) => (
          <option key={opt} value={opt}>{opt}</option>
        ))}
      </select>
    </label>
  );
}

/** Delivery-attempt trail for one notification, fetched lazily when the row is expanded. */
function AttemptTrail({ notificationId }: { notificationId: string }) {
  const { data, loading, error, reload } = useApiResource(
    () => getDeliveryAttempts(notificationId),
    [notificationId],
    [],
  );
  return (
    <div className="border-t border-border bg-muted/30 px-4 py-3">
      <AsyncBoundary
        loading={loading}
        error={error}
        empty={data.length === 0}
        onRetry={reload}
        emptyTitle="No delivery attempts"
        emptyMessage="This notification has not been attempted yet."
      >
        <ol className="space-y-2">
          {data.map((attempt) => (
            <li key={attempt.id} className="flex flex-wrap items-center gap-2 text-xs">
              <span className="font-mono text-muted-foreground">#{attempt.attemptNumber}</span>
              <Badge variant={statusVariant(attempt.outcome)}>{attempt.outcome}</Badge>
              <span className="text-muted-foreground/70">
                {formatNotificationTimestamp(attempt.attemptedAt)}
              </span>
              {attempt.detail && <span className="text-muted-foreground">— {attempt.detail}</span>}
            </li>
          ))}
        </ol>
      </AsyncBoundary>
    </div>
  );
}

/**
 * Officer/admin delivery-operations console. Lists the full notification roster (all channels and
 * delivery states) with status/channel/type filters, a per-row delivery-attempt drill-down, and a
 * Retry action that re-queues FAILED outbound (email/SMS) rows for the scheduled dispatcher.
 * Visibility and every action are enforced server-side (officer/admin only, campus-scoped).
 */
export function NotificationAdminConsole() {
  const pushToast = useAppStore((s) => s.pushToast);
  const [status, setStatus] = useState<NotificationStatus | "all">("all");
  const [channel, setChannel] = useState<NotificationChannel | "all">("all");
  const [type, setType] = useState<NotificationType | "all">("all");
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<string | null>(null);

  const filters: AdminNotificationFilters = {
    status: status === "all" ? undefined : status,
    channel: channel === "all" ? undefined : channel,
    type: type === "all" ? undefined : type,
  };

  const { data, loading, error, reload } = useApiResource(
    () => adminSearchNotifications(filters, page, PAGE_SIZE),
    [status, channel, type, page],
    EMPTY_PAGE,
  );

  // A filter change resets to the first page so results are never read against a stale offset.
  function changeFilter<T>(setter: (v: T) => void, value: T) {
    setter(value);
    setPage(0);
  }

  async function onRetry(id: string) {
    setRetryingId(id);
    try {
      await retryNotification(id);
      pushToast("Re-queued", "The notification was re-queued for delivery.");
      reload();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Could not retry this notification.";
      pushToast("Retry failed", message);
    } finally {
      setRetryingId(null);
    }
  }

  const rows = data.content;

  return (
    <div className="mx-auto mt-10 max-w-5xl">
      <PageHeader
        eyebrow="Delivery operations"
        title="Notification Admin Console"
        subtitle="Every notification across channels and delivery states. Retry stuck email/SMS sends."
        actions={
          <Button variant="outline" size="sm" onClick={reload}>
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-3">
        <FilterSelect label="Status" value={status} options={STATUSES}
          onChange={(v) => changeFilter(setStatus, v)} />
        <FilterSelect label="Channel" value={channel} options={CHANNELS}
          onChange={(v) => changeFilter(setChannel, v)} />
        <FilterSelect label="Type" value={type} options={TYPES}
          onChange={(v) => changeFilter(setType, v)} />
      </div>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={rows.length === 0}
        onRetry={reload}
        emptyTitle="No notifications"
        emptyMessage="No notifications match these filters."
      >
        <Card className="divide-y divide-border p-0">
          {rows.map((n) => {
            const expanded = expandedId === n.id;
            return (
              <div key={n.id}>
                <div className="flex items-start gap-3 p-4">
                  <button
                    onClick={() => setExpandedId(expanded ? null : n.id)}
                    className="mt-0.5 text-muted-foreground hover:text-foreground"
                    aria-label={expanded ? "Collapse attempts" : "Show attempts"}
                  >
                    {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                  </button>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-medium">{n.subject}</p>
                      <Badge variant={statusVariant(n.status)}>{n.status}</Badge>
                      <Badge variant="outline">{n.channel}</Badge>
                      {n.type && <Badge variant="muted">{n.type}</Badge>}
                    </div>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">{n.body}</p>
                    <p className="mt-1 text-[10px] text-muted-foreground/60">
                      To {n.recipientUsername} · {formatNotificationTimestamp(n.createdAt)}
                      {n.attemptCount > 0 && ` · ${n.attemptCount} attempt${n.attemptCount === 1 ? "" : "s"}`}
                    </p>
                  </div>
                  {isRetryable(n) && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onRetry(n.id)}
                      disabled={retryingId === n.id}
                    >
                      <RotateCw className={cn("h-3.5 w-3.5", retryingId === n.id && "animate-spin")} />
                      Retry
                    </Button>
                  )}
                </div>
                {expanded && <AttemptTrail notificationId={n.id} />}
              </div>
            );
          })}
        </Card>

        <div className="mt-4 flex items-center justify-between text-xs text-muted-foreground">
          <span>
            Page {data.page + 1} of {Math.max(data.totalPages, 1)} · {data.totalElements} total
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(p - 1, 0))}
              disabled={data.page <= 0}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => p + 1)}
              disabled={data.page + 1 >= data.totalPages}
            >
              Next
            </Button>
          </div>
        </div>
      </AsyncBoundary>
    </div>
  );
}

