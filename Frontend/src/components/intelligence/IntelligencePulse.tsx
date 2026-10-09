"use client";

import { motion, AnimatePresence } from "framer-motion";
import { Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * NEXPLOY signature interaction — the "Intelligence Pulse".
 *
 * A single reusable component triggered every time a simulated
 * intelligence action runs: Digital Twin refresh, What-If recalculation,
 * Candidate analysis, Risk analysis, Schedule optimization, Copilot
 * response. Do not build one-off glow effects elsewhere — wrap the
 * relevant surface with this component instead.
 */
export function IntelligencePulse({
  active,
  children,
  className,
}: {
  active: boolean;
  children: React.ReactNode;
  className?: string;
}) {
  const reduced = useReducedMotion();

  return (
    <div className={cn("relative", className)}>
      <AnimatePresence>
        {active && !reduced && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
            className="pointer-events-none absolute -inset-1 -z-10 rounded-2xl"
            style={{
              background:
                "radial-gradient(60% 60% at 50% 50%, hsl(var(--violet) / 0.35), transparent 70%)",
              filter: "blur(18px)",
            }}
          />
        )}
      </AnimatePresence>
      <div
        className={cn(
          "relative rounded-xl transition-shadow duration-500",
          active && "shadow-glow-strong ring-1 ring-violet/40",
        )}
      >
        {children}
      </div>
    </div>
  );
}

/** Small inline badge used at the top of a staged-run panel. */
export function IntelligencePulseBadge({ label }: { label: string }) {
  const reduced = useReducedMotion();
  return (
    <div className="inline-flex items-center gap-1.5 rounded-full border border-violet/40 bg-violet/10 px-2.5 py-1 text-[11px] font-medium text-violet-bright">
      <Sparkles className={cn("h-3 w-3", !reduced && "animate-pulse-glow")} />
      {label}
    </div>
  );
}
