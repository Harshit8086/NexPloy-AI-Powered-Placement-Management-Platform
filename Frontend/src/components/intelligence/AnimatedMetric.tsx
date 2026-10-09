"use client";

import { useAnimatedNumber } from "@/hooks/useAnimatedNumber";
import { cn } from "@/lib/utils";

/**
 * Shared count-up number display. Use for every KPI/stat value in the app
 * (StatCard, Command Center, Dashboard) instead of rendering a static
 * number — reveals the same way everywhere.
 */
export function AnimatedMetric({
  value,
  prefix = "",
  suffix = "",
  decimals = 0,
  duration = 800,
  resetKey,
  className,
}: {
  value: number;
  prefix?: string;
  suffix?: string;
  decimals?: number;
  duration?: number;
  resetKey?: string | number;
  className?: string;
}) {
  const display = useAnimatedNumber(value, duration, resetKey);
  return (
    <span className={cn("tabular-nums", className)}>
      {prefix}
      {display.toFixed(decimals)}
      {suffix}
    </span>
  );
}
