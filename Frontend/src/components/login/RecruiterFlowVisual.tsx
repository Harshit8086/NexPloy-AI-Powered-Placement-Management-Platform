"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowDown,
  BarChart3,
  Building2,
  Briefcase,
  CalendarClock,
  ClipboardCheck,
  FileText,
  Sparkles,
  Users,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * Recruiter sign-in centerpiece — "Hiring Intelligence Flow".
 *
 *   Applications / Assessments / AI Matching / Interviews
 *        →  Recruiter (intelligence core)  →  Analytics / Candidate Pool / Offers
 *
 * Pure SVG paths + positioned HTML labels: no WebGL, no canvas, no new
 * dependency. Geometry lives in a 560×340 coordinate space; HTML elements are
 * placed by percentage of that space so paths and labels stay aligned at any
 * width. The story reads fully without animation.
 */

const W = 560;
const H = 340;
const CX = 270;
const CY = 170;
const CORE_R = 42;

type Item = { label: string; sub?: string; icon: LucideIcon };

const inputs: Item[] = [
  { label: "Applications", icon: FileText },
  { label: "Assessments", icon: ClipboardCheck },
  { label: "AI Matching", sub: "AI-assisted", icon: Sparkles },
  { label: "Interviews", icon: CalendarClock },
];
const outputs: Item[] = [
  { label: "Analytics", sub: "Hiring insights", icon: BarChart3 },
  { label: "Candidate Pool", sub: "Ranked & filtered", icon: Users },
  { label: "Offers", sub: "Track & manage", icon: Briefcase },
];

const IN_Y = [56, 132, 208, 284];
const OUT_Y = [62, 170, 278];
const IN_X = 130;
const OUT_X = 402;

const inPath = (y: number) => `M${IN_X} ${y} C ${IN_X + 58} ${y}, ${CX - CORE_R - 46} ${CY}, ${CX - CORE_R} ${CY}`;
const outPath = (y: number) => `M${CX + CORE_R} ${CY} C ${CX + CORE_R + 46} ${CY}, ${OUT_X - 52} ${y}, ${OUT_X} ${y}`;

const pctX = (x: number) => `${(x / W) * 100}%`;
const pctY = (y: number) => `${(y / H) * 100}%`;

type Hover = { kind: "in" | "out"; i: number } | { kind: "core" } | null;

// Intelligence Pulse loop: 1–4 inputs in turn, 5 core, 6 outputs, 7 cards lit.
const PHASES: [number, number][] = [
  [1, 0],
  [2, 550],
  [3, 1100],
  [4, 1650],
  [5, 2200],
  [6, 2900],
  [7, 3900],
  [0, 4800],
];
const LOOP_MS = 8500;

function Comet({ d, delay = 0 }: { d: string; delay?: number }) {
  return (
    <motion.path
      d={d}
      pathLength={1}
      fill="none"
      stroke="#F3EBFF"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeDasharray="0.12 2"
      initial={{ strokeDashoffset: 0.12, opacity: 0 }}
      animate={{ strokeDashoffset: -1, opacity: [0, 1, 1, 0] }}
      transition={{ duration: 0.95, ease: "easeInOut", delay }}
      style={{ filter: "drop-shadow(0 0 3px hsl(var(--violet-bright)))" }}
    />
  );
}

