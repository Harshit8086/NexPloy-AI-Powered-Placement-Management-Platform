"use client";

import { useEffect, useMemo, useState } from "react";
import { Card } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  getNotificationPreferences,
  updateNotificationPreferences,
} from "@/lib/api/notifications";
import type { NotificationPreference, NotificationType } from "@/types";

/** Human labels for each type, grouped into the categories the preferences UI displays. */
const CATEGORIES: { title: string; types: { type: NotificationType; label: string }[] }[] = [
  {
    title: "Offers",
    types: [
      { type: "OFFER_EXTENDED", label: "Offer extended" },
      { type: "OFFER_ACCEPTED", label: "Offer accepted" },
      { type: "OFFER_DECLINED", label: "Offer declined" },
      { type: "OFFER_REVOKED", label: "Offer revoked" },
      { type: "OFFER_DEADLINE", label: "Offer response deadline" },
    ],
  },
  {
    title: "Applications & shortlisting",
    types: [
      { type: "APPLICATION_ADVANCED", label: "Application advanced" },
      { type: "SHORTLISTED", label: "Shortlisted" },
      { type: "SHORTLIST_REJECTED", label: "Not shortlisted" },
    ],
  },
  {
    title: "Interviews",
    types: [
      { type: "INTERVIEW_SCHEDULED", label: "Interview scheduled" },
      { type: "INTERVIEW_RESCHEDULED", label: "Interview rescheduled" },
      { type: "INTERVIEW_CANCELLED", label: "Interview cancelled" },
      { type: "INTERVIEW_REMINDER", label: "Interview reminder" },
    ],
  },
  {
    title: "Documents",
    types: [
      { type: "DOCUMENT_APPROVED", label: "Document approved" },
      { type: "DOCUMENT_REJECTED", label: "Document rejected" },
    ],
  },
  {
    title: "Drives & readiness",
    types: [
      { type: "DRIVE_PUBLISHED", label: "Drive published" },
      { type: "RISK_DETECTED", label: "Risk detected" },
      { type: "INTERVENTION_ASSIGNED", label: "Intervention assigned" },
    ],
  },
];

/**
 * Per-type opt-out settings for the signed-in user. Renders one toggle per notification type
 * (grouped by category); flipping a toggle immediately persists via PUT and reflects the
 * server's resolved set. Only IN_APP is delivered today, so this reads as "notify me about…".
 */
export function NotificationPreferences() {
  const pushToast = useAppStore((s) => s.pushToast);
  const { data, loading, error, reload } = useApiResource(
    () => getNotificationPreferences(),
    [],
    [],
  );

  const [enabled, setEnabled] = useState<Record<string, boolean>>({});
  const [saving, setSaving] = useState<NotificationType | null>(null);

  useEffect(() => {
    setEnabled(Object.fromEntries(data.map((p) => [p.type, p.enabled])));
  }, [data]);

  const total = useMemo(() => CATEGORIES.reduce((n, c) => n + c.types.length, 0), []);
  const mutedCount = useMemo(
    () => Object.values(enabled).filter((v) => v === false).length,
    [enabled],
  );

  async function toggle(type: NotificationType, next: boolean) {
    const previous = enabled;
    setEnabled((e) => ({ ...e, [type]: next })); // optimistic
    setSaving(type);
    try {
      const resolved = await updateNotificationPreferences([{ type, enabled: next }]);
      setEnabled(Object.fromEntries(resolved.map((p: NotificationPreference) => [p.type, p.enabled])));
    } catch {
      setEnabled(previous); // roll back
      pushToast("Couldn't save preference", "Please try again.");
    } finally {
      setSaving(null);
    }
  }

  return (
    <Card className="p-5">
      <div className="mb-4">
        <h3 className="text-sm font-semibold">Notification preferences</h3>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Choose which notifications you receive.{" "}
          {mutedCount > 0 ? `${mutedCount} of ${total} muted.` : "All types on."}
        </p>
      </div>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={false}
        onRetry={reload}
      >
        <div className="space-y-5">
          {CATEGORIES.map((category) => (
            <div key={category.title}>
              <p className="mb-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground/70">
                {category.title}
              </p>
              <div className="divide-y divide-border rounded-lg border border-border">
                {category.types.map(({ type, label }) => (
                  <div key={type} className="flex items-center justify-between px-3 py-2.5">
                    <span className="text-sm">{label}</span>
                    <Switch
                      checked={enabled[type] ?? true}
                      disabled={saving === type}
                      onCheckedChange={(next) => void toggle(type, next)}
                      aria-label={label}
                    />
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </AsyncBoundary>
    </Card>
  );
}
