"use client";

import { useEffect, useId, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowRight,
  BadgeCheck,
  BriefcaseBusiness,
  Gauge,
  Layers3,
  ShieldCheck,
  Users,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { DemoDataBadge } from "@/components/layout/DemoDataBadge";
import { showcaseStudent, showcaseTopMatch } from "@/components/landing/showcase-data";

/**
 * Landing hero centerpiece — "Placement Intelligence Core".
 *
 * Rahul's Placement Digital Twin sits at the centre of six intelligence
 * signals (Skills, Readiness, Risk, Opportunities, Recruiters, Offers).
 * Every figure comes from the illustrative showcase data (labelled "Demo
 * Data" in the UI). SVG + CSS only;
 * geometry lives in a 600×520 space and HTML is placed by percentage.
 */

const W = 600;
const H = 520;
const CX = 300;
const CY = 262;
const CORE_R = 62;

const pctX = (x: number) => `${(x / W) * 100}%`;
const pctY = (y: number) => `${(y / H) * 100}%`;

// ---- illustrative showcase data (labelled "Demo Data" in the UI) ----
const TOP = {
  company: showcaseTopMatch.company,
  role: showcaseTopMatch.role,
  fit: showcaseTopMatch.fit,
};
const COURSE = `B.Tech ${showcaseStudent.branchCode}`;

type Node = "twin" | "skills" | "readiness" | "risk" | "opps" | "recruiters" | "offers";
type Hover = Node | null;

const NODE_POS: Record<Exclude<Node, "twin">, { x: number; y: number; r: number }> = {
  skills: { x: 300, y: 50, r: 22 },
  readiness: { x: 105, y: 128, r: 22 },
  risk: { x: 70, y: 330, r: 20 },
  opps: { x: 495, y: 108, r: 22 },
  recruiters: { x: 548, y: 262, r: 20 },
  offers: { x: 470, y: 405, r: 22 },
};

/** Gently curved line from the core edge to a node edge. */
function edge(to: { x: number; y: number; r: number }, bend = 0.12) {
  const dx = to.x - CX;
  const dy = to.y - CY;
  const len = Math.hypot(dx, dy);
  const ux = dx / len;
  const uy = dy / len;
  const sx = CX + ux * (CORE_R + 8);
  const sy = CY + uy * (CORE_R + 8);
  const ex = to.x - ux * (to.r + 4);
  const ey = to.y - uy * (to.r + 4);
  const mx = (sx + ex) / 2 - uy * len * bend;
  const my = (sy + ey) / 2 + ux * len * bend;
  return `M${sx.toFixed(1)} ${sy.toFixed(1)} Q${mx.toFixed(1)} ${my.toFixed(1)} ${ex.toFixed(1)} ${ey.toFixed(1)}`;
}

function chord(a: { x: number; y: number; r: number }, b: { x: number; y: number; r: number }, bulge: number) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len = Math.hypot(dx, dy);
  const ux = dx / len;
  const uy = dy / len;
  const sx = a.x + ux * (a.r + 4);
  const sy = a.y + uy * (a.r + 4);
  const ex = b.x - ux * (b.r + 4);
  const ey = b.y - uy * (b.r + 4);
  return `M${sx.toFixed(1)} ${sy.toFixed(1)} Q${((sx + ex) / 2 + bulge).toFixed(1)} ${((sy + ey) / 2).toFixed(1)} ${ex.toFixed(1)} ${ey.toFixed(1)}`;
}

type Line = "skills" | "readiness" | "risk" | "opps" | "recruiters" | "offers";
const P: Record<Line, string> = {
  skills: edge(NODE_POS.skills, 0),
  readiness: edge(NODE_POS.readiness, 0.1),
  risk: edge(NODE_POS.risk, -0.1),
  opps: edge(NODE_POS.opps, -0.1),
  // outcome chain along the right side: opportunities → recruiters → offers
  recruiters: chord(NODE_POS.opps, NODE_POS.recruiters, 90),
  offers: chord(NODE_POS.recruiters, NODE_POS.offers, 60),
};
const LINES = Object.keys(P) as Line[];