export function RecruiterFlowVisual() {
  const reduced = useReducedMotion();
  const animate = !reduced;
  const [stage, setStage] = useState(0);
  const [phase, setPhase] = useState(0);
  const [hover, setHover] = useState<Hover>(null);

  useEffect(() => {
    if (reduced) {
      setStage(99);
      return;
    }
    const ms = [80, 350, 550, 850, 1000, 1130, 1260];
    const t = ms.map((m, i) => setTimeout(() => setStage(i + 1), m));
    return () => t.forEach(clearTimeout);
  }, [reduced]);

  useEffect(() => {
    if (reduced) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = () => {
      timers = PHASES.map(([p, ms]) => setTimeout(() => setPhase(p), ms));
    };
    const first = setTimeout(run, 2800);
    const loop = setInterval(run, LOOP_MS);
    return () => {
      clearTimeout(first);
      clearInterval(loop);
      timers.forEach(clearTimeout);
    };
  }, [reduced]);

  const onEnter = (h: Hover) => (e: React.PointerEvent) => {
    if (e.pointerType === "mouse") setHover(h);
  };
  const onLeave = () => setHover(null);

  // Path emphasis given the current hover
  const lineStyle = (kind: "in" | "out", i: number) => {
    if (!hover) return { o: 0.42, w: 1 };
    if (hover.kind === "core") return { o: 0.95, w: 1.5 };
    if (hover.kind === kind && hover.i === i) return { o: 1, w: 1.9 };
    return { o: 0.12, w: 1 };
  };
  const coreLit = hover?.kind === "core" || (hover?.kind === "in" && hover.i === 2) || phase === 5;
  const outLit = (i: number) =>
    phase === 7 || hover?.kind === "core" || (hover?.kind === "out" && hover.i === i);
  const inLit = (i: number) =>
    phase === i + 1 || hover?.kind === "core" || (hover?.kind === "in" && hover.i === i);

  return (
    <figure
      className="relative m-0 w-full select-none"
      style={{ aspectRatio: `${W} / ${H}` }}
      aria-label="Hiring intelligence flow: applications, assessments, AI matching and interviews feed the recruiter core, which produces analytics, a ranked candidate pool and offers."
    >
      {/* soft glow behind the core */}
      <div
        className="pointer-events-none absolute"
        style={{
          left: pctX(CX - 130),
          top: pctY(CY - 130),
          width: pctX(260),
          height: pctY(260),
          background: "radial-gradient(closest-side, hsl(var(--violet) / 0.2), transparent 75%)",
        }}
      />

      <svg viewBox={`0 0 ${W} ${H}`} className="absolute inset-0 h-full w-full overflow-visible" aria-hidden="true" focusable="false">
        <defs>
          <linearGradient id="rf-in" gradientUnits="userSpaceOnUse" x1={IN_X} y1="0" x2={CX - CORE_R} y2="0">
            <stop offset="0" stopColor="#6D28D9" stopOpacity="0.55" />
            <stop offset="1" stopColor="#A57BFF" />
          </linearGradient>
          <linearGradient id="rf-out" gradientUnits="userSpaceOnUse" x1={CX + CORE_R} y1="0" x2={OUT_X} y2="0">
            <stop offset="0" stopColor="#A57BFF" />
            <stop offset="1" stopColor="#6D28D9" stopOpacity="0.6" />
          </linearGradient>
        </defs>

        {/* input streams */}
        {IN_Y.map((y, i) => {
          const d = inPath(y);
          const s = lineStyle("in", i);
          return (
            <g key={`in${i}`}>
              <motion.path
                d={d}
                fill="none"
                stroke="url(#rf-in)"
                strokeLinecap="round"
                initial={{ pathLength: animate ? 0 : 1, opacity: 0, strokeWidth: 1 }}
                animate={{ pathLength: stage >= 3 ? 1 : animate ? 0 : 1, opacity: stage >= 3 ? s.o : 0, strokeWidth: s.w }}
                transition={{ pathLength: { duration: 0.7, delay: animate ? i * 0.06 : 0, ease: "easeOut" }, opacity: { duration: 0.3 }, strokeWidth: { duration: 0.25 } }}
              />
              {animate && phase === i + 1 && <Comet d={d} />}
              <circle cx={IN_X} cy={y} r={hover?.kind === "in" && hover.i === i ? 4.5 : 3.5} fill="#B794FF" opacity={stage >= 1 ? (inLit(i) ? 1 : 0.75) : 0} style={{ transition: "all .3s", filter: "drop-shadow(0 0 4px hsl(var(--violet-bright)))" }} />
            </g>
          );
        })}

        {/* output streams */}
        {OUT_Y.map((y, i) => {
          const d = outPath(y);
          const s = lineStyle("out", i);
          return (
            <g key={`out${i}`}>
              <motion.path
                d={d}
                fill="none"
                stroke="url(#rf-out)"
                strokeLinecap="round"
                initial={{ pathLength: animate ? 0 : 1, opacity: 0, strokeWidth: 1 }}
                animate={{ pathLength: stage >= 4 ? 1 : animate ? 0 : 1, opacity: stage >= 4 ? s.o : 0, strokeWidth: s.w }}
                transition={{ pathLength: { duration: 0.7, delay: animate ? i * 0.08 : 0, ease: "easeOut" }, opacity: { duration: 0.3 }, strokeWidth: { duration: 0.25 } }}
              />
              {animate && phase === 6 && <Comet d={d} delay={i * 0.06} />}
              <circle cx={OUT_X} cy={y} r={3.5} fill="#B794FF" opacity={stage >= 4 ? (outLit(i) ? 1 : 0.7) : 0} style={{ transition: "opacity .3s", filter: "drop-shadow(0 0 4px hsl(var(--violet-bright)))" }} />
            </g>
          );
        })}
      </svg>

      {/* inputs */}
      <ul className="m-0 list-none p-0">
        {inputs.map((it, i) => {
          const Icon = it.icon;
          const ai = i === 2;
          return (
            <motion.li
              key={it.label}
              onPointerEnter={onEnter({ kind: "in", i })}
              onPointerLeave={onLeave}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: stage >= 1 ? 1 : 0, x: stage >= 1 ? 0 : -8 }}
              transition={{ duration: 0.4, delay: animate ? i * 0.07 : 0 }}
              className="absolute flex items-center justify-end gap-1.5 text-right"
              style={{ left: 0, width: pctX(IN_X - 12), top: pctY(IN_Y[i]), translate: "0 -50%" }}
            >
              <span className="min-w-0">
                <span className={cn("block truncate text-[12px] font-medium transition-colors", inLit(i) ? "text-foreground" : "text-foreground/85")}>
                  {it.label}
                </span>
                {it.sub && <span className="block text-[9px] leading-none text-violet-bright/90">{it.sub}</span>}
              </span>
              <span
                className={cn(
                  "flex h-6 w-6 shrink-0 items-center justify-center rounded-full border transition-colors",
                  inLit(i) ? "border-violet-bright/70 bg-violet/25" : "border-violet/30 bg-violet/10",
                  ai && animate && "animate-pulse-glow",
                )}
              >
                <Icon className="h-3 w-3 text-violet-bright" aria-hidden="true" />
              </span>
            </motion.li>
          );
        })}
      </ul>

      {/* recruiter core */}
      <motion.div
        onPointerEnter={onEnter({ kind: "core" })}
        onPointerLeave={onLeave}
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: stage >= 2 ? 1 : 0, scale: stage >= 2 ? 1 : 0.96 }}
        transition={{ duration: 0.5, ease: "easeOut" }}
        className="absolute"
        style={{
          left: pctX(CX - CORE_R),
          top: pctY(CY - CORE_R),
          width: pctX(CORE_R * 2),
          height: pctY(CORE_R * 2),
        }}
      >
        <div className="absolute -inset-3 rounded-full border border-violet/15" />
        {animate && phase === 5 && (
          <motion.div
            key="core-ring"
            className="absolute inset-0 rounded-full border border-violet-bright/70"
            initial={{ scale: 1, opacity: 0.8 }}
            animate={{ scale: 1.55, opacity: 0 }}
            transition={{ duration: 1, ease: "easeOut" }}
          />
        )}
        <div
          className={cn(
            "relative flex h-full w-full flex-col items-center justify-center rounded-full border transition-shadow duration-500",
            coreLit
              ? "border-violet-bright/80 shadow-[0_0_34px_-2px_hsl(var(--violet)/0.75)]"
              : "border-violet/55 shadow-[0_0_22px_-4px_hsl(var(--violet)/0.5)]",
          )}
          style={{ background: "radial-gradient(circle at 50% 40%, hsl(var(--violet) / 0.32), hsl(var(--card)) 72%)" }}
        >
          <Building2 className="h-5 w-5 text-violet-bright" aria-hidden="true" />
          <span className="mt-0.5 text-[11px] font-semibold text-foreground">Recruiter</span>
        </div>
        <span
          className={cn(
            "pointer-events-none absolute left-1/2 top-full mt-3 -translate-x-1/2 whitespace-nowrap text-[8px] font-medium uppercase tracking-[0.2em] text-violet-bright transition-opacity duration-300",
            hover?.kind === "core" ? "opacity-100" : "opacity-0",
          )}
        >
          Hiring intelligence
        </span>
      </motion.div>

      {/* outputs */}
      <ul className="m-0 list-none p-0">
        {outputs.map((it, i) => {
          const Icon = it.icon;
          return (
            <motion.li
              key={it.label}
              onPointerEnter={onEnter({ kind: "out", i })}
              onPointerLeave={onLeave}
              initial={{ opacity: 0, x: 8 }}
              animate={{ opacity: stage >= 5 + i ? 1 : 0, x: stage >= 5 + i ? 0 : 8 }}
              transition={{ duration: 0.4 }}
              className="absolute"
              style={{ left: pctX(OUT_X + 6), width: pctX(W - OUT_X - 6), top: pctY(OUT_Y[i]), translate: "0 -50%" }}
            >
              <div
                className={cn(
                  "flex items-center gap-1.5 rounded-[10px] border bg-card/70 px-2 py-2 backdrop-blur-sm transition-all duration-500",
                  outLit(i)
                    ? "border-violet/60 shadow-[0_0_22px_-6px_hsl(var(--violet)/0.7)]"
                    : "border-white/[0.07] shadow-[0_4px_18px_-10px_hsl(var(--violet)/0.35)]",
                )}
              >
                <span className="flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-md border border-violet/30 bg-violet/10">
                  <Icon className="h-3.5 w-3.5 text-violet-bright" aria-hidden="true" />
                </span>
                <span className="min-w-0">
                  <span className="block whitespace-nowrap text-[12px] font-semibold leading-tight text-foreground">{it.label}</span>
                  <span className="block whitespace-nowrap text-[9.5px] leading-tight text-muted-foreground">{it.sub}</span>
                </span>
              </div>
            </motion.li>
          );
        })}
      </ul>
    </figure>
  );
}

