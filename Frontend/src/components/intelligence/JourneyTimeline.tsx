"use client";

import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { PLACEMENT_JOURNEY_STAGES, PLACEMENT_JOURNEY_FULL_LABELS } from "@/lib/status";

export { PLACEMENT_JOURNEY_STAGES };

export function JourneyTimeline({
  stages = PLACEMENT_JOURNEY_STAGES as unknown as string[],
  currentIndex,
  /** Renders the whole track muted — used for a rejected application. */
  halted = false,
}: {
  stages?: string[];
  /** Index of the stage the student is currently at (0-based) */
  currentIndex: number;
  halted?: boolean;
}) {
  const reduced = useReducedMotion();

  return (
    <div className="flex w-full items-start overflow-x-auto pb-2" role="list">
      {stages.map((stage, i) => {
        const completed = i < currentIndex;
        const current = i === currentIndex && !halted;
        const future = i > currentIndex || halted;
        return (
          <div key={stage} className="flex flex-1 items-center last:flex-none" role="listitem">
            <div className="flex shrink-0 flex-col items-center gap-1.5">
              <div
                className={cn(
                  "flex h-7 w-7 items-center justify-center rounded-full border-2 text-[10px] font-semibold transition-colors",
                  completed && "border-success bg-success/15 text-success",
                  current && "border-violet-bright bg-violet/20 text-violet-bright",
                  current && !reduced && "animate-pulse-glow",
                  future && "border-border bg-muted text-muted-foreground",
                )}
                title={PLACEMENT_JOURNEY_FULL_LABELS[stage] ?? stage}
              >
                {completed ? <Check className="h-3.5 w-3.5" /> : i + 1}
              </div>
              <span
                className={cn(
                  "whitespace-nowrap text-[9.5px] font-medium uppercase tracking-wide",
                  completed && "text-success",
                  current && "text-violet-bright",
                  future && "text-muted-foreground/60",
                )}
              >
                {stage}
              </span>
            </div>
            {i < stages.length - 1 && (
              <div
                className={cn(
                  "mx-1.5 mt-[-18px] h-0.5 min-w-[14px] flex-1 rounded-full",
                  i < currentIndex && !halted ? "bg-success/60" : "bg-border",
                )}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
