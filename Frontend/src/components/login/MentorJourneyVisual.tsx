"use client";

import { useEffect, useId, useState } from "react";
import { motion } from "framer-motion";
import { ClipboardCheck, TrendingUp, TriangleAlert, UserSearch, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { roleIcon } from "@/components/layout/nav-config";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * Mentor sign-in centerpiece — "Student Journey Pathway".
 *
 *   At Risk → Student Review → Mentor (guidance) → Intervention → Progress
 *
 * One smooth ascending S-curve in SVG, with HTML checkpoints placed by
 * percentage of a 560×340 space. The Mentor checkpoint is the largest — the
 * point where data becomes human guidance. Only the Intelligence Pulse and a
 * gentle Mentor glow move; the path itself is static.
 */

const W = 560;
const H = 340;

type Stage = {
  key: "risk" | "review" | "mentor" | "intervention" | "progress";
  label: string;
  sub?: string;
  icon: LucideIcon;
  x: number;
  y: number;
};

const MentorIcon = roleIcon.mentor;

const STAGES: Stage[] = [
  { key: "risk", label: "At Risk", sub: "Needs attention", icon: TriangleAlert, x: 62, y: 282 },
  { key: "review", label: "Student Review", icon: UserSearch, x: 174, y: 226 },
  { key: "mentor", label: "Mentor", sub: "Guidance", icon: MentorIcon, x: 286, y: 168 },
  { key: "intervention", label: "Intervention", icon: ClipboardCheck, x: 398, y: 122 },
  { key: "progress", label: "Progress", sub: "Readiness ↑", icon: TrendingUp, x: 500, y: 80 },
];

// Segment i runs stage i → i+1. Horizontal tangents at every checkpoint give
// each hop a soft S, so the whole journey reads as one flowing ascent.
const seg = (a: Stage, b: Stage) => {
  const mx = (a.x + b.x) / 2;
  return `M${a.x} ${a.y} C ${mx} ${a.y}, ${mx} ${b.y}, ${b.x} ${b.y}`;
};
const SEGS = STAGES.slice(0, -1).map((s, i) => seg(s, STAGES[i + 1]));
const FULL = STAGES.slice(0, -1)
  .map((s, i) => {
    const b = STAGES[i + 1];
    const mx = (s.x + b.x) / 2;
    return `${i === 0 ? `M${s.x} ${s.y} ` : ""}C ${mx} ${s.y}, ${mx} ${b.y}, ${b.x} ${b.y}`;
  })
  .join(" ");

const pctX = (x: number) => `${(x / W) * 100}%`;
const pctY = (y: number) => `${(y / H) * 100}%`;

type Key = Stage["key"];

// Pulse cycle: which checkpoint is responding as the signal passes it. The
// signal pauses on the Mentor (3.0s → 4.3s) before moving on.
const SEQ: [number, Key | null][] = [
  [0, "risk"],
  [1400, "review"],
  [3000, "mentor"],
  [4300, "intervention"],
  [5700, "progress"],
  [7300, null],
];
const COMET_S = 7.2;
const LOOP_MS = 12000;
// Path fractions where the signal reaches each checkpoint (matches SEQ pacing).
const COMET_TIMES = [0, 0.2, 0.42, 0.58, 0.78, 1];
const COMET_OFFSET = [0.04, -0.21, -0.46, -0.46, -0.71, -1];

// Which segments glow for a given responding checkpoint.
const LIT_SEGS: Record<Key, number[]> = {
  risk: [],
  review: [0],
  mentor: [1, 2],
  intervention: [2],
  progress: [3],
};
const HOVER_SEGS: Record<Key, number[]> = {
  risk: [0],
  review: [0, 1],
  mentor: [0, 1, 2, 3],
  intervention: [2],
  progress: [3],
};

export function MentorJourneyVisual() {
  const uid = useId().replace(/:/g, "");
  const reduced = useReducedMotion();
  const animate = !reduced;
  const [stage, setStage] = useState(0);
  const [lit, setLit] = useState<Key | null>(null);
  const [cycle, setCycle] = useState(0);
  const [hover, setHover] = useState<Key | null>(null);

  useEffect(() => {
    if (reduced) {
      setStage(99);
      return;
    }
    const ms = [100, 500, 750, 1000, 1250];
    const t = ms.map((m, i) => setTimeout(() => setStage(i + 1), m));
    return () => t.forEach(clearTimeout);
  }, [reduced]);

  useEffect(() => {
    if (reduced) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = () => {
      setCycle((c) => c + 1);
      timers = SEQ.map(([ms, k]) => setTimeout(() => setLit(k), ms));
    };
    const first = setTimeout(run, 2200);
    const loop = setInterval(run, LOOP_MS);
    return () => {
      clearTimeout(first);
      clearInterval(loop);
      timers.forEach(clearTimeout);
    };
  }, [reduced]);

  const enter = (k: Key) => (e: React.PointerEvent) => {
    if (e.pointerType === "mouse") setHover(k);
  };
  const leave = () => setHover(null);

  const segOn = (i: number) =>
    hover ? HOVER_SEGS[hover].includes(i) : lit ? LIT_SEGS[lit].includes(i) : false;
  const dimmed = (i: number) => hover !== null && !HOVER_SEGS[hover].includes(i);
  const nodeOn = (k: Key) => lit === k || hover === k;

  return (
    <figure
      className="relative m-0 w-full select-none"
      style={{ aspectRatio: `${W} / ${H}` }}
      aria-label="Student journey: an at-risk student is reviewed, guided by a mentor, given an intervention plan, and shows progress."
    >
      {/* glow behind the Mentor checkpoint only */}
      <div
        className="pointer-events-none absolute"
        style={{
          left: pctX(286 - 120),
          top: pctY(168 - 120),
          width: pctX(240),
          height: pctY(240),
          background: "radial-gradient(closest-side, hsl(var(--violet) / 0.2), transparent 75%)",
        }}
      />

      <svg viewBox={`0 0 ${W} ${H}`} className="absolute inset-0 h-full w-full overflow-visible" aria-hidden="true" focusable="false">
        <defs>
          {/* muted at the start → brighter toward progress */}
          <linearGradient id={`${uid}-g`} gradientUnits="userSpaceOnUse" x1="62" y1="0" x2="500" y2="0">
            <stop offset="0" stopColor="#5B3FAF" />
            <stop offset="0.5" stopColor="#8B5CF6" />
            <stop offset="1" stopColor="#D2BFFF" />
          </linearGradient>
        </defs>

        {SEGS.map((d, i) => {
          const visible = stage >= i + 1;
          const on = segOn(i);
          const base = dimmed(i) ? 0.35 : 0.85;
          const draw = { pathLength: { duration: 0.6, ease: "easeOut" as const }, opacity: { duration: 0.3 }, strokeWidth: { duration: 0.3 } };
          const init = { pathLength: animate ? 0 : 1, opacity: 0, strokeWidth: 1.4 };
          const target = { pathLength: visible ? 1 : animate ? 0 : 1 };
          return (
            <g key={i}>
              {/* dark base */}
              <motion.path d={d} fill="none" stroke="#3B2A78" strokeLinecap="round" initial={init} animate={{ ...target, opacity: visible ? 0.35 : 0, strokeWidth: 5 }} transition={draw} />
              {/* soft glow */}
              <motion.path d={d} fill="none" stroke="#7C3AED" strokeLinecap="round" initial={init} animate={{ ...target, opacity: visible ? (on ? 0.3 : 0.1) : 0, strokeWidth: 8 }} transition={draw} />
              {/* primary thin line */}
              <motion.path d={d} fill="none" stroke={`url(#${uid}-g)`} strokeLinecap="round" initial={init} animate={{ ...target, opacity: visible ? (on ? 1 : base) : 0, strokeWidth: on ? 2.2 : 1.4 }} transition={draw} />
            </g>
          );
        })}

        {/* Intelligence Pulse — pauses on the Mentor checkpoint */}
        {animate && cycle > 0 && (
          <motion.path
            key={`c${cycle}`}
            d={FULL}
            pathLength={1}
            fill="none"
            stroke="#F3EBFF"
            strokeWidth={2.4}
            strokeLinecap="round"
            strokeDasharray="0.03 2"
            initial={{ strokeDashoffset: 0.04, opacity: 0 }}
            animate={{ strokeDashoffset: COMET_OFFSET, opacity: [0, 1, 1, 0] }}
            transition={{
              strokeDashoffset: { duration: COMET_S, times: COMET_TIMES, ease: "linear" },
              opacity: { duration: COMET_S, times: [0, 0.03, 0.97, 1] },
            }}
            style={{ filter: "drop-shadow(0 0 4px hsl(var(--violet-bright)))" }}
          />
        )}
      </svg>

      {/* checkpoints */}
      <ol className="m-0 list-none p-0">
        {STAGES.map((s, i) => {
          const Icon = s.icon;
          const isMentor = s.key === "mentor";
          const isRisk = s.key === "risk";
          const isEnd = s.key === "progress";
          const offset = s.key === "review" || s.key === "intervention";
          const size = isMentor ? 66 : 32;
          const visible = stage >= i + 1;
          const on = nodeOn(s.key);
          return (
            <motion.li
              key={s.key}
              onPointerEnter={enter(s.key)}
              onPointerLeave={leave}
              initial={{ opacity: 0, scale: isMentor ? 0.96 : 1 }}
              animate={{ opacity: visible ? 1 : 0, scale: visible ? 1 : isMentor ? 0.96 : 1 }}
              transition={{ duration: 0.45, ease: "easeOut" }}
              className="absolute flex flex-col items-center"
              style={{ left: pctX(s.x), top: pctY(s.y), translate: "-50% -50%" }}
            >
              <div className="relative" style={{ width: size, height: size }}>
                {isMentor && <div className="absolute -inset-3 rounded-full border border-violet/20" />}
                {isMentor && (
                  <div
                    className={cn("absolute -inset-5 rounded-full", animate && "animate-pulse-glow")}
                    style={{ background: "radial-gradient(closest-side, hsl(var(--violet) / 0.28), transparent 72%)" }}
                  />
                )}
                {isMentor && animate && lit === "mentor" && (
                  <motion.div
                    key={`ring${cycle}`}
                    className="absolute inset-0 rounded-full border border-violet-bright/70"
                    initial={{ scale: 1, opacity: 0.8 }}
                    animate={{ scale: 1.6, opacity: 0 }}
                    transition={{ duration: 1.1, ease: "easeOut", delay: 0.2 }}
                  />
                )}
                <div
                  className={cn(
                    "relative flex h-full w-full flex-col items-center justify-center rounded-full border transition-all duration-500",
                    isMentor
                      ? on
                        ? "border-violet-bright/85 shadow-[0_0_38px_-2px_hsl(var(--violet)/0.8)]"
                        : "border-violet/65 shadow-[0_0_26px_-4px_hsl(var(--violet)/0.55)]"
                      : on
                        ? "border-violet-bright/80 bg-violet/25 shadow-[0_0_18px_-2px_hsl(var(--violet)/0.8)]"
                        : isEnd
                          ? "border-violet-bright/50 bg-card/85 shadow-[0_0_16px_-3px_hsl(var(--violet)/0.65)]"
                          : "border-violet/35 bg-card/85 shadow-[0_0_10px_-4px_hsl(var(--violet)/0.5)]",
                  )}
                  style={isMentor ? { background: "radial-gradient(circle at 50% 38%, hsl(var(--violet) / 0.36), hsl(var(--card)) 72%)" } : undefined}
                >
                  <Icon
                    className={cn(isMentor ? "h-6 w-6" : "h-3.5 w-3.5", isRisk ? "text-risk/80" : "text-violet-bright")}
                    aria-hidden="true"
                  />
                  {isMentor && <span className="mt-0.5 text-[11px] font-semibold leading-none text-foreground">Mentor</span>}
                </div>
              </div>
              {/* the path arrives from the lower-left and leaves to the upper-right,
                  so mid-journey labels sit in the free lower-right quadrant */}
              <div
                className={cn(
                  "flex flex-col",
                  offset ? "absolute left-[calc(50%+12px)] top-[calc(100%-6px)] items-start text-left" : "mt-1.5 items-center text-center",
                )}
              >
                <span
                  className={cn(
                    "whitespace-nowrap leading-tight",
                    isMentor ? "text-[9px] font-medium uppercase tracking-[0.2em] text-violet-bright" : "text-[12px] font-medium",
                    !isMentor && (on ? "text-foreground" : "text-foreground/85"),
                  )}
                >
                  {isMentor ? "Guidance" : s.label}
                </span>
                {!isMentor && s.sub && <span className="whitespace-nowrap text-[9.5px] leading-tight text-muted-foreground">{s.sub}</span>}
              </div>
            </motion.li>
          );
        })}
      </ol>
    </figure>
  );
}

/* ------------------------------------------------------------------ */
/* Compact vertical journey — tablet / mobile, shown above the form      */
/* ------------------------------------------------------------------ */

export function MentorJourneyCompact() {
  return (
    <div className="relative overflow-hidden rounded-xl border border-white/[0.07] bg-elevated/70 p-4">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: "radial-gradient(60% 40% at 30% 50%, hsl(var(--violet) / 0.18), transparent 70%)" }}
      />
      <ol className="relative m-0 list-none p-0">
        <span
          className="absolute bottom-4 left-[15px] top-4 w-px bg-gradient-to-b from-violet/25 via-violet/60 to-violet-bright"
          aria-hidden="true"
        />
        {STAGES.map((s) => {
          const Icon = s.icon;
          const isMentor = s.key === "mentor";
          return (
            <li key={s.key} className="relative flex items-center gap-3 py-2">
              <span
                className={cn(
                  "relative z-10 flex shrink-0 items-center justify-center rounded-full border bg-card",
                  isMentor
                    ? "h-9 w-9 border-violet/70 shadow-[0_0_20px_-2px_hsl(var(--violet)/0.7)]"
                    : "h-[30px] w-[30px] border-violet/35",
                  isMentor ? "-ml-[2px]" : "",
                )}
              >
                <Icon className={cn(isMentor ? "h-4 w-4" : "h-3.5 w-3.5", s.key === "risk" ? "text-risk/80" : "text-violet-bright")} aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className={cn("block leading-tight text-foreground", isMentor ? "text-[14px] font-semibold" : "text-[13px] font-medium")}>
                  {s.label}
                </span>
                {s.sub && <span className="block text-[10.5px] leading-tight text-muted-foreground">{s.sub}</span>}
              </span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
