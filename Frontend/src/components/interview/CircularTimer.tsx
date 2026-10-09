"use client";

import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

/**
 * A circular countdown ring for one mock-interview question — the green→amber→red ring that mirrors
 * the reference "AI Interview" design. It counts down from `seconds`, shows the remaining seconds in
 * the centre (e.g. `89s`), and calls `onExpire` once at zero (the parent decides what expiry means;
 * this never force-submits). Restart is driven by `resetKey`: change it (e.g. to the question id) and
 * the ring refills from `seconds`. Purely presentational — no answer data flows through it.
 */
export function CircularTimer({
  seconds,
  resetKey,
  onExpire,
  size = 72,
  strokeWidth = 6,
}: {
  seconds: number;
  resetKey?: string | number;
  onExpire?: () => void;
  size?: number;
  strokeWidth?: number;
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

  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const frac = seconds > 0 ? Math.max(0, Math.min(1, remaining / seconds)) : 0;
  const offset = circumference - frac * circumference;
  // Green while there's plenty of time, amber past the two-thirds mark, red in the final stretch.
  const tone = frac > 0.5 ? "success" : frac > 0.2 ? "warn" : "risk";
  const ringClass =
    tone === "success"
      ? "stroke-success"
      : tone === "warn"
        ? "stroke-[hsl(var(--warning,38_92%_50%))]"
        : "stroke-risk";
  const textClass = tone === "success" ? "text-success" : tone === "warn" ? "text-foreground" : "text-risk";

  return (
    <div
      className="relative inline-flex items-center justify-center"
      style={{ width: size, height: size }}
      role="timer"
      aria-live="off"
    >
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={strokeWidth}
          className="fill-none stroke-muted"
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          className={cn("fill-none transition-[stroke-dashoffset] duration-1000 ease-linear", ringClass)}
        />
      </svg>
      <span className={cn("absolute text-sm font-semibold tabular-nums", textClass)}>{remaining}s</span>
    </div>
  );
}
