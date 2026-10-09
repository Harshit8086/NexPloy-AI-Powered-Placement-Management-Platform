"use client";

import { SoftParticleField } from "@/components/intelligence/SoftParticleField";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { useIsMobile } from "@/hooks/useIsMobile";

/**
 * The shared background motion layer for authenticated dashboards (student/
 * officer/recruiter). Mounted once in AppShell — never per-page. Barely
 * noticeable by design: a fixed, pointer-events-none layer behind content
 * with a faint violet radial glow, a very slow gradient drift, and a
 * sparse particle field. Disabled outright under prefers-reduced-motion.
 */
export function AmbientBackground() {
  const reduced = useReducedMotion();
  const isMobile = useIsMobile();

  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden" aria-hidden>
      <div
        className={!reduced && !isMobile ? "animate-ambient-drift" : undefined}
        style={{
          position: "absolute",
          inset: "-10%",
          background:
            "radial-gradient(38% 32% at 18% 12%, hsl(var(--violet) / 0.10), transparent 65%), " +
            "radial-gradient(30% 28% at 85% 78%, hsl(var(--violet-bright) / 0.07), transparent 65%)",
        }}
      />
      {!isMobile && (
        <div
          className="absolute inset-0 opacity-[0.035]"
          style={{
            backgroundImage:
              "linear-gradient(hsl(var(--violet-bright) / 0.6) 1px, transparent 1px), linear-gradient(90deg, hsl(var(--violet-bright) / 0.6) 1px, transparent 1px)",
            backgroundSize: "64px 64px",
          }}
        />
      )}
      {!reduced && !isMobile && <SoftParticleField />}
    </div>
  );
}
