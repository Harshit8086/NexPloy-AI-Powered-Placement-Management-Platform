"use client";

import * as React from "react";
import { AlertTriangle, Inbox, Loader2 } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * The single async-state primitive for data-backed pages. Every page that fetches business
 * data from the API renders through this so the four states are consistent and, critically,
 * an error NEVER silently falls back to fabricated data — the user sees an error with a retry.
 *
 * Precedence: loading -> error -> empty -> children. Pair it with `useApiResource`, e.g.
 *
 *   const { data, loading, error, reload } = useApiResource(() => listDrives(), [], []);
 *   <AsyncBoundary loading={loading} error={error} empty={data.length === 0} onRetry={reload}>
 *     {data.map(...)}
 *   </AsyncBoundary>
 */
export interface AsyncBoundaryProps {
  loading: boolean;
  error?: string | null;
  /** True when the fetch succeeded but returned no rows (shows the empty state, not children). */
  empty?: boolean;
  onRetry?: () => void;
  /** Copy for the empty state. */
  emptyTitle?: string;
  emptyMessage?: string;
  /** Optional custom skeleton shown while loading (defaults to a spinner row). */
  loadingFallback?: React.ReactNode;
  className?: string;
  children: React.ReactNode;
}

export function AsyncBoundary({
  loading,
  error,
  empty = false,
  onRetry,
  emptyTitle = "Nothing here yet",
  emptyMessage = "There's no data to show yet.",
  loadingFallback,
  className,
  children,
}: AsyncBoundaryProps) {
  if (loading) {
    return (
      <div className={className}>
        {loadingFallback ?? (
          <div
            className="flex items-center justify-center gap-2 py-12 text-sm text-muted-foreground"
            role="status"
            aria-live="polite"
          >
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>Loading…</span>
          </div>
        )}
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive" className={cn("my-4", className)}>
        <AlertTriangle />
        <AlertTitle>Couldn&apos;t load this</AlertTitle>
        <AlertDescription className="flex flex-col gap-3">
          <span>{error}</span>
          {onRetry ? (
            <Button variant="outline" size="sm" className="w-fit" onClick={onRetry}>
              Retry
            </Button>
          ) : null}
        </AlertDescription>
      </Alert>
    );
  }

  if (empty) {
    return (
      <div
        className={cn(
          "flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border py-12 text-center",
          className,
        )}
      >
        <Inbox className="h-6 w-6 text-muted-foreground" />
        <p className="font-medium">{emptyTitle}</p>
        <p className="max-w-sm text-sm text-muted-foreground">{emptyMessage}</p>
        {onRetry ? (
          <Button variant="ghost" size="sm" className="mt-1" onClick={onRetry}>
            Refresh
          </Button>
        ) : null}
      </div>
    );
  }

  return <>{children}</>;
}