// One Intelligence Pulse cycle (ms → responding node); `card` = TechNova card.
const SEQ: [number, Node | "card" | null][] = [
  [0, "twin"],
  [450, "skills"],
  [900, "readiness"],
  [1350, "risk"],
  [1800, "opps"],
  [2300, "card"],
  [2900, "recruiters"],
  [3500, "offers"],
  [4400, null],
];
const COMET: Record<Line, [number, number]> = {
  skills: [0, 550],
  readiness: [450, 550],
  risk: [900, 550],
  opps: [1350, 550],
  recruiters: [2400, 550],
  offers: [3000, 550],
};
const LOOP_MS = 12000;

function Comet({ d, delay, duration }: { d: string; delay: number; duration: number }) {
  return (
    <motion.path
      d={d}
      pathLength={1}
      fill="none"
      stroke="#F3EBFF"
      strokeWidth={2.4}
      strokeLinecap="round"
      strokeDasharray="0.06 2"
      initial={{ strokeDashoffset: 0.06, opacity: 0 }}
      animate={{ strokeDashoffset: -1, opacity: [0, 1, 1, 0] }}
      transition={{ duration: duration / 1000, delay: delay / 1000, ease: "linear", times: [0, 0.05, 0.95, 1] }}
      style={{ filter: "drop-shadow(0 0 4px hsl(var(--violet-bright)))" }}
    />
  );
}

const T_LABEL = "text-[clamp(8px,1.6cqw,10px)] uppercase tracking-[0.12em] text-muted-foreground";

function SignalNode({
  icon: Icon,
  label,
  sub,
  lit,
  pos,
  side = "below",
  visible,
  onEnter,
  onLeave,
  delay,
  animate,
}: {
  icon: LucideIcon;
  label: string;
  sub: string;
  lit: boolean;
  pos: { x: number; y: number; r: number };
  side?: "below" | "right" | "left" | "above";
  visible: boolean;
  onEnter: (e: React.PointerEvent) => void;
  onLeave: () => void;
  delay: number;
  animate: boolean;
}) {
  const size = pos.r * 2;
  const labelCls =
    side === "below"
      ? "left-1/2 top-full mt-1.5 -translate-x-1/2 items-center text-center"
      : side === "above"
        ? "bottom-full left-1/2 mb-1.5 -translate-x-1/2 items-center text-center"
        : side === "right"
          ? "left-full top-1/2 ml-2.5 -translate-y-1/2 items-start text-left"
          : "right-full top-1/2 mr-2.5 -translate-y-1/2 items-end text-right";
  return (
    <motion.div
      onPointerEnter={onEnter}
      onPointerLeave={onLeave}
      initial={{ opacity: 0, scale: animate ? 0.92 : 1 }}
      animate={{ opacity: visible ? 1 : 0, scale: visible ? 1 : animate ? 0.92 : 1 }}
      transition={{ duration: 0.4, delay: animate ? delay : 0 }}
      className="absolute"
      style={{ left: pctX(pos.x - pos.r), top: pctY(pos.y - pos.r), width: pctX(size), height: pctY(size) }}
    >
      <span
        className={cn(
          "flex h-full w-full items-center justify-center rounded-full border transition-all duration-500",
          lit
            ? "border-violet-bright/80 bg-violet/25 shadow-[0_0_22px_-2px_hsl(var(--violet)/0.85)]"
            : "border-violet/40 bg-card/85 shadow-[0_0_14px_-5px_hsl(var(--violet)/0.6)]",
        )}
        style={{ aspectRatio: "1 / 1" }}
      >
        <Icon className="h-[46%] w-[46%] text-violet-bright" aria-hidden="true" />
      </span>
      <span className={cn("pointer-events-none absolute flex flex-col whitespace-nowrap leading-tight", labelCls)}>
        <span className={cn("text-[clamp(9px,2cqw,12px)] font-semibold transition-colors", lit ? "text-foreground" : "text-foreground/90")}>
          {label}
        </span>
        <span className="text-[clamp(7.5px,1.55cqw,9.5px)] text-muted-foreground">{sub}</span>
      </span>
    </motion.div>
  );
}

