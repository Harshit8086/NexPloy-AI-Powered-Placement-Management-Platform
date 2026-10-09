"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * Shared connector line used to visually relate intelligence values —
 * readiness → skill gap → opportunity, roadmap milestones, matching
 * factors, orchestrator conflict links. One implementation everywhere
 * instead of bespoke divider divs per page.
 *
 * `orientation="horizontal"` renders a thin bar; `"vertical"` renders a
 * column bar (for stacked timelines). `active` triggers a single traveling
 * pulse along the line — use it for moments tied to a real recompute
 * (What-If toggle, Orchestrator optimize), not as constant background motion.
 */
export function GlowLine({
  orientation = "horizontal",
  active = false,
  tone = "violet",
  className,
}: {
  orientation?: "horizontal" | "vertical";
  active?: boolean;
  tone?: "violet" | "success" | "warning" | "risk";
  className?: string;
}) {
  const reduced = useReducedMotion();
  const toneVar = {
    violet: "--violet",
    success: "--success",
    warning: "--warning",
    risk: "--risk",
  }[tone];

  const isHorizontal = orientation === "horizontal";

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-full bg-border",
        isHorizontal ? "h-px w-full" : "h-full w-px",
        className,
      )}
    >
      <div
        className="absolute inset-0"
        style={{
          background: `linear-gradient(${isHorizontal ? "90deg" : "180deg"}, transparent, hsl(var(${toneVar}) / 0.6), transparent)`,
        }}
      />
      {active && !reduced && (
        <motion.div
          className="absolute rounded-full"
          style={
            isHorizontal
              ? { top: -2, height: 5, width: 28, filter: "blur(1px)" }
              : { left: -2, width: 5, height: 28, filter: "blur(1px)" }
          }
          initial={isHorizontal ? { left: "-10%" } : { top: "-10%" }}
          animate={isHorizontal ? { left: "100%" } : { top: "100%" }}
          transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}
        >
          <div
            className="h-full w-full rounded-full"
            style={{ background: `hsl(var(${toneVar}) / 0.9)`, boxShadow: `0 0 12px 2px hsl(var(${toneVar}) / 0.7)` }}
          />
        </motion.div>
      )}
    </div>
  );
}
