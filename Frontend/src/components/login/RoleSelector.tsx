"use client";

import { useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { roleDescription, roleIcon, roleLabel, roleOrder } from "@/components/layout/nav-config";
import type { Role } from "@/types";

/**
 * Role-selection cards for the login hub.
 *
 * Each card has a large avatar slot. Final 3D-style character art is dropped
 * in by adding a transparent WebP at the path in AVATAR_SRC (public/avatars/
 * <role>.webp, ~640×640) — no component changes needed. Until then a
 * dimensional icon "orb" with role-specific signals fills the slot.
 */

/** Set a role's path here once its final artwork exists in /public. */
const AVATAR_SRC: Partial<Record<Role, string>> = {
  student: "/avatars/student.webp",
  recruiter: "/avatars/recruiter.webp",
  officer: "/avatars/officer.webp",
  mentor: "/avatars/mentor.webp",
  admin: "/avatars/admin.webp",
};

/** Restrained accent hue per role — violet stays dominant. */
const ACCENT_HUE: Record<Role, number> = {
  student: 214,
  recruiter: 238,
  officer: 190,
  mentor: 285,
  admin: 315,
};

/** Subtle role-specific signals shown as a caption (labels only, no metrics). */
const SIGNALS: Record<Role, [string, string, string]> = {
  student: ["Skills", "Readiness", "Opportunities"],
  recruiter: ["Candidate Pool", "Ranking", "Match"],
  officer: ["Drives", "Schedule", "Outcomes"],
  mentor: ["Guide", "Support", "Progress"],
  admin: ["Campuses", "Governance", "Analytics"],
};

function RoleAvatar({ role }: { role: Role }) {
  const Icon = roleIcon[role];
  const src = AVATAR_SRC[role];
  return (
    <div className="relative h-full w-full" aria-hidden="true">
      {/* aura */}
      <div
        className="absolute inset-[6%] rounded-full opacity-70 transition-opacity duration-300 group-hover:opacity-100 group-focus-visible:opacity-100"
        style={{
          background:
            "radial-gradient(closest-side, hsl(var(--violet) / 0.42), hsl(var(--accent-h) 80% 60% / 0.12) 60%, transparent 78%)",
        }}
      />
      {/* avatar */}
      <div
        className="absolute inset-0 flex items-center justify-center transition-transform duration-300 ease-out group-focus-visible:-translate-y-1 group-focus-visible:scale-[1.025]"
      >
        <div
          className="flex h-full w-full items-center justify-center"
          style={{ transform: "translate(calc(var(--px, 0) * 4px), calc(var(--py, 0) * 4px))" }}
        >
          {src ? (
            <div className="relative aspect-square h-[74%]">
              <div className="absolute -inset-[14%] rounded-full border border-violet/20" />
              <div className="absolute -inset-[5%] rounded-full border border-violet/35" />
              <div className="relative h-full w-full overflow-hidden rounded-full border border-violet/50 shadow-[0_14px_34px_-10px_hsl(var(--violet)/0.7),inset_0_2px_0_0_hsl(0_0%_100%/0.14),inset_0_-14px_26px_-10px_hsl(0_0%_0%/0.6)]">
                <Image
                  src={src}
                  alt=""
                  width={240}
                  height={240}
                  sizes="200px"
                  className="h-full w-full object-cover object-top"
                />
              </div>
            </div>
          ) : (
            <div className="relative aspect-square h-[74%]">
              <div className="absolute -inset-[14%] rounded-full border border-violet/20" />
              <div className="absolute -inset-[5%] rounded-full border border-violet/35" />
              <div
                className="relative flex h-full w-full items-center justify-center rounded-full border border-violet/50 shadow-[0_14px_34px_-10px_hsl(var(--violet)/0.7),inset_0_2px_0_0_hsl(0_0%_100%/0.14),inset_0_-14px_26px_-10px_hsl(0_0%_0%/0.6)]"
                style={{
                  background:
                    "radial-gradient(circle at 34% 26%, hsl(var(--violet-bright) / 0.85), hsl(var(--violet) / 0.55) 42%, hsl(var(--accent-h) 70% 30% / 0.9) 100%)",
                }}
              >
                <Icon className="h-[42%] w-[42%] text-white drop-shadow-[0_4px_8px_hsl(0_0%_0%/0.45)]" strokeWidth={1.6} />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function RoleCard({
  role,
  index,
  reduced,
  selected,
  onSelect,
}: {
  role: Role;
  index: number;
  reduced: boolean;
  selected: Role | null;
  onSelect: (role: Role) => void;
}) {
  const Icon = roleIcon[role];
  const ref = useRef<HTMLAnchorElement | null>(null);
  const isSelected = selected === role;
  const href = `/login/${role}`;

  // pointer parallax via CSS vars — no re-renders
  const onMove = (e: React.PointerEvent<HTMLAnchorElement>) => {
    if (reduced || e.pointerType !== "mouse") return;
    const r = e.currentTarget.getBoundingClientRect();
    e.currentTarget.style.setProperty("--px", String(((e.clientX - r.left) / r.width - 0.5) * 2));
    e.currentTarget.style.setProperty("--py", String(((e.clientY - r.top) / r.height - 0.5) * 2));
  };
  const onLeave = (e: React.PointerEvent<HTMLAnchorElement>) => {
    e.currentTarget.style.setProperty("--px", "0");
    e.currentTarget.style.setProperty("--py", "0");
  };

  const onClick = (e: React.MouseEvent<HTMLAnchorElement>) => {
    // let modified clicks (new tab etc.) use the normal link behaviour
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    if (reduced) return;
    e.preventDefault();
    onSelect(role);
  };

  return (
    <motion.div
      initial={{ opacity: reduced ? 1 : 0, y: reduced ? 0 : 14 }}
      animate={{ opacity: selected && !isSelected ? 0.45 : 1, y: 0 }}
      transition={{ duration: 0.45, delay: reduced ? 0 : 0.35 + index * 0.08, ease: "easeOut" }}
      className="max-sm:w-full sm:w-[calc(33.333%-0.75rem)] lg:w-[calc(20%-0.9rem)]"
    >
      <Link
        ref={ref}
        href={href}
        onClick={onClick}
        onPointerMove={onMove}
        onPointerLeave={onLeave}
        aria-label={`${roleLabel[role]} — ${roleDescription[role]}`}
        style={{ ["--accent-h" as string]: ACCENT_HUE[role] }}
        className={cn(
          "group relative flex h-full overflow-hidden rounded-2xl border bg-gradient-to-b from-elevated to-card text-center shadow-card transition-all duration-300 ease-out",
          "border-white/[0.09]",
          "focus-visible:-translate-y-1.5 focus-visible:border-violet-bright/70 focus-visible:shadow-glow-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-violet-bright/60",
          "max-sm:flex-row max-sm:items-center max-sm:gap-4 max-sm:p-3 sm:flex-col sm:pb-4 sm:pt-3",
          isSelected && "-translate-y-1.5 border-violet-bright/80 shadow-glow-strong",
        )}
      >
        <span
          className="pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent via-violet-bright/70 to-transparent opacity-50 transition-opacity duration-300 group-hover:opacity-100"
          aria-hidden="true"
        />
        <div className="h-24 w-24 shrink-0 sm:h-[168px] sm:w-full xl:h-[184px]">
          <RoleAvatar role={role} />
        </div>
        <div className="flex min-w-0 flex-1 flex-col max-sm:items-start max-sm:text-left sm:items-center sm:px-3">
          <div className="flex items-center gap-1.5">
            <Icon className="h-3.5 w-3.5 text-violet-bright" aria-hidden="true" />
            <h2 className="text-sm font-semibold tracking-tight text-foreground">{roleLabel[role]}</h2>
          </div>
          <p className="mt-1 text-[12px] leading-snug text-muted-foreground">{roleDescription[role]}</p>
          <p className="mt-2 text-[9px] font-medium uppercase tracking-[0.14em] text-muted-foreground/60 transition-colors duration-300 group-hover:text-violet-bright/80 group-focus-visible:text-violet-bright/80 max-sm:hidden">
            {SIGNALS[role].join(" · ")}
          </p>
          <span className="mt-3 inline-flex items-center gap-1 text-[11px] font-medium text-violet-bright/70 transition-colors duration-300 group-hover:text-violet-bright group-focus-visible:text-violet-bright max-sm:mt-2">
            Continue
            <ArrowRight
              className="h-3 w-3 transition-transform duration-300 group-hover:translate-x-0.5 group-focus-visible:translate-x-0.5"
              aria-hidden="true"
            />
          </span>
        </div>
      </Link>
    </motion.div>
  );
}

export function RoleSelector() {
  const reduced = useReducedMotion();
  const router = useRouter();
  const [selected, setSelected] = useState<Role | null>(null);

  const onSelect = (role: Role) => {
    setSelected(role);
    // short emphasis, then the existing route
    setTimeout(() => router.push(`/login/${role}`), 300);
  };

  return (
    <div className="relative mx-auto w-full max-w-6xl">
      <div
        className="pointer-events-none absolute inset-x-0 top-1/2 h-[120%] -translate-y-1/2"
        style={{ background: "radial-gradient(45% 45% at 50% 50%, hsl(var(--violet) / 0.14), transparent 75%)" }}
        aria-hidden="true"
      />
      <div className="relative flex flex-wrap justify-center gap-4">
        {roleOrder.map((role, i) => (
          <RoleCard key={role} role={role} index={i} reduced={reduced} selected={selected} onSelect={onSelect} />
        ))}
      </div>
    </div>
  );
}
