"use client";

import dynamic from "next/dynamic";
import type { LucideIcon } from "lucide-react";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { useIsMobile } from "@/hooks/useIsMobile";
import type { RoleSignal } from "@/components/three/RoleSignatureScene";

const RoleSignatureScene = dynamic(() => import("@/components/three/RoleSignatureScene"), {
  ssr: false,
  loading: () => null,
});

/** Static stand-in for mobile / reduced-motion / while the R3F chunk
 * loads — never a blank gap while the bundle loads. */
function SignalFallback({ signals }: { signals: RoleSignal[] }) {
  return (
    <div className="flex h-full items-center">
      {signals.map((s, i) => {
        const SignalIcon = s.icon;
        return (
          <div key={s.label} className="flex flex-1 items-center">
            <div className="flex flex-col items-center gap-1.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-full border border-violet/30 bg-violet/10">
                <SignalIcon className="h-4 w-4 text-violet-bright" />
              </div>
              <span className="text-center text-[10px] leading-tight text-muted-foreground">{s.label}</span>
            </div>
            {i < signals.length - 1 && <div className="mx-1 mb-4 h-px flex-1 bg-border" />}
          </div>
        );
      })}
    </div>
  );
}

/**
 * Gated mount point for a role sign-in page's 3D centerpiece — mirrors
 * NetworkCanvas.tsx's pattern: only mounts the R3F canvas on capable,
 * motion-enabled desktop viewports; everywhere else it renders the same
 * icon/label story as a static 2D strip so the page never breaks or
 * feels empty.
 */
export function RoleSignatureCanvas({
  Icon,
  label,
  signals,
}: {
  Icon: LucideIcon;
  label: string;
  signals: RoleSignal[];
}) {
  const reduced = useReducedMotion();
  const isMobile = useIsMobile();

  if (reduced || isMobile) {
    return <SignalFallback signals={signals} />;
  }

  return <RoleSignatureScene Icon={Icon} label={label} signals={signals} />;
}
