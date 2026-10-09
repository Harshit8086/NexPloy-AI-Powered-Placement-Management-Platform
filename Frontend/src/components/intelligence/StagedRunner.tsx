"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Check, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { StagedStep } from "@/lib/simulate";

/**
 * Runs a fixed, deterministic sequence of labeled steps (used by Digital
 * Twin refresh, Candidate Analysis, and Schedule Optimization) and calls
 * onDone once every step has resolved. Reused instead of re-implementing
 * the staged-text pattern per screen.
 */
export function StagedRunner({
  steps,
  onDone,
}: {
  steps: StagedStep[];
  onDone: () => void;
}) {
  const [completedCount, setCompletedCount] = useState(0);

  useEffect(() => {
    setCompletedCount(0);
    let cancelled = false;
    let cumulative = 0;

    steps.forEach((step, i) => {
      cumulative += step.durationMs;
      window.setTimeout(() => {
        if (cancelled) return;
        setCompletedCount(i + 1);
        if (i === steps.length - 1) {
          window.setTimeout(() => {
            if (!cancelled) onDone();
          }, 200);
        }
      }, cumulative);
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [steps]);

  return (
    <div className="space-y-2.5">
      {steps.map((step, i) => {
        const done = i < completedCount;
        const active = i === completedCount;
        return (
          <motion.div
            key={step.label}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: active || done ? 1 : 0.35, x: 0 }}
            className="flex items-center gap-2.5 text-sm"
          >
            <span
              className={cn(
                "flex h-5 w-5 shrink-0 items-center justify-center rounded-full border",
                done && "border-success bg-success/15 text-success",
                active && !done && "border-violet-bright text-violet-bright",
                !done && !active && "border-border text-muted-foreground",
              )}
            >
              {done ? (
                <Check className="h-3 w-3" />
              ) : active ? (
                <Loader2 className="h-3 w-3 animate-spin" />
              ) : (
                <span className="h-1 w-1 rounded-full bg-current" />
              )}
            </span>
            <span className={cn(done ? "text-foreground" : active ? "text-foreground" : "text-muted-foreground")}>
              {step.label}
            </span>
          </motion.div>
        );
      })}
    </div>
  );
}
