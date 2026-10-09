"use client";

import { useEffect, useId, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowDown,
  ArrowLeftRight,
  CalendarDays,
  ShieldAlert,
  ShieldCheck,
  SlidersHorizontal,
  TrendingUp,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * Placement Officer sign-in centerpiece — "Placement Command Loop".
 *
 * A stationary horizontal infinity loop (the continuous placement
 * lifecycle) with the Placement Officer core at the crossover, Plan Drives
 * in the left lobe, Track Outcomes in the right lobe, Risk Radar above and
 * Optimize & Scale below. Only the Intelligence Pulse moves.
 *
 * SVG + positioned HTML labels; geometry lives in a 560×340 space and the
 * HTML is placed by percentage so everything scales together.
 */

const W = 560;
const H = 340;
const CX = 280;
const CY = 170;
const CORE_R = 40;

// Right lobe, then left lobe — both start at the crossover so the reveal
// draws outward from the center.
const RIGHT = `M280 170 C 320 105 375 70 425 70 C 480 70 520 115 520 170 C 520 225 480 270 425 270 C 375 270 320 235 280 170`;
const LEFT = `M280 170 C 240 105 185 70 135 70 C 80 70 40 115 40 170 C 40 225 80 270 135 270 C 185 270 240 235 280 170`;
// Whole loop as one continuous path (starts at the far left) — the pulse
// travels this: Plan → Officer → Track → around → Officer → back to Plan.
const FULL = `M40 170 C 40 225 80 270 135 270 C 185 270 240 235 280 170 C 320 105 375 70 425 70 C 480 70 520 115 520 170 C 520 225 480 270 425 270 C 375 270 320 235 280 170 C 240 105 185 70 135 70 C 80 70 40 115 40 170 Z`;

const pctX = (x: number) => `${(x / W) * 100}%`;
const pctY = (y: number) => `${(y / H) * 100}%`;

type Node = "plan" | "track" | "risk" | "optimize" | "officer";
type Hover = Node | null;

// One Intelligence Pulse cycle (ms → which node is responding).
const SEQ: [number, Node | null][] = [
  [0, "plan"],
  [1200, "officer"],
  [2000, null],
  [2700, "track"],
  [3500, null],
  [3900, "optimize"],
  [4500, "officer"],
  [5200, "plan"],
  [6000, null],
  [7200, "risk"],
  [7900, "officer"],
  [8500, null],
];
const LOOP_MS = 10500;
const COMET_MS = 6000;

function Comet({ d, duration, width = 2.4 }: { d: string; duration: number; width?: number }) {
  return (
    <motion.path
      d={d}
      pathLength={1}
      fill="none"
      stroke="#F3EBFF"
      strokeWidth={width}
      strokeLinecap="round"
      strokeDasharray="0.035 2"
      initial={{ strokeDashoffset: 0.035, opacity: 0 }}
      animate={{ strokeDashoffset: -1, opacity: [0, 1, 1, 0] }}
      transition={{ duration: duration / 1000, ease: "linear", times: [0, 0.03, 0.97, 1] }}
      style={{ filter: "drop-shadow(0 0 4px hsl(var(--violet-bright)))" }}
    />
  );
}

function IconNode({
  icon: Icon,
  lit,
  size = 34,
  className,
}: {
  icon: LucideIcon;
  lit: boolean;
  size?: number;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full border transition-all duration-500",
        lit
          ? "border-violet-bright/80 bg-violet/25 shadow-[0_0_18px_-2px_hsl(var(--violet)/0.8)]"
          : "border-violet/40 bg-card/80 shadow-[0_0_12px_-4px_hsl(var(--violet)/0.5)]",
        className,
      )}
      style={{ width: size, height: size }}
    >
      <Icon className="h-[46%] w-[46%] text-violet-bright" aria-hidden="true" />
    </span>
  );
}