const cardBase = "rounded-[10px] border bg-card transition-all duration-300";
const cardIdle = "border-white/[0.08] shadow-[0_0_18px_-10px_hsl(var(--violet)/0.6)]";
const cardLit = "border-violet-bright/60 shadow-[0_0_24px_-6px_hsl(var(--violet)/0.75)]";

function useInView() {
  const ref = useRef<HTMLElement | null>(null);
  const [inView, setInView] = useState(true);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === "undefined") return;
    const io = new IntersectionObserver(([e]) => setInView(e.isIntersecting), { threshold: 0.1 });
    io.observe(el);
    return () => io.disconnect();
  }, []);
  return [ref, inView] as const;
}

export function PlacementIntelligenceCore() {
  const uid = useId().replace(/:/g, "");
  const reduced = useReducedMotion();
  const animate = !reduced;
  const [ref, inView] = useInView();
  const [stage, setStage] = useState(0);
  const [lit, setLit] = useState<Node | "card" | null>(null);
  const [cycle, setCycle] = useState(0);
  const [hover, setHover] = useState<Hover>(null);
  const [depth, setDepth] = useState({ x: 0, y: 0 });

  useEffect(() => {
    if (reduced) {
      setStage(99);
      return;
    }
    // 1 core · 2 rings · 3 skills/readiness/risk · 4 opps/recruiters/offers · 5 cards+lines
    const ms = [200, 450, 700, 950, 1200];
    const t = ms.map((m, i) => setTimeout(() => setStage(i + 1), m));
    return () => t.forEach(clearTimeout);
  }, [reduced]);

  const running = animate && inView && stage >= 5;
  useEffect(() => {
    if (!running) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = () => {
      setCycle((c) => c + 1);
      timers = SEQ.map(([ms, n]) => setTimeout(() => setLit(n), ms));
    };
    const first = setTimeout(run, 300);
    const loop = setInterval(run, LOOP_MS);
    return () => {
      clearTimeout(first);
      clearInterval(loop);
      timers.forEach(clearTimeout);
      setLit(null);
    };
  }, [running]);

  const enter = (h: Hover) => (e: React.PointerEvent) => {
    if (e.pointerType === "mouse") setHover(h);
  };
  const leave = () => setHover(null);

  const onMove = (e: React.PointerEvent<HTMLElement>) => {
    if (!animate || e.pointerType !== "mouse") return;
    const r = e.currentTarget.getBoundingClientRect();
    setDepth({ x: ((e.clientX - r.left) / r.width - 0.5) * 2, y: ((e.clientY - r.top) / r.height - 0.5) * 2 });
  };

  const on = (n: Node) => lit === n || hover === n;
  const twinHover = hover === "twin";
  const cardOpps = lit === "card" || hover === "opps";
  // hovered node lights its own line; the twin lights all; recruiters/offers light the outcome chain
  const lineOn = (l: Line) =>
    twinHover ||
    hover === l ||
    lit === l ||
    (l === "opps" && cardOpps) ||
    ((hover === "recruiters" || hover === "offers") && (l === "opps" || l === "recruiters" || (l === "offers" && hover === "offers")));
  const lineVisible = stage >= 5;
  const nodeVisible = (n: Node) => (n === "skills" || n === "readiness" || n === "risk" ? stage >= 3 : stage >= 4);
  const par = (k: number) => ({ transform: `translate3d(${depth.x * k}px, ${depth.y * k}px, 0)` });

  return (
    <figure
      ref={ref}
      onPointerMove={onMove}
      onPointerLeave={() => setDepth({ x: 0, y: 0 })}
      className="relative m-0 w-full select-none [container-type:inline-size]"
      style={{ aspectRatio: `${W} / ${H}` }}
      aria-label="NEXPLOY Placement Intelligence Core: Rahul Sharma's Placement Digital Twin connects skills, readiness, risk, opportunities, recruiters and offers."
    >
      {/* atmosphere */}
      <div
        className="pointer-events-none absolute transition-transform duration-500 ease-out"
        style={{
          left: pctX(40),
          top: pctY(60),
          width: pctX(520),
          height: pctY(420),
          background: "radial-gradient(closest-side, hsl(var(--violet) / 0.2), transparent 80%)",
          ...par(4),
        }}
      />

      <svg viewBox={`0 0 ${W} ${H}`} className="absolute inset-0 h-full w-full overflow-visible" aria-hidden="true" focusable="false">
        <defs>
          <radialGradient id={`${uid}-c`} cx="50%" cy="38%" r="70%">
            <stop offset="0" stopColor="#7C3AED" stopOpacity="0.42" />
            <stop offset="1" stopColor="#0E0E14" stopOpacity="1" />
          </radialGradient>
        </defs>

        {/* faint intelligence orbit + ring */}
        <g className="transition-transform duration-500 ease-out" style={par(1.5)}>
          <motion.ellipse
            cx={CX}
            cy={CY}
            rx={240}
            ry={200}
            fill="none"
            stroke="#8B5CF6"
            strokeWidth={0.8}
            strokeDasharray="2 6"
            initial={{ opacity: 0 }}
            animate={{ opacity: stage >= 2 ? 0.28 : 0 }}
            transition={{ duration: 0.8 }}
          />
          <motion.circle
            cx={CX}
            cy={CY}
            r={CORE_R + 34}
            fill="none"
            stroke="#8B5CF6"
            strokeWidth={0.8}
            initial={{ opacity: 0 }}
            animate={{ opacity: stage >= 2 ? 0.22 : 0 }}
            transition={{ duration: 0.8 }}
          />
          <motion.circle
            cx={CX}
            cy={CY}
            r={CORE_R + 16}
            fill="none"
            stroke="#8B5CF6"
            strokeWidth={0.8}
            initial={{ opacity: 0 }}
            animate={{ opacity: stage >= 2 ? 0.35 : 0 }}
            transition={{ duration: 0.8, delay: 0.1 }}
          />
        </g>

        {/* connections */}
        {LINES.map((l) => (
          <motion.path
            key={l}
            d={P[l]}
            fill="none"
            stroke="#A57BFF"
            strokeWidth={lineOn(l) ? 1.6 : 1.1}
            strokeLinecap="round"
            initial={{ pathLength: animate ? 0 : 1, opacity: 0 }}
            animate={{
              pathLength: lineVisible ? 1 : animate ? 0 : 1,
              opacity: lineVisible ? (lineOn(l) ? 0.95 : 0.36) : 0,
            }}
            transition={{ pathLength: { duration: 0.8, ease: "easeOut" }, opacity: { duration: 0.35 } }}
          />
        ))}

        {/* Intelligence Pulse */}
        {running && LINES.map((l) => <Comet key={`${l}${cycle}`} d={P[l]} delay={COMET[l][0]} duration={COMET[l][1]} />)}
      </svg>

      {/* Header */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 1 ? 1 : 0 }}
        transition={{ duration: 0.5 }}
        className="pointer-events-none absolute left-0 top-0 hidden sm:block"
      >
        <p className="text-[clamp(8px,1.7cqw,10px)] font-semibold uppercase tracking-[0.16em] text-violet-bright">
          NEXPLOY Intelligence Core
        </p>
        <p className="mt-0.5 text-[clamp(8px,1.7cqw,10.5px)] text-muted-foreground">Turning potential into real opportunities</p>
      </motion.div>
      <div className="absolute right-0 top-0">
        <DemoDataBadge />
      </div>

      {/* Digital Twin core */}
      <motion.div
        onPointerEnter={enter("twin")}
        onPointerLeave={leave}
        initial={{ opacity: 0, scale: animate ? 0.94 : 1 }}
        animate={{ opacity: stage >= 1 ? 1 : 0, scale: stage >= 1 ? 1 : animate ? 0.94 : 1 }}
        transition={{ duration: 0.55, ease: "easeOut" }}
        className="absolute"
        style={{ left: pctX(CX - CORE_R), top: pctY(CY - CORE_R), width: pctX(CORE_R * 2), height: pctY(CORE_R * 2) }}
      >
        {animate && stage >= 2 && (
          <motion.div
            className="pointer-events-none absolute -inset-5 rounded-full"
            style={{ background: "radial-gradient(closest-side, hsl(var(--violet) / 0.35), transparent 75%)" }}
            animate={{ opacity: [0.45, 0.85, 0.45] }}
            transition={{ duration: 6, repeat: Infinity, ease: "easeInOut" }}
          />
        )}
        {animate && lit === "twin" && (
          <motion.div
            key={`ring${cycle}`}
            className="absolute inset-0 rounded-full border border-violet-bright/70"
            initial={{ scale: 1, opacity: 0.8 }}
            animate={{ scale: 1.14, opacity: 0 }}
            transition={{ duration: 0.9, ease: "easeOut" }}
          />
        )}
        <div
          className={cn(
            "relative flex h-full w-full flex-col items-center justify-center overflow-hidden rounded-full border-2 transition-shadow duration-500",
            on("twin")
              ? "border-violet-bright/85 shadow-[0_0_54px_-2px_hsl(var(--violet)/0.85)]"
              : "border-violet/70 shadow-[0_0_38px_-4px_hsl(var(--violet)/0.6)]",
          )}
          style={{ background: `radial-gradient(circle at 50% 36%, hsl(var(--violet) / 0.4), hsl(var(--card)) 72%)` }}
        >
          {/* abstract student silhouette — no external image */}
          <svg viewBox="0 0 100 100" className="h-[62%] w-[62%]" aria-hidden="true" focusable="false">
            <circle cx="50" cy="36" r="15" fill="#C4A6FF" opacity="0.9" />
            <path d="M18 88 C18 64 32 54 50 54 C68 54 82 64 82 88 Z" fill="#9D5CFF" opacity="0.75" />
          </svg>
          <span className="absolute bottom-[9%] text-[clamp(6px,1.2cqw,8px)] font-semibold uppercase tracking-[0.16em] text-violet-bright">
            Digital Twin
          </span>
        </div>
        <div className="pointer-events-none absolute left-1/2 top-full mt-2.5 -translate-x-1/2 whitespace-nowrap text-center leading-tight">
          <p className="text-[clamp(10px,2.4cqw,14px)] font-semibold text-foreground">{showcaseStudent.name}</p>
          <p className="text-[clamp(8px,1.7cqw,10.5px)] text-muted-foreground">{COURSE}</p>
        </div>
        <span
          className={cn(
            "pointer-events-none absolute bottom-full left-1/2 mb-2 -translate-x-1/2 whitespace-nowrap rounded-full border border-violet/30 bg-card px-2.5 py-0.5 text-[8px] font-medium uppercase tracking-[0.2em] text-violet-bright transition-opacity duration-300",
            twinHover ? "opacity-100" : "opacity-0",
          )}
        >
          Placement Intelligence Profile
        </span>
      </motion.div>

      {/* Signal nodes */}
      <SignalNode
        icon={Layers3}
        label="Skills"
        sub="Strengths & Gaps"
        lit={on("skills")}
        pos={NODE_POS.skills}
        side="right"
        visible={nodeVisible("skills")}
        onEnter={enter("skills")}
        onLeave={leave}
        delay={0}
        animate={animate}
      />
      <SignalNode
        icon={Gauge}
        label="Readiness"
        sub="Placement Ready"
        lit={on("readiness")}
        pos={NODE_POS.readiness}
        side="right"
        visible={nodeVisible("readiness")}
        onEnter={enter("readiness")}
        onLeave={leave}
        delay={0.08}
        animate={animate}
      />
      <SignalNode
        icon={ShieldCheck}
        label="Risk"
        sub="Early Detection"
        lit={on("risk")}
        pos={NODE_POS.risk}
        side="right"
        visible={nodeVisible("risk")}
        onEnter={enter("risk")}
        onLeave={leave}
        delay={0.16}
        animate={animate}
      />
      <SignalNode
        icon={BriefcaseBusiness}
        label="Opportunities"
        sub="Drives & Job Matches"
        lit={on("opps")}
        pos={NODE_POS.opps}
        side="left"
        visible={nodeVisible("opps")}
        onEnter={enter("opps")}
        onLeave={leave}
        delay={0}
        animate={animate}
      />
      <SignalNode
        icon={Users}
        label="Recruiters"
        sub="Real Opportunities"
        lit={on("recruiters")}
        pos={NODE_POS.recruiters}
        side="below"
        visible={nodeVisible("recruiters")}
        onEnter={enter("recruiters")}
        onLeave={leave}
        delay={0.08}
        animate={animate}
      />
      <SignalNode
        icon={BadgeCheck}
        label="Offers"
        sub="Career Outcomes"
        lit={on("offers")}
        pos={NODE_POS.offers}
        side="right"
        visible={nodeVisible("offers")}
        onEnter={enter("offers")}
        onLeave={leave}
        delay={0.16}
        animate={animate}
      />

      {/* Readiness card */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 5 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute transition-transform duration-500 ease-out"
        style={{ left: pctX(22), top: pctY(186), width: pctX(150), height: pctY(70), ...par(3) }}
      >
        <div className={cn("flex h-full items-center gap-[7%] px-[7%]", cardBase, on("readiness") ? cardLit : cardIdle)}>
          <div className="w-[34%] shrink-0 [&_*]:!text-[10px]">
            <ScoreRing value={showcaseStudent.readiness} size={44} strokeWidth={5} />
          </div>
          <div className="leading-tight">
            <p className="text-[clamp(10px,2.3cqw,14px)] font-semibold tabular-nums text-foreground">
              {showcaseStudent.readiness} <span className="text-muted-foreground">/ 100</span>
            </p>
            <p className={T_LABEL}>Readiness</p>
            <p className="text-[clamp(8px,1.6cqw,10px)] font-medium text-success">{showcaseStudent.riskReason}</p>
          </div>
        </div>
      </motion.div>

      {/* Risk state */}
      <motion.span
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 5 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className={cn(
          "absolute rounded-full border px-2 py-[1px] text-[clamp(7px,1.5cqw,9px)] font-semibold uppercase tracking-wider transition-all duration-300",
          on("risk") ? "border-success/60 bg-success/15 text-success" : "border-success/30 bg-success/10 text-success/90",
        )}
        style={{ left: pctX(46), top: pctY(362) }}
      >
        Risk: {showcaseStudent.riskLevel}
      </motion.span>

      {/* Top Opportunity card */}
      <motion.div
        onPointerEnter={enter("opps")}
        onPointerLeave={leave}
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 5 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute transition-transform duration-500 ease-out"
        style={{ left: pctX(386), top: pctY(156), width: pctX(160), height: pctY(76), ...par(3) }}
      >
        <div className={cn("flex h-full flex-col justify-center px-[7%]", cardBase, cardOpps ? cardLit : "border-violet/30 shadow-[0_0_18px_-8px_hsl(var(--violet)/0.7)]")}>
          <p className={T_LABEL}>Top Opportunity</p>
          <div className="mt-[2%] flex items-baseline justify-between gap-2">
            <p className="text-[clamp(10px,2.3cqw,14px)] font-semibold text-foreground">{TOP.company}</p>
            <p className="text-[clamp(10px,2.3cqw,14px)] font-semibold tabular-nums text-violet-bright">{TOP.fit}% Match</p>
          </div>
          <p className="text-[clamp(8px,1.7cqw,10.5px)] text-muted-foreground">{TOP.role}</p>
        </div>
      </motion.div>

      {/* Placement probability card */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 5 ? 1 : 0 }}
        transition={{ duration: 0.4, delay: animate ? 0.1 : 0 }}
        className="absolute transition-transform duration-500 ease-out"
        style={{ left: pctX(196), top: pctY(430), width: pctX(208), height: pctY(64), ...par(3) }}
      >
        <div className={cn("flex h-full items-center justify-between px-[6%]", cardBase, cardIdle)}>
          <div className="leading-tight">
            <p className={T_LABEL}>Placement Probability</p>
            <p className="text-[clamp(7.5px,1.5cqw,9.5px)] text-muted-foreground">Prototype estimate</p>
          </div>
          <p className="text-[clamp(16px,4.2cqw,26px)] font-semibold tabular-nums text-violet-bright">
            {showcaseStudent.placementProbability}%
          </p>
        </div>
      </motion.div>
    </figure>
  );
}