/* ------------------------------------------------------------------ */
/* Compact vertical flow — tablet / mobile, shown above the form        */
/* ------------------------------------------------------------------ */

function Chip({ item, className }: { item: Item; className?: string }) {
  const Icon = item.icon;
  return (
    <li className={cn("flex items-center gap-2 rounded-lg border border-white/[0.07] bg-card/60 px-2.5 py-1.5", className)}>
      <Icon className="h-3.5 w-3.5 shrink-0 text-violet-bright" aria-hidden="true" />
      <span className="text-[12px] leading-tight text-foreground">{item.label}</span>
    </li>
  );
}

function Down() {
  return (
    <div className="flex flex-col items-center py-1.5" aria-hidden="true">
      <span className="h-3 w-px bg-gradient-to-b from-violet/0 to-violet-bright/70" />
      <ArrowDown className="h-3 w-3 text-violet-bright" />
    </div>
  );
}

export function RecruiterFlowCompact() {
  return (
    <div className="relative overflow-hidden rounded-xl border border-white/[0.07] bg-elevated/70 p-4">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: "radial-gradient(60% 50% at 50% 50%, hsl(var(--violet) / 0.18), transparent 70%)" }}
      />
      <div className="relative">
        <ul className="m-0 grid list-none grid-cols-2 gap-2 p-0">
          {inputs.map((it) => (
            <Chip key={it.label} item={it} />
          ))}
        </ul>
        <Down />
        <div className="mx-auto flex w-fit items-center gap-2 rounded-full border border-violet/60 bg-violet/15 px-4 py-2 shadow-[0_0_22px_-4px_hsl(var(--violet)/0.5)]">
          <Building2 className="h-4 w-4 text-violet-bright" aria-hidden="true" />
          <span className="text-[13px] font-semibold text-foreground">Recruiter</span>
        </div>
        <Down />
        <ul className="m-0 grid list-none grid-cols-3 gap-2 p-0">
          {[outputs[1], outputs[0], outputs[2]].map((it) => (
            <Chip key={it.label} item={it} className="flex-col gap-1 px-1.5 py-2 text-center" />
          ))}
        </ul>
      </div>
    </div>
  );
}