export function OfficerLoopVisual() {
  const uid = useId().replace(/:/g, "");
  const reduced = useReducedMotion();
  const animate = !reduced;
  const [stage, setStage] = useState(0);
  const [lit, setLit] = useState<Node | null>(null);
  const [cycle, setCycle] = useState(0);
  const [hover, setHover] = useState<Hover>(null);

  useEffect(() => {
    if (reduced) {
      setStage(99);
      return;
    }
    const ms = [120, 320, 900, 1000, 1100, 1200];
    const t = ms.map((m, i) => setTimeout(() => setStage(i + 1), m));
    return () => t.forEach(clearTimeout);
  }, [reduced]);

  useEffect(() => {
    if (reduced) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = () => {
      setCycle((c) => c + 1);
      timers = SEQ.map(([ms, n]) => setTimeout(() => setLit(n), ms));
    };
    const first = setTimeout(run, 2300);
    const loop = setInterval(run, LOOP_MS);
    return () => {
      clearTimeout(first);
      clearInterval(loop);
      timers.forEach(clearTimeout);
    };
  }, [reduced]);

  const enter = (h: Hover) => (e: React.PointerEvent) => {
    if (e.pointerType === "mouse") setHover(h);
  };
  const leave = () => setHover(null);

  const on = (n: Node) => lit === n || hover === n;
  const coreOn = hover === "officer" || hover === "risk" || lit === "officer";
  const leftBright = hover === "plan" || hover === "officer" || hover === "optimize";
  const rightBright = hover === "track" || hover === "officer" || hover === "optimize";
  const vertBright = (n: "risk" | "optimize") => hover === "officer" || hover === n || lit === n;

  const loopStroke = (bright: boolean) => ({ opacity: bright ? 1 : 0.72, strokeWidth: bright ? 2.2 : 1.6 });

  return (
    <figure
      className="relative m-0 w-full select-none"
      style={{ aspectRatio: `${W} / ${H}` }}
      aria-label="Placement command loop: Plan Drives and Track Outcomes on a continuous loop around the Placement Officer, with Risk Radar above and Optimize and Scale below."
    >
      <div
        className="pointer-events-none absolute"
        style={{
          left: pctX(30),
          top: pctY(20),
          width: pctX(500),
          height: pctY(300),
          background: "radial-gradient(closest-side, hsl(var(--violet) / 0.16), transparent 78%)",
        }}
      />

      <svg viewBox={`0 0 ${W} ${H}`} className="absolute inset-0 h-full w-full overflow-visible" aria-hidden="true" focusable="false">
        <defs>
          <linearGradient id={`${uid}-g`} gradientUnits="userSpaceOnUse" x1="40" y1="0" x2="520" y2="0">
            <stop offset="0" stopColor="#8B5CF6" />
            <stop offset="0.5" stopColor="#C4A6FF" />
            <stop offset="1" stopColor="#8B5CF6" />
          </linearGradient>
          <radialGradient id={`${uid}-f`} cx="50%" cy="50%" r="50%">
            <stop offset="0" stopColor="#7C3AED" stopOpacity="0.16" />
            <stop offset="1" stopColor="#7C3AED" stopOpacity="0.03" />
          </radialGradient>
        </defs>

        {/* base: dark translucent interior */}
        <motion.path
          d={FULL}
          fill={`url(#${uid}-f)`}
          stroke="none"
          initial={{ opacity: 0 }}
          animate={{ opacity: stage >= 2 ? 1 : 0 }}
          transition={{ duration: 0.8 }}
        />

        {[
          { d: RIGHT, bright: rightBright, k: "r" },
          { d: LEFT, bright: leftBright, k: "l" },
        ].map(({ d, bright, k }) => (
          <g key={k}>
            {/* soft glow layers (cheap wide low-opacity strokes, no blur filter) */}
            <motion.path
              d={d}
              fill="none"
              stroke="#7C3AED"
              strokeWidth={11}
              strokeLinecap="round"
              initial={{ pathLength: animate ? 0 : 1, opacity: 0 }}
              animate={{ pathLength: stage >= 2 ? 1 : animate ? 0 : 1, opacity: stage >= 2 ? (bright ? 0.2 : 0.09) : 0 }}
              transition={{ pathLength: { duration: 0.9, ease: "easeOut" }, opacity: { duration: 0.4 } }}
            />
            <motion.path
              d={d}
              fill="none"
              stroke="#8B5CF6"
              strokeWidth={5}
              strokeLinecap="round"
              initial={{ pathLength: animate ? 0 : 1, opacity: 0 }}
              animate={{ pathLength: stage >= 2 ? 1 : animate ? 0 : 1, opacity: stage >= 2 ? (bright ? 0.32 : 0.17) : 0 }}
              transition={{ pathLength: { duration: 0.9, ease: "easeOut" }, opacity: { duration: 0.4 } }}
            />
            {/* main stroke */}
            <motion.path
              d={d}
              fill="none"
              stroke={`url(#${uid}-g)`}
              strokeLinecap="round"
              initial={{ pathLength: animate ? 0 : 1, opacity: 0, strokeWidth: 1.6 }}
              animate={{
                pathLength: stage >= 2 ? 1 : animate ? 0 : 1,
                opacity: stage >= 2 ? loopStroke(bright).opacity : 0,
                strokeWidth: loopStroke(bright).strokeWidth,
              }}
              transition={{ pathLength: { duration: 0.9, ease: "easeOut" }, opacity: { duration: 0.3 }, strokeWidth: { duration: 0.3 } }}
            />
          </g>
        ))}

        {/* monitoring (above) and improvement (below) links */}
        <motion.path
          d={`M${CX} 58 L${CX} ${CY - CORE_R - 4}`}
          stroke="#A57BFF"
          strokeWidth={1.2}
          strokeDasharray="3 4"
          fill="none"
          animate={{ opacity: stage >= 5 ? (vertBright("risk") ? 1 : 0.5) : 0 }}
          transition={{ duration: 0.4 }}
        />
        <motion.path
          d={`M${CX} ${CY + CORE_R + 4} L${CX} 282`}
          stroke="#A57BFF"
          strokeWidth={1.2}
          strokeDasharray="3 4"
          fill="none"
          animate={{ opacity: stage >= 6 ? (vertBright("optimize") ? 1 : 0.5) : 0 }}
          transition={{ duration: 0.4 }}
        />

        {/* Intelligence Pulse */}
        {animate && cycle > 0 && <Comet key={`c${cycle}`} d={FULL} duration={COMET_MS} />}
        {animate && lit === "risk" && <Comet key={`r${cycle}`} d={`M${CX} 58 L${CX} ${CY - CORE_R}`} duration={700} width={2} />}
        {animate && hover === "plan" && <Comet key="hp" d={`M190 ${CY} L${CX - CORE_R} ${CY}`} duration={700} width={2} />}
      </svg>

      {/* Plan Drives — left lobe */}
      <motion.div
        onPointerEnter={enter("plan")}
        onPointerLeave={leave}
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 3 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute flex flex-col items-center gap-1.5 text-center"
        style={{ left: pctX(150), top: pctY(CY), translate: "-50% -50%" }}
      >
        <IconNode icon={CalendarDays} lit={on("plan")} />
        <span className={cn("text-[12px] font-medium leading-tight transition-colors", on("plan") ? "text-foreground" : "text-foreground/85")}>
          Plan
          <br />
          Drives
        </span>
      </motion.div>

      {/* Track Outcomes — right lobe */}
      <motion.div
        onPointerEnter={enter("track")}
        onPointerLeave={leave}
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 4 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute flex flex-col items-center gap-1.5 text-center"
        style={{ left: pctX(410), top: pctY(CY), translate: "-50% -50%" }}
      >
        <IconNode icon={TrendingUp} lit={on("track")} />
        <span className={cn("text-[12px] font-medium leading-tight transition-colors", on("track") ? "text-foreground" : "text-foreground/85")}>
          Track
          <br />
          Outcomes
        </span>
      </motion.div>

      {/* Risk Radar — above */}
      <motion.div
        onPointerEnter={enter("risk")}
        onPointerLeave={leave}
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 5 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute"
        style={{ left: pctX(CX), top: pctY(38), translate: "-50% -50%" }}
      >
        <IconNode icon={ShieldAlert} lit={on("risk")} size={30} />
        <span className="absolute left-full top-1/2 ml-2 -translate-y-1/2 whitespace-nowrap text-[11.5px] font-medium text-foreground/85">
          Risk Radar
        </span>
      </motion.div>

      {/* Optimize & Scale — below */}
      <motion.div
        onPointerEnter={enter("optimize")}
        onPointerLeave={leave}
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 6 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute"
        style={{ left: pctX(CX), top: pctY(302), translate: "-50% -50%" }}
      >
        <IconNode icon={SlidersHorizontal} lit={on("optimize")} size={30} />
        <span className="absolute left-full top-1/2 ml-2 -translate-y-1/2 whitespace-nowrap text-[11.5px] font-medium leading-tight text-foreground/85">
          Optimize &amp; Scale
        </span>
      </motion.div>

      {/* Placement Officer core */}
      <motion.div
        onPointerEnter={enter("officer")}
        onPointerLeave={leave}
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: stage >= 1 ? 1 : 0, scale: stage >= 1 ? 1 : 0.96 }}
        transition={{ duration: 0.5, ease: "easeOut" }}
        className="absolute"
        style={{
          left: pctX(CX - CORE_R),
          top: pctY(CY - CORE_R),
          width: pctX(CORE_R * 2),
          height: pctY(CORE_R * 2),
        }}
      >
        <div className="absolute -inset-3 rounded-full border border-violet/20" />
        {animate && lit === "officer" && (
          <motion.div
            key={`ring${cycle}-${lit}`}
            className="absolute inset-0 rounded-full border border-violet-bright/70"
            initial={{ scale: 1, opacity: 0.8 }}
            animate={{ scale: 1.5, opacity: 0 }}
            transition={{ duration: 0.9, ease: "easeOut" }}
          />
        )}
        <div
          className={cn(
            "relative flex h-full w-full flex-col items-center justify-center rounded-full border transition-shadow duration-500",
            coreOn
              ? "border-violet-bright/80 shadow-[0_0_36px_-2px_hsl(var(--violet)/0.8)]"
              : "border-violet/60 shadow-[0_0_24px_-4px_hsl(var(--violet)/0.55)]",
          )}
          style={{ background: "radial-gradient(circle at 50% 38%, hsl(var(--violet) / 0.34), hsl(var(--card)) 72%)" }}
        >
          <ShieldCheck className="h-[17px] w-[17px] text-violet-bright" aria-hidden="true" />
          <span className="mt-1 text-center text-[10.5px] font-semibold leading-[1.1] text-foreground">
            Placement
            <br />
            Officer
          </span>
        </div>
        <span
          className={cn(
            "pointer-events-none absolute left-1/2 top-full mt-2.5 -translate-x-1/2 whitespace-nowrap rounded-full border border-violet/30 bg-card px-2.5 py-0.5 text-[8px] font-medium uppercase tracking-[0.2em] text-violet-bright transition-opacity duration-300",
            hover === "officer" ? "opacity-100" : "opacity-0",
          )}
        >
          Placement command center
        </span>
      </motion.div>
    </figure>
  );
}

