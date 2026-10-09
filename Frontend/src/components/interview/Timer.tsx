"use client";

import { useEffect, useRef, useState } from "react";
import { Clock } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * A lightweight countdown for one mock-interview question. It counts down from `seconds` and calls
 * `onExpire` once when it reaches zero — the parent decides what expiry means (it never force-submits
 * here). Restarting is driven by `resetKey`: change it (e.g. to the question id) and the timer
 * restarts from `seconds`. Purely presentational; no answer data flows through it.
 */
export function Timer({
  seconds,
  resetKey,
  onExpire,
  className,
}: {
  seconds: number;
  resetKey?: string | number;
  onExpire?: () => void;
  className?: string;
}) {
  const [remaining, setRemaining] = useState(seconds);
  const firedRef = useRef(false);

  useEffect(() => {
    setRemaining(seconds);
    firedRef.current = false;
    const id = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(id);
          if (!firedRef.current) {
            firedRef.current = true;
            onExpire?.();
          }
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seconds, resetKey]);

  const mm = Math.floor(remaining / 60);
  const ss = remaining % 60;
  const low = remaining <= 15;

  return (
    <div
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-sm font-medium tabular-nums",
        low ? "border-risk/40 bg-risk/10 text-risk" : "border-border text-muted-foreground",
        className,
      )}
      role="timer"
      aria-live="off"
    >
      <Clock className="h-3.5 w-3.5" />
      {mm}:{ss.toString().padStart(2, "0")}
    </div>
  );
}
