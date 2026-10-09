"use client";

import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";

export function ScoreRing({
  value,
  size = 148,
  strokeWidth = 10,
  label,
  sublabel,
  colorClass = "stroke-[hsl(var(--violet))]",
  valueClassName,
  animateKey,
}: {
  value: number; // 0-100
  size?: number;
  strokeWidth?: number;
  label?: string;
  sublabel?: string;
  colorClass?: string;
  valueClassName?: string;
  /** Change this to re-trigger the reveal animation (e.g. on refresh) */
  animateKey?: string | number;
}) {
  const reduced = useReducedMotion();
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const [displayValue, setDisplayValue] = useState(reduced ? value : 0);

  useEffect(() => {
    if (reduced) {
      setDisplayValue(value);
      return;
    }
    setDisplayValue(0);
    const start = performance.now();
    const durationMs = 900;
    let raf: number;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplayValue(Math.round(eased * value));
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, animateKey, reduced]);

  const offset = circumference - (displayValue / 100) * circumference;

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
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
          className={cn("fill-none", colorClass)}
          style={{ transition: reduced ? undefined : "stroke-dashoffset 0.1s linear" }}
        />
      </svg>
      <div className="absolute flex flex-col items-center justify-center text-center px-1">
        <span
          className={cn(
            size < 60
              ? "text-xs font-bold"
              : size < 85
              ? "text-base font-bold"
              : size < 110
              ? "text-xl font-bold"
              : size < 140
              ? "text-2xl font-bold"
              : "text-3xl font-semibold",
            "tabular-nums text-foreground leading-none",
            valueClassName
          )}
        >
          {displayValue}
        </span>
        {label && (
          <span className={cn(size < 85 ? "text-[9px] leading-tight mt-0.5" : "text-[11px] mt-0.5", "text-muted-foreground")}>
            {label}
          </span>
        )}
        {sublabel && (
          <span className={cn(size < 85 ? "text-[8px] leading-tight" : "text-[10px] mt-0.5", "text-muted-foreground/70")}>
            {sublabel}
          </span>
        )}
      </div>
    </div>
  );
}
