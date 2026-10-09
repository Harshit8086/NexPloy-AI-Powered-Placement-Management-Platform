"use client";

import dynamic from "next/dynamic";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { useIsMobile } from "@/hooks/useIsMobile";
import type { TwinModule } from "@/components/three/DigitalTwinScene";

const DigitalTwinScene = dynamic(() => import("@/components/three/DigitalTwinScene"), {
  ssr: false,
  loading: () => null,
});

/** Static stand-in for mobile / reduced-motion / while the R3F chunk loads. */
function ConstellationFallback({ modules }: { modules: TwinModule[] }) {
  return (
    <div className="grid h-full grid-cols-2 content-center gap-3 px-4 sm:grid-cols-3">
      {modules.map((m) => (
        <div key={m.label} className="rounded-lg border border-violet/25 bg-violet/5 px-3 py-2.5 text-center">
          <p className="text-lg font-semibold tabular-nums text-violet-bright">{Math.round(m.value)}</p>
          <p className="mt-0.5 text-[10px] text-muted-foreground">{m.label}</p>
        </div>
      ))}
    </div>
  );
}

export function DigitalTwinCanvas({ readiness, modules }: { readiness: number; modules: TwinModule[] }) {
  const reduced = useReducedMotion();
  const isMobile = useIsMobile();

  if (reduced || isMobile) {
    return <ConstellationFallback modules={modules} />;
  }

  return <DigitalTwinScene readiness={readiness} modules={modules} />;
}