/* ------------------------------------------------------------------ */
/* Compact version — tablet / mobile                                    */
/* ------------------------------------------------------------------ */

export function PlacementIntelligenceCompact() {
  return (
    <div className="relative overflow-hidden rounded-2xl border border-white/[0.07] bg-elevated/60 p-5">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: "radial-gradient(60% 55% at 50% 30%, hsl(var(--violet) / 0.22), transparent 70%)" }}
      />
      <div className="relative mx-auto flex max-w-sm flex-col items-center text-center">
        <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-violet-bright">NEXPLOY Intelligence Core</p>
        <span className="mt-4 flex h-24 w-24 items-center justify-center rounded-full border-2 border-violet/70 bg-card shadow-[0_0_38px_-4px_hsl(var(--violet)/0.6)]">
          <svg viewBox="0 0 100 100" className="h-14 w-14" aria-hidden="true" focusable="false">
            <circle cx="50" cy="36" r="15" fill="#C4A6FF" opacity="0.9" />
            <path d="M18 88 C18 64 32 54 50 54 C68 54 82 64 82 88 Z" fill="#9D5CFF" opacity="0.75" />
          </svg>
        </span>
        <p className="mt-3 text-sm font-semibold text-foreground">{showcaseStudent.name}</p>
        <p className="text-[11px] text-muted-foreground">
          {COURSE} · <span className="uppercase tracking-wider text-violet-bright">Digital Twin</span>
        </p>

        <div className="mt-4 grid w-full grid-cols-2 gap-2">
          <div className="rounded-lg border border-white/[0.08] bg-card/80 px-3 py-2 text-left">
            <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Readiness</p>
            <p className="text-lg font-semibold tabular-nums">
              {showcaseStudent.readiness}
              <span className="text-xs text-muted-foreground"> / 100</span>
            </p>
          </div>
          <div className="rounded-lg border border-white/[0.08] bg-card/80 px-3 py-2 text-left">
            <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Probability</p>
            <p className="text-lg font-semibold tabular-nums text-violet-bright">{showcaseStudent.placementProbability}%</p>
          </div>
        </div>
        <div className="mt-2 flex w-full items-center justify-between rounded-lg border border-violet/30 bg-violet/5 px-3 py-2 text-left">
          <div>
            <p className="text-[10px] uppercase tracking-wider text-muted-foreground">Top Opportunity</p>
            <p className="text-sm font-semibold">{TOP.company}</p>
            <p className="text-[11px] text-muted-foreground">{TOP.role}</p>
          </div>
          <p className="text-sm font-semibold tabular-nums text-violet-bright">{TOP.fit}% Match</p>
        </div>

        <div className="mt-4 flex items-center gap-2 text-[11px] font-medium text-foreground/85" aria-label="Skills to opportunity to offer">
          <span>Skills</span>
          <ArrowRight className="h-3 w-3 text-violet-bright" aria-hidden="true" />
          <span>Opportunity</span>
          <ArrowRight className="h-3 w-3 text-violet-bright" aria-hidden="true" />
          <span>Offer</span>
        </div>
      </div>
    </div>
  );
}
