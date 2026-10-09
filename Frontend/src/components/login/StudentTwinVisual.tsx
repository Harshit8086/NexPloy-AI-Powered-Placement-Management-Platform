"use client";

import { useEffect, useId, useRef, useState } from "react";
import {
  motion,
  useMotionValue,
  useSpring,
  useTransform,
  type MotionValue,
} from "framer-motion";
import {
  BadgeCheck,
  Code2,
  Gauge,
  GraduationCap,
  Layers,
  ShieldCheck,
  Target,
  TrendingUp,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { useAnimatedNumber } from "@/hooks/useAnimatedNumber";
import { studentTwin as s } from "@/components/login/studentTwinData";
import { TWIN_H, TWIN_W, twinMesh } from "@/components/login/twinMesh";

/**
 * Student sign-in centerpiece — "Mini Placement Digital Twin".
 *
 *   profile rail  →  human wireframe digital twin  →  intelligence cards
 *
 * The twin is a procedural SVG mesh (see twinMesh.ts): a handful of <path>
 * elements, no WebGL, no extra dependency, and therefore no failure mode —
 * it is its own fallback. Only rendered by the student role's sign-in page.
 */

/* ------------------------------------------------------------------ */
/* Twin figure                                                         */
/* ------------------------------------------------------------------ */

function TwinFigure({
  energized,
  scanKey,
  materialized,
  animate,
  className,
}: {
  energized: boolean;
  scanKey: number;
  materialized: boolean;
  animate: boolean;
  className?: string;
}) {
  const uid = useId().replace(/:/g, "");
  const m = twinMesh;
  const BAND = 44;

  return (
    <svg
      viewBox={`0 0 ${TWIN_W} ${TWIN_H}`}
      className={className}
      role="img"
      aria-label="Abstract wireframe placement digital twin"
    >
      <defs>
        <radialGradient id={`${uid}-glow`} cx="50%" cy="30%" r="55%">
          <stop offset="0%" stopColor="#7C3AED" stopOpacity="0.5" />
          <stop offset="55%" stopColor="#6D28D9" stopOpacity="0.14" />
          <stop offset="100%" stopColor="#6D28D9" stopOpacity="0" />
        </radialGradient>
        {/* brightness hierarchy: face/head brightest → shoulders → lower bust */}
        <linearGradient id={`${uid}-lg`} gradientUnits="userSpaceOnUse" x1="0" y1="24" x2="0" y2="372">
          <stop offset="0" stopColor="#D4BFFF" />
          <stop offset="0.35" stopColor="#B08CFF" stopOpacity="0.85" />
          <stop offset="0.7" stopColor="#8B5CF6" stopOpacity="0.5" />
          <stop offset="1" stopColor="#7C3AED" stopOpacity="0.12" />
        </linearGradient>
        <linearGradient id={`${uid}-fade`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#fff" />
          <stop offset="0.5" stopColor="#fff" />
          <stop offset="0.86" stopColor="#000" />
        </linearGradient>
        <mask id={`${uid}-mask`} maskUnits="userSpaceOnUse" x="-40" y="-20" width={TWIN_W + 80} height={TWIN_H + 40}>
          <rect x="-40" y="-20" width={TWIN_W + 80} height={TWIN_H + 40} fill={`url(#${uid}-fade)`} />
        </mask>
        <clipPath id={`${uid}-band`}>
          <motion.rect
            key={scanKey}
            x={-20}
            width={TWIN_W + 40}
            height={BAND}
            initial={{ y: -BAND }}
            animate={{ y: scanKey > 0 && animate ? TWIN_H + BAND : -BAND }}
            transition={{ duration: 2.1, ease: "easeInOut" }}
          />
        </clipPath>
        <style>{`
          @keyframes twin-shimmer-${uid} { 0%,100% { opacity: .55 } 50% { opacity: 1 } }
          @keyframes twin-drift-${uid} { 0%,100% { transform: translateY(0) } 50% { transform: translateY(-5px) } }
        `}</style>
      </defs>

      {/* soft outer glow — stronger around head / shoulders */}
      <ellipse cx="150" cy="120" rx="150" ry="170" fill={`url(#${uid}-glow)`} opacity={energized ? 1 : 0.8} style={{ transition: "opacity .6s" }} />

      <g mask={`url(#${uid}-mask)`}>
        <motion.g
          initial={{ opacity: 0 }}
          animate={{ opacity: materialized ? 1 : 0 }}
          transition={{ duration: animate ? 0.9 : 0.2, ease: "easeOut" }}
        >
          {/* point cloud */}
          <g fill="#C4A6FF">
            {m.cloud.map((c, i) => (
              <circle
                key={i}
                cx={c.x}
                cy={c.y}
                r={c.r}
                opacity={0.5}
                style={animate ? { animation: `twin-shimmer-${uid} 4.5s ease-in-out ${c.d}s infinite` } : undefined}
              />
            ))}
          </g>

          {/* mesh */}
          <path d={m.outline} fill="none" stroke={`url(#${uid}-lg)`} strokeWidth="5" strokeLinejoin="round" style={{ opacity: energized ? 0.26 : 0.14, transition: "opacity .5s" }} />
          <path d={m.edges} fill="none" stroke={`url(#${uid}-lg)`} strokeWidth="0.7" style={{ opacity: energized ? 0.85 : 0.5, transition: "opacity .5s" }} />
          <path d={m.rimEdges} fill="none" stroke={`url(#${uid}-lg)`} strokeWidth="1" style={{ opacity: energized ? 1 : 0.75, transition: "opacity .5s" }} />
          <path d={m.outline} fill="none" stroke={`url(#${uid}-lg)`} strokeWidth="1.1" strokeLinejoin="round" style={{ opacity: energized ? 0.95 : 0.7, transition: "opacity .5s" }} />
          <path d={m.vertices} fill="none" stroke={`url(#${uid}-lg)`} strokeWidth="2" strokeLinecap="round" opacity="0.75" />
          <path
            d={m.brightVertices}
            fill="none"
            stroke="#EADFFF"
            strokeWidth="3"
            strokeLinecap="round"
            style={animate ? { animation: `twin-shimmer-${uid} 5s ease-in-out infinite` } : { opacity: 0.85 }}
          />

          {/* scan band — brighter copy of the mesh revealed through a moving clip */}
          <g clipPath={`url(#${uid}-band)`}>
            <path d={m.edges} fill="none" stroke="#D9C6FF" strokeWidth="0.9" opacity="0.75" />
            <path d={m.rimEdges} fill="none" stroke="#fff" strokeWidth="1.3" opacity="0.9" />
            <path d={m.vertices + m.brightVertices} fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" opacity="0.9" />
          </g>
        </motion.g>

        {/* halo particles */}
        {animate && (
          <g fill="#C4A6FF">
            {m.halo.map((h, i) => (
              <circle
                key={i}
                cx={h.x}
                cy={h.y}
                r="1.3"
                opacity="0.55"
                style={{ animation: `twin-drift-${uid} 7s ease-in-out ${h.d}s infinite, twin-shimmer-${uid} 6s ease-in-out ${h.d}s infinite` }}
              />
            ))}
          </g>
        )}
      </g>
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/* Profile rail                                                        */
/* ------------------------------------------------------------------ */

const railItems = [
  { icon: GraduationCap, value: s.cgpa.toFixed(2), label: "CGPA" },
  { icon: ShieldCheck, value: String(s.backlogs), label: "Backlogs" },
  { icon: Code2, value: s.skills, label: "Skills" },
  { icon: Layers, value: String(s.projects), label: "Projects" },
  { icon: BadgeCheck, value: String(s.certifications), label: "Certifications" },
];

function ProfileRail({ visible, active, animate }: { visible: boolean; active: boolean; animate: boolean }) {
  return (
    <motion.div
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: visible ? 1 : 0, x: visible ? 0 : -10 }}
      transition={{ duration: 0.5, ease: "easeOut" }}
    >
      <p className="text-[9px] font-medium uppercase tracking-[0.18em] text-muted-foreground/60">Student profile</p>
      <p className="mt-2 text-[15px] font-semibold leading-tight text-foreground">{s.name}</p>
      <p className="text-[11px] text-muted-foreground">
        {s.branchCode} • {s.graduationYear}
      </p>

      <div className="relative mt-3 pl-4">
        {/* intelligence line */}
        <span className="absolute bottom-2 left-[3px] top-1 w-px bg-gradient-to-b from-violet-bright/70 via-violet/40 to-violet/10" />
        {animate && active && (
          <motion.span
            key="rail-signal"
            className="absolute left-[1px] h-2 w-[5px] rounded-full bg-white/90 shadow-[0_0_8px_2px_hsl(var(--violet-bright)/0.9)]"
            initial={{ top: "0%", opacity: 0 }}
            animate={{ top: "92%", opacity: [0, 1, 1, 0] }}
            transition={{ duration: 0.9, ease: "easeInOut" }}
          />
        )}
        <ul className="space-y-[13px]">
          {railItems.map((it, i) => {
            const Icon = it.icon;
            return (
              <motion.li
                key={it.label}
                className="relative flex items-center gap-2"
                initial={{ opacity: 0 }}
                animate={{ opacity: visible ? 1 : 0 }}
                transition={{ delay: animate ? 0.08 * i : 0, duration: 0.4 }}
              >
                <span
                  className={cn(
                    "absolute -left-[16px] top-1/2 h-[7px] w-[7px] -translate-y-1/2 rounded-full bg-violet-bright shadow-[0_0_8px_1px_hsl(var(--violet-bright)/0.75)] transition-transform duration-500",
                    active && "scale-125",
                  )}
                />
                <Icon className="h-3 w-3 shrink-0 text-violet-bright/80" />
                <span className="text-[13px] font-semibold tabular-nums text-foreground">{it.value}</span>
                <span className="truncate text-[11px] text-muted-foreground">{it.label}</span>
              </motion.li>
            );
          })}
        </ul>
      </div>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/* Metric cards                                                        */
/* ------------------------------------------------------------------ */

function MetricCard({
  visible,
  active,
  hoverable = true,
  className,
  children,
}: {
  visible: boolean;
  active: boolean;
  hoverable?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: visible ? 1 : 0, y: visible ? 0 : 8 }}
      transition={{ duration: 0.4, ease: "easeOut" }}
    >
      <div
        className={cn(
          "rounded-[10px] border bg-card/70 px-3 py-2 backdrop-blur-sm transition-all duration-500",
          hoverable && "",
          active
            ? "border-violet/60 shadow-[0_0_22px_-6px_hsl(var(--violet)/0.7)]"
            : "border-white/[0.07] shadow-[0_4px_18px_-10px_hsl(var(--violet)/0.35)]",
          className,
        )}
      >
        {children}
      </div>
    </motion.div>
  );
}

function CardLabel({ icon: Icon, children }: { icon: typeof Gauge; children: React.ReactNode }) {
  return (
    <p className="flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
      <Icon className="h-3 w-3 text-violet-bright" />
      {children}
    </p>
  );
}

function ReadinessValue({ run }: { run: boolean }) {
  const n = useAnimatedNumber(s.readiness, 1000);
  return (
    <>
      <p className="mt-1 text-[22px] font-semibold leading-none tabular-nums text-foreground">
        {Math.round(run ? n : s.readiness)}
        <span className="text-[12px] font-normal text-muted-foreground"> / 100</span>
      </p>
      <div className="mt-2 h-[3px] overflow-hidden rounded-full bg-white/10">
        <div
          className="h-full rounded-full bg-gradient-to-r from-violet to-violet-bright"
          style={{ width: `${run ? n : s.readiness}%` }}
        />
      </div>
    </>
  );
}

function ProbabilityValue({ run }: { run: boolean }) {
  const n = useAnimatedNumber(s.probability, 1000);
  return (
    <p className="mt-1 flex items-baseline gap-2 text-[22px] font-semibold leading-none tabular-nums text-foreground">
      {Math.round(run ? n : s.probability)}%
      <span className="text-[10px] font-normal text-muted-foreground/70">Prototype estimate</span>
    </p>
  );
}

function MatchValue({ run }: { run: boolean }) {
  const n = useAnimatedNumber(s.topMatchScore, 1000);
  return (
    <div className="mt-1 flex items-end justify-between gap-2">
      <div className="min-w-0">
        <p className="truncate text-[13px] font-semibold text-foreground">{s.topMatchRole}</p>
        {s.topMatchCompany && <p className="text-[10px] text-muted-foreground">{s.topMatchCompany}</p>}
      </div>
      <p className="text-[20px] font-semibold leading-none tabular-nums text-violet-bright">
        {Math.round(run ? n : s.topMatchScore)}%
      </p>
    </div>
  );
}

function MetricStack({ stage, step, animate }: { stage: number; step: number; animate: boolean }) {
  const run = animate;
  return (
    <div className="space-y-2">
      <p className="text-[9px] font-medium uppercase tracking-[0.18em] text-muted-foreground/60">Intelligence</p>
      <MetricCard visible={stage >= 4} active={step === 3}>
        <CardLabel icon={Gauge}>Readiness</CardLabel>
        {stage >= 4 && <ReadinessValue run={run} />}
      </MetricCard>
      <MetricCard visible={stage >= 5} active={step === 4}>
        <CardLabel icon={TrendingUp}>Placement Probability</CardLabel>
        {stage >= 5 && <ProbabilityValue run={run} />}
      </MetricCard>
      <MetricCard visible={stage >= 6} active={step === 5}>
        <CardLabel icon={Target}>Top Match</CardLabel>
        {stage >= 6 && <MatchValue run={run} />}
      </MetricCard>
      <MetricCard visible={stage >= 7} active={step === 6}>
        <CardLabel icon={Layers}>Key Gaps</CardLabel>
        <div className="mt-1.5 flex flex-wrap gap-1">
          {s.gaps.map((g, i) => (
            <motion.span
              key={g}
              initial={{ opacity: 0 }}
              animate={{ opacity: stage >= 7 ? 1 : 0 }}
              transition={{ delay: animate ? 0.12 * i : 0, duration: 0.35 }}
              className="rounded-full border border-violet/30 bg-violet/10 px-2 py-0.5 text-[10px] text-violet-bright"
            >
              {g}
            </motion.span>
          ))}
        </div>
      </MetricCard>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Composition                                                         */
/* ------------------------------------------------------------------ */

const STAGE_MS = [400, 550, 750, 900, 1030, 1160, 1290]; // rail, twin, mesh, cards…
// Intelligence Pulse: 1 rail → 2 twin (+scan) → 3..6 cards, then rests.
const PULSE_STEPS: [number, number][] = [
  [1, 0],
  [2, 800],
  [3, 1800],
  [4, 2050],
  [5, 2300],
  [6, 2550],
  [0, 3300],
];
const PULSE_EVERY_MS = 10000;

function FlowDot({ show, id }: { show: boolean; id: string }) {
  if (!show) return null;
  return (
    <motion.span
      key={id}
      className="absolute top-1/2 h-[5px] w-[5px] -translate-y-1/2 rounded-full bg-white shadow-[0_0_10px_3px_hsl(var(--violet-bright)/0.9)]"
      initial={{ left: "0%", opacity: 0 }}
      animate={{ left: "100%", opacity: [0, 1, 1, 0] }}
      transition={{ duration: 0.7, ease: "easeInOut" }}
    />
  );
}

export function StudentTwinVisual() {
  const reduced = useReducedMotion();
  const animate = !reduced;
  const [stage, setStage] = useState(0);
  const [step, setStep] = useState(0);
  const [scanKey, setScanKey] = useState(0);
  const [hover, setHover] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  // Staged reveal
  useEffect(() => {
    if (reduced) {
      setStage(99);
      return;
    }
    const timers = STAGE_MS.map((ms, i) => setTimeout(() => setStage(i + 1), ms));
    return () => timers.forEach(clearTimeout);
  }, [reduced]);

  // Recurring Intelligence Pulse — starts once everything has revealed
  useEffect(() => {
    if (reduced) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = () => {
      timers = PULSE_STEPS.map(([st, ms]) =>
        setTimeout(() => {
          setStep(st);
          if (st === 2) setScanKey((k) => k + 1);
        }, ms),
      );
    };
    const first = setTimeout(run, 2600);
    const loop = setInterval(run, PULSE_EVERY_MS);
    return () => {
      clearTimeout(first);
      clearInterval(loop);
      timers.forEach(clearTimeout);
    };
  }, [reduced]);

  // Very small pointer parallax (desktop pointer only)
  const px = useMotionValue(0);
  const py = useMotionValue(0);
  const sx = useSpring(px, { stiffness: 60, damping: 18 });
  const sy = useSpring(py, { stiffness: 60, damping: 18 });
  const rotateY = useTransform(sx as MotionValue<number>, [-1, 1], [-4, 4]);
  const rotateX = useTransform(sy as MotionValue<number>, [-1, 1], [3, -3]);
  const shiftX = useTransform(sx as MotionValue<number>, [-1, 1], [-5, 5]);

  const onMove = (e: React.PointerEvent) => {
    if (reduced || e.pointerType !== "mouse" || !boxRef.current) return;
    const r = boxRef.current.getBoundingClientRect();
    px.set(((e.clientX - r.left) / r.width) * 2 - 1);
    py.set(((e.clientY - r.top) / r.height) * 2 - 1);
  };
  const onLeave = () => {
    px.set(0);
    py.set(0);
  };

  return (
    <div
      ref={boxRef}
      onPointerMove={onMove}
      onPointerLeave={onLeave}
      className="relative grid h-[400px] w-full grid-cols-[25fr_40fr_35fr] items-center gap-3"
    >
      {/* faint radial glow behind the twin */}
      <div
        className="pointer-events-none absolute left-[24%] top-[-6%] h-[112%] w-[46%]"
        style={{ background: "radial-gradient(closest-side, hsl(var(--violet) / 0.2), transparent 75%)" }}
      />

      {/* flow lines: rail → twin → cards */}
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute left-[18%] top-[46%] h-px w-[13%] bg-gradient-to-r from-violet/0 via-violet/35 to-violet/0">
          <FlowDot show={animate && step === 1} id={`a${scanKey}`} />
        </div>
        <div className="absolute left-[64%] top-[46%] h-px w-[6%] bg-gradient-to-r from-violet/0 via-violet/35 to-violet/0">
          <FlowDot show={animate && step === 3} id={`b${scanKey}`} />
        </div>
      </div>

      <div className="relative z-10 self-center">
        <ProfileRail visible={stage >= 1} active={step === 1} animate={animate} />
      </div>

      {/* Digital twin */}
      <div
        className="relative h-full min-w-0"
        onPointerEnter={() => setHover(true)}
        onPointerLeave={() => setHover(false)}
      >
        <p className="absolute left-0 right-0 top-0 text-center text-[9px] font-medium uppercase tracking-[0.18em] text-muted-foreground/60">
          Digital twin
        </p>
        <motion.div
          className="absolute inset-x-[-18%] bottom-6 top-4"
          style={animate ? { x: shiftX, rotateY, rotateX, transformPerspective: 900 } : undefined}
        >
          <motion.div
            className="h-full w-full"
            animate={animate ? { y: [0, -4, 0] } : undefined}
            transition={animate ? { duration: 8, repeat: Infinity, ease: "easeInOut" } : undefined}
          >
            <TwinFigure
              energized={step === 2 || hover}
              scanKey={scanKey}
              materialized={stage >= 3}
              animate={animate}
              className="h-full w-full overflow-visible"
            />
          </motion.div>
        </motion.div>
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: stage >= 3 ? (hover ? 1 : 0.65) : 0 }}
          transition={{ duration: 0.4 }}
          className="absolute bottom-0 left-0 right-0 text-center text-[9px] font-medium uppercase tracking-[0.2em] text-violet-bright"
        >
          Placement Digital Twin
        </motion.p>
      </div>

      <div className="relative z-10 self-center">
        <MetricStack stage={stage} step={step} animate={animate} />
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Compact version — tablet / mobile, shown above the sign-in form      */
/* ------------------------------------------------------------------ */

export function StudentTwinCompact() {
  const stats = [
    { label: "Readiness", value: String(s.readiness) },
    { label: "Probability", value: `${s.probability}%` },
    { label: "Top match", value: `${s.topMatchScore}%` },
  ];
  return (
    <div className="relative overflow-hidden rounded-xl border border-white/[0.07] bg-elevated/70 p-4">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: "radial-gradient(60% 80% at 25% 40%, hsl(var(--violet) / 0.2), transparent 70%)" }}
      />
      <div className="relative flex items-center gap-3">
        <TwinFigure
          energized={false}
          scanKey={0}
          materialized
          animate={false}
          className="h-[150px] w-[118px] shrink-0 overflow-visible sm:h-[180px] sm:w-[142px]"
        />
        <div className="min-w-0 flex-1">
          <p className="text-[15px] font-semibold text-foreground">{s.name}</p>
          <p className="text-[11px] text-muted-foreground">
            {s.branchCode} • {s.graduationYear} · {s.cgpa.toFixed(2)} CGPA
          </p>
          <dl className="mt-3 space-y-1.5">
            {stats.map((x) => (
              <div key={x.label} className="flex items-baseline justify-between border-b border-white/[0.06] pb-1">
                <dt className="text-[11px] text-muted-foreground">{x.label}</dt>
                <dd className="text-[15px] font-semibold tabular-nums text-foreground">{x.value}</dd>
              </div>
            ))}
          </dl>
          <div className="mt-2 flex flex-wrap gap-1">
            {s.gaps.map((g) => (
              <span key={g} className="rounded-full border border-violet/30 bg-violet/10 px-2 py-0.5 text-[10px] text-violet-bright">
                {g}
              </span>
            ))}
          </div>
        </div>
      </div>
      <p className="relative mt-2 text-[9px] uppercase tracking-[0.18em] text-muted-foreground/60">
        Placement digital twin · prototype estimate
      </p>
    </div>
  );
}
