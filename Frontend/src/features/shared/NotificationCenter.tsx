"use client";

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { BellOff, CheckCheck } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/layout/PageHeader";
import { notificationIcon } from "@/components/layout/NotificationsPanel";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { NotificationPreferences } from "@/features/shared/NotificationPreferences";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { listNotifications, formatNotificationTimestamp } from "@/lib/api/notifications";
import { cn } from "@/lib/utils";
import type { NotificationChannel, Role } from "@/types";

/** Real delivery channels, used as the (authoritative) filter facets. */
const CHANNELS: { channel: NotificationChannel; label: string }[] = [
  { channel: "IN_APP", label: "In-app" },
  { channel: "EMAIL", label: "Email" },
  { channel: "SMS", label: "SMS" },
];

/** Shared Notification Center. Visibility is scoped server-side (a student sees their own,
 * officers/admins their campus), so the component no longer filters by a client-side
 * audience — it renders exactly what the API returns. Channel facets and the per-item
 * channel badge only matter for admins, whose feed spans every channel; every other role's
 * feed is in-app only, so showing "In-app" on each row would be noise. */
export function NotificationCenter({ role, eyebrow }: { role: Role; eyebrow: string }) {
  const notifications = useAppStore((s) => s.notifications);
  const setNotifications = useAppStore((s) => s.setNotifications);
  const markRead = useAppStore((s) => s.markRead);
  const markAllRead = useAppStore((s) => s.markAllRead);
  const [filter, setFilter] = useState<NotificationChannel | "all">("all");

  const { data, loading, error, reload } = useApiResource(() => listNotifications(), [], []);

  // Sync the fetched list into the shared store so the topbar badge and read-state stay
  // consistent with what this page shows.
  useEffect(() => {
    setNotifications(data);
  }, [data, setNotifications]);

  // Only admins get a multi-channel feed (server-side); everyone else is in-app only.
  const showChannelUi = role === "admin";

  const visible =
    !showChannelUi || filter === "all"
      ? notifications
      : notifications.filter((n) => n.channel === filter);
  const unread = notifications.filter((n) => !n.read).length;

  const availableChannels = CHANNELS.filter((c) => notifications.some((n) => n.channel === c.channel));

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        eyebrow={eyebrow}
        title="Notification Center"
        subtitle={
          unread > 0
            ? `${unread} unread notification${unread === 1 ? "" : "s"}.`
            : "You're all caught up."
        }
        actions={
          <Button variant="outline" size="sm" onClick={markAllRead} disabled={unread === 0}>
            <CheckCheck className="h-3.5 w-3.5" /> Mark all read
          </Button>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={notifications.length === 0}
        onRetry={reload}
        emptyTitle="No notifications"
        emptyMessage="You have no notifications yet."
      >
        {showChannelUi && (
          <div className="mb-4 flex flex-wrap gap-1.5">
            <button
              onClick={() => setFilter("all")}
              className={cn(
                "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                filter === "all"
                  ? "border-violet/50 bg-violet/10 text-violet-bright"
                  : "border-border text-muted-foreground hover:bg-accent",
              )}
            >
              All ({notifications.length})
            </button>
            {availableChannels.map((c) => {
              const count = notifications.filter((n) => n.channel === c.channel).length;
              return (
                <button
                  key={c.channel}
                  onClick={() => setFilter(c.channel)}
                  className={cn(
                    "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                    filter === c.channel
                      ? "border-violet/50 bg-violet/10 text-violet-bright"
                      : "border-border text-muted-foreground hover:bg-accent",
                  )}
                >
                  {c.label} ({count})
                </button>
              );
            })}
          </div>
        )}

        {visible.length === 0 ? (
          <Card className="flex flex-col items-center p-12 text-center">
            <BellOff className="mb-3 h-8 w-8 text-muted-foreground" />
            <p className="text-sm font-medium">Nothing here</p>
            <p className="mt-1 text-xs text-muted-foreground">
              {showChannelUi ? "Try a different channel or clear the filter." : "You have no notifications yet."}
            </p>
          </Card>
        ) : (
          <Card className="divide-y divide-border p-0">
            <AnimatePresence initial={false}>
              {visible.map((n) => {
                const Icon = notificationIcon[n.channel];
                const label = CHANNELS.find((c) => c.channel === n.channel)?.label ?? n.channel;
                return (
                  <motion.button
                    key={n.id}
                    layout
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.2 }}
                    onClick={() => markRead(n.id)}
                    className={cn(
                      "flex w-full gap-3 p-4 text-left transition-colors hover:bg-accent/40",
                      !n.read && "bg-violet/5",
                    )}
                  >
                    <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted">
                      <Icon className="h-4 w-4 text-violet-bright" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-sm font-medium">{n.title}</p>
                        {showChannelUi && <Badge variant="muted">{label}</Badge>}
                      </div>
                      <p className="mt-0.5 text-xs text-muted-foreground">{n.message}</p>
                      <p className="mt-1 text-[10px] text-muted-foreground/60">
                        {formatNotificationTimestamp(n.timestamp)}
                      </p>
                    </div>
                    {!n.read && (
                      <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-violet-bright" />
                    )}
                  </motion.button>
                );
              })}
            </AnimatePresence>
          </Card>
        )}
      </AsyncBoundary>

      <div className="mt-6">
        <NotificationPreferences />
      </div>
    </div>
  );
}
