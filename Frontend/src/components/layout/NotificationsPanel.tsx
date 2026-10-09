"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Bell, CheckCheck, Mail, MessageSquare } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { useAppStore } from "@/hooks/useAppStore";
import { useRole } from "@/hooks/useRole";
import { formatNotificationTimestamp } from "@/lib/api/notifications";
import { cn } from "@/lib/utils";
import type { NotificationChannel } from "@/types";

/** Icon per real delivery channel (the backend does not classify by topic). */
export const notificationIcon: Record<NotificationChannel, typeof Bell> = {
  EMAIL: Mail,
  SMS: MessageSquare,
  IN_APP: Bell,
};

/** Where each role's full Notification Center lives (every role now has one). */
const centerHref: Record<string, string> = {
  student: "/student/notifications",
  recruiter: "/recruiter/notifications",
  officer: "/officer/notifications",
  mentor: "/mentor/notifications",
  admin: "/admin/notifications",
};

/** How often the badge count is refreshed from the server (near-real-time in-app). */
const POLL_INTERVAL_MS = 30_000;

export function NotificationsPanel() {
  const { role } = useRole();
  const unreadCount = useAppStore((s) => s.unreadCount);
  const notifications = useAppStore((s) => s.notifications);
  const loadNotifications = useAppStore((s) => s.loadNotifications);
  const refreshUnreadCount = useAppStore((s) => s.refreshUnreadCount);
  const markAllRead = useAppStore((s) => s.markAllRead);
  const markRead = useAppStore((s) => s.markRead);

  // Near-real-time in-app: EventSource can't carry the JWT bearer header, so instead of SSE we
  // poll the cheap own-feed unread-count endpoint for the badge. Also refresh on tab focus so a
  // returning user sees a fresh count immediately. The full list is (re)loaded lazily when the
  // popover opens. Errors are swallowed — the Notification Center surfaces them with retry.
  useEffect(() => {
    void loadNotifications().catch(() => {});
    void refreshUnreadCount();
    const timer = window.setInterval(() => void refreshUnreadCount(), POLL_INTERVAL_MS);
    const onFocus = () => void refreshUnreadCount();
    window.addEventListener("focus", onFocus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", onFocus);
    };
  }, [loadNotifications, refreshUnreadCount]);

  const href = centerHref[role];

  return (
    <Popover
      onOpenChange={(open) => {
        // Pull the freshest list + count the moment the panel is opened.
        if (open) {
          void loadNotifications().catch(() => {});
          void refreshUnreadCount();
        }
      }}
    >
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          aria-label={`Notifications${unreadCount > 0 ? `, ${unreadCount} unread` : ""}`}
        >
          <Bell className="h-4 w-4" />
          {unreadCount > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-violet text-[9px] font-bold text-white">
              {unreadCount}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[calc(100vw-1rem)] p-0 sm:w-80">
        <div className="flex items-center justify-between px-4 py-3">
          <span className="text-sm font-semibold">Notifications</span>
          <button
            onClick={markAllRead}
            className="flex items-center gap-1 text-xs text-violet-bright hover:underline"
          >
            <CheckCheck className="h-3 w-3" /> Mark all read
          </button>
        </div>
        <Separator />
        <ScrollArea className="h-80">
          <div className="divide-y divide-border">
            {notifications.length === 0 && (
              <p className="px-4 py-10 text-center text-xs text-muted-foreground">
                No notifications yet.
              </p>
            )}
            {notifications.map((n) => {
              const Icon = notificationIcon[n.channel];
              return (
                <button
                  key={n.id}
                  onClick={() => markRead(n.id)}
                  className={cn(
                    "flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-accent/50",
                    !n.read && "bg-violet/5",
                  )}
                >
                  <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-muted">
                    <Icon className="h-3.5 w-3.5 text-violet-bright" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-medium text-foreground">{n.title}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">{n.message}</p>
                    <p className="mt-1 text-[10px] text-muted-foreground/60">
                      {formatNotificationTimestamp(n.timestamp)}
                    </p>
                  </div>
                  {!n.read && <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-violet-bright" />}
                </button>
              );
            })}
          </div>
        </ScrollArea>
        {href && (
          <>
            <Separator />
            <Link
              href={href}
              className="block px-4 py-2.5 text-center text-xs text-violet-bright hover:underline"
            >
              Open Notification Center
            </Link>
          </>
        )}
      </PopoverContent>
    </Popover>
  );
}
