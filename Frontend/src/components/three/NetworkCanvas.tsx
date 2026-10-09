"use client";

import dynamic from "next/dynamic";
import { GraduationCap, Target, Gauge, Sparkles, Building2, ShieldCheck } from "lucide-react";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { useIsMobile } from "@/hooks/useIsMobile";

const PlacementIntelligenceNetwork = dynamic(
  () => import("@/components/three/PlacementIntelligenceNetwork"),
  { ssr: false, loading: () => null },
);

const staticNodes = [
  { label: "Student", icon: GraduationCap },
  { label: "Skills", icon: Target },
  { label: "Readiness", icon: Gauge },
  { label: "Opportunity", icon: Sparkles },
  { label: "Recruiter", icon: Building2 },
  { label: "Offer", icon: ShieldCheck },
];

/** Lightweight 2D stand-in used on mobile and under reduced-motion, and as
 * the R3F chunk's loading state — never a blank gap while the bundle loads. */
function NetworkFallback() {
  return (
    <div className="flex h-full items-center justify-center px-6">
      <div className="flex w-full max-w-3xl items-center justify-between">
        {staticNodes.map((n, i) => {
          const Icon = n.icon;
          return (
            <div key={n.label} className="flex flex-1 items-center">
              <div className="flex flex-col items-center gap-1.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-full border border-violet/30 bg-violet/10">
                  <Icon className="h-4 w-4 text-violet-bright" />
                </div>
                <span className="text-[10px] text-muted-foreground">{n.label}</span>
              </div>
              {i < staticNodes.length - 1 && <div className="mx-1 h-px flex-1 bg-gradient-to-r from-violet/40 via-border to-violet/40" />}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Gated mount point for the landing page's 3D network. Only mounts the
 * R3F canvas on capable, motion-enabled desktop viewports; everywhere
 * else it renders the same visual story as a static 2D strip so the
 * section never breaks or feels empty.
 */
export function NetworkCanvas() {
  const reduced = useReducedMotion();
  const isMobile = useIsMobile();

  if (reduced || isMobile) {
    return <NetworkFallback />;
  }

  return <PlacementIntelligenceNetwork />;
}
