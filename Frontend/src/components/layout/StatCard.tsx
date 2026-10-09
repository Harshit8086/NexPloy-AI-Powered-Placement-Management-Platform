import type { LucideIcon } from "lucide-react";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { AnimatedMetric } from "@/components/intelligence/AnimatedMetric";
import { cn } from "@/lib/utils";

export function StatCard({
  label,
  value,
  suffix = "",
  icon: Icon,
  trend,
  trendPositive = true,
  accent = "violet",
}: {
  label: string;
  value: string | number;
  /** Appended after the animated number, e.g. "%" or "/100". Ignored for string values. */
  suffix?: string;
  icon?: LucideIcon;
  trend?: string;
  trendPositive?: boolean;
  accent?: "violet" | "success" | "warning" | "risk";
}) {
  const accentClass = {
    violet: "bg-violet/10 text-violet-bright",
    success: "bg-success/10 text-success",
    warning: "bg-warning/10 text-warning",
    risk: "bg-risk/10 text-risk",
  }[accent];

  // Only animate the count-up when the value is a plain number — string
  // values (e.g. "91%", "₹12L") render as-is rather than trying to parse.
  const isNumeric = typeof value === "number";

  return (
    <DepthCard className="p-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-muted-foreground">{label}</p>
          <p className="mt-1.5 text-2xl font-semibold tabular-nums text-foreground">
            {isNumeric ? <AnimatedMetric value={value} suffix={suffix} /> : value}
          </p>
        </div>
        {Icon && (
          <div className={cn("flex h-8 w-8 shrink-0 items-center justify-center rounded-lg", accentClass)}>
            <Icon className="h-4 w-4" />
          </div>
        )}
      </div>
      {trend && (
        <p className={cn("mt-2 text-xs", trendPositive ? "text-success" : "text-risk")}>{trend}</p>
      )}
    </DepthCard>
  );
}
