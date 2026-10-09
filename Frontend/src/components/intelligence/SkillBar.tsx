"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

export function SkillBar({
  label,
  value,
  max = 100,
  colorClass = "bg-violet",
  valueSuffix = "%",
  className,
}: {
  label: string;
  value: number;
  max?: number;
  colorClass?: string;
  valueSuffix?: string;
  className?: string;
}) {
  const pct = Math.min(100, (value / max) * 100);
  return (
    <div className={cn("space-y-1.5", className)}>
      <div className="flex items-center justify-between gap-2 text-xs">
        <span className="min-w-0 truncate text-muted-foreground">{label}</span>
        <span className="shrink-0 font-medium tabular-nums text-foreground">
          {value}
          {valueSuffix}
        </span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
        <motion.div
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.6, ease: "easeOut" }}
          className={cn("h-full rounded-full", colorClass)}
        />
      </div>
    </div>
  );
}