/* ------------------------------------------------------------------ */
/* Compact version — tablet / mobile, shown above the form              */
/* ------------------------------------------------------------------ */

function Chip({ icon: Icon, label, core, stack }: { icon: LucideIcon; label: string; core?: boolean; stack?: boolean }) {
  return (
    <div
      className={cn(
        "flex items-center justify-center gap-2 rounded-lg border px-2.5 py-2 text-center",
        stack && "h-full flex-col gap-1 px-1.5",
        core
          ? "border-violet/60 bg-violet/15 shadow-[0_0_22px_-4px_hsl(var(--violet)/0.5)]"
          : "border-white/[0.07] bg-card/60",
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0 text-violet-bright" aria-hidden="true" />
      <span className="text-[12px] font-medium leading-tight text-foreground">{label}</span>
    </div>
  );
}

function Down() {
  return (
    <div className="flex flex-col items-center py-1" aria-hidden="true">
      <span className="h-2.5 w-px bg-gradient-to-b from-violet/0 to-violet-bright/70" />
      <ArrowDown className="h-3 w-3 text-violet-bright" />
    </div>
  );
}

export function OfficerLoopCompact() {
  return (
    <div className="relative overflow-hidden rounded-xl border border-white/[0.07] bg-elevated/70 p-4">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: "radial-gradient(60% 50% at 50% 50%, hsl(var(--violet) / 0.18), transparent 70%)" }}
      />
      <div className="relative mx-auto max-w-sm">
        <div className="mx-auto w-fit">
          <Chip icon={ShieldAlert} label="Risk Radar" />
        </div>
        <Down />
        <div className="grid grid-cols-[1fr_auto_1.15fr_auto_1fr] items-stretch gap-1">
          <Chip icon={CalendarDays} label="Plan Drives" stack />
          <ArrowLeftRight className="h-3 w-3 self-center text-violet-bright" aria-hidden="true" />
          <Chip icon={ShieldCheck} label="Placement Officer" core stack />
          <ArrowLeftRight className="h-3 w-3 self-center text-violet-bright" aria-hidden="true" />
          <Chip icon={TrendingUp} label="Track Outcomes" stack />
        </div>
        <Down />
        <div className="mx-auto w-fit">
          <Chip icon={SlidersHorizontal} label="Optimize & Scale" />
        </div>
      </div>
    </div>
  );
}
