"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { roleDescription } from "@/components/layout/nav-config";
import type { ConstellationRole } from "@/components/three/RoleConstellation";

/** The login hub's role picker — a clickable avatar-card grid. */
function ConstellationFallback({ roles }: { roles: ConstellationRole[] }) {
  return (
    <div className="grid w-full grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
      {roles.map((r) => {
        const Icon = r.icon;
        return (
          <Link
            key={r.role}
            href={`/login/${r.role}`}
            className="group flex h-full flex-col items-center gap-3 rounded-2xl border border-border bg-card/60 px-4 py-6 text-center shadow-card backdrop-blur-sm transition-all duration-300 focus-visible:-translate-y-1 focus-visible:border-violet/50 focus-visible:shadow-glow-strong focus-visible:outline-none active:translate-y-0 active:scale-[0.98]"
          >
            <span className="flex h-14 w-14 items-center justify-center rounded-full border border-violet/30 bg-violet/10 text-violet-bright transition-transform duration-300">
              <Icon className="h-6 w-6" />
            </span>
            <span className="text-sm font-semibold text-foreground">{r.label}</span>
            <span className="text-[11px] leading-snug text-muted-foreground">{roleDescription[r.role]}</span>
            <span className="mt-auto flex items-center gap-1 pt-1 text-[11px] font-medium text-violet-bright opacity-0 transition-opacity duration-300 group-hover:opacity-100">
              Continue <ArrowRight className="h-3 w-3" />
            </span>
          </Link>
        );
      })}
    </div>
  );
}

/**
 * Login hub's role picker. Was a mounted R3F constellation on capable
 * desktop viewports; reverted to always using the flat clickable card
 * grid per direct design feedback — the 3D scene didn't hold up well
 * at wide viewports (too much dead space, nodes small and off-center).
 * The 3D scene component is kept unused rather than deleted in case we
 * revisit it, but this entry point no longer mounts it.
 *
 * NOTE: as of the RoleSelector.tsx redesign, the actual /login page no
 * longer imports this component at all — it now renders RoleSelector
 * directly. This file is kept only as unused legacy fallback code.
 */
export function RoleConstellationCanvas({ roles }: { roles: ConstellationRole[] }) {
  return <ConstellationFallback roles={roles} />;
}
