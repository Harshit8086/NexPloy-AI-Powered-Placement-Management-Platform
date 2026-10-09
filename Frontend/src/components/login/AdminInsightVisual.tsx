"use client";

import { useEffect, useId, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowDown,
  Building2,
  GraduationCap,
  Landmark,
  Settings,
  ShieldAlert,
  ShieldCheck,
  TrendingUp,
  UserCog,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { useAnimatedNumber } from "@/hooks/useAnimatedNumber";
import { adminShowcase } from "@/components/landing/showcase-data";

/**
 * Super Admin sign-in centerpiece — "Ecosystem Insight Dashboard".
 *
 * Scale (3 KPIs) → Intelligence (Performance Trends + Risk Alerts) →
 * Super Admin core → Governance controls (people / policy / platform).
 * All figures derive from the centralized mock data (demo data), so they
 * never contradict the Super Admin dashboard. Only the Intelligence Pulse
 * moves after the entrance.
 *
 * SVG for lines/charts + positioned HTML for text; geometry lives in a
 * 560×400 space and HTML is placed by percentage so everything scales.
 */

const W = 560;
const H = 400;
const CX = 280;
const CY = 250;
const CORE_R = 42;

const pctX = (x: number) => `${(x / W) * 100}%`;
const pctY = (y: number) => `${(y / H) * 100}%`;

// ---- illustrative showcase data (labelled "Demo Data" in the UI) ----
const KPIS = {
  campuses: adminShowcase.campuses,
  students: adminShowcase.students,
  rate: adminShowcase.placementRate,
};

// Cumulative offers across all campuses, month by month → an upward trend.
const TREND = adminShowcase.trend;

// Risk distribution summed across campuses (Low / Medium / High).
const RISK = adminShowcase.risk;

const CH_W = 100;
const CH_H = 34;
const trendPoints = (() => {
  const min = TREND[0] * 0.6;
  const max = TREND[TREND.length - 1];
  return TREND.map((v, i) => [
    2 + (i / (TREND.length - 1)) * (CH_W - 4),
    CH_H - 3 - ((v - min) / (max - min)) * (CH_H - 8),
  ]);
})();
const trendLine = trendPoints.map(([x, y], i) => `${i ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`).join(" ");
const trendArea = `${trendLine} L${trendPoints[trendPoints.length - 1][0]} ${CH_H} L${trendPoints[0][0]} ${CH_H} Z`;
const riskMax = Math.max(...RISK.map((r) => r.count));

// ---- geometry ----
type Node = "campuses" | "students" | "rate" | "perf" | "risk" | "core" | "users" | "policies" | "settings";
type Hover = Node | null;

const KPI_X = { campuses: 84, students: 280, rate: 476 } as const;
const CTRL_X = { users: 100, policies: 280, settings: 460 } as const;

const P = {
  campuses: `M${KPI_X.campuses} 58 L${KPI_X.campuses} 84 L${CX} 84 L${CX} ${CY - CORE_R - 4}`,
  students: `M${KPI_X.students} 58 L${CX} ${CY - CORE_R - 4}`,
  rate: `M${KPI_X.rate} 58 L${KPI_X.rate} 84 L${CX} 84 L${CX} ${CY - CORE_R - 4}`,
  perf: `M214 170 C 240 170 226 ${CY} ${CX - CORE_R - 4} ${CY}`,
  risk: `M346 170 C 320 170 334 ${CY} ${CX + CORE_R + 4} ${CY}`,
  users: `M${CX} ${CY + CORE_R + 4} C ${CX} 330 ${CTRL_X.users} 314 ${CTRL_X.users} 342`,
  policies: `M${CX} ${CY + CORE_R + 4} L${CX} 342`,
  settings: `M${CX} ${CY + CORE_R + 4} C ${CX} 330 ${CTRL_X.settings} 314 ${CTRL_X.settings} 342`,
} as const;
type Line = keyof typeof P;
const LINES = Object.keys(P) as Line[];

// One Intelligence Pulse cycle: ms → which node is responding.
const SEQ: [number, Node | null][] = [
  [0, "campuses"],
  [450, "students"],
  [900, "rate"],
  [1500, "perf"],
  [1900, "risk"],
  [2600, "core"],
  [3300, "users"],
  [3300, "policies"],
  [3300, "settings"],
  [4300, null],
];
const LOOP_MS = 9500;
// comet start delay + duration (ms) per line inside a cycle
const COMET: Record<Line, [number, number]> = {
  campuses: [0, 1100],
  students: [450, 800],
  rate: [900, 1100],
  perf: [1500, 900],
  risk: [1900, 900],
  users: [2700, 900],
  policies: [2700, 700],
  settings: [2700, 900],
};

function Comet({ d, delay, duration }: { d: string; delay: number; duration: number }) {
  return (
    <motion.path
      d={d}
      pathLength={1}
      fill="none"
      stroke="#F3EBFF"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeDasharray="0.05 2"
      initial={{ strokeDashoffset: 0.05, opacity: 0 }}
      animate={{ strokeDashoffset: -1, opacity: [0, 1, 1, 0] }}
      transition={{ duration: duration / 1000, delay: delay / 1000, ease: "linear", times: [0, 0.05, 0.95, 1] }}
      style={{ filter: "drop-shadow(0 0 4px hsl(var(--violet-bright)))" }}
    />
  );
}

function IconChip({ icon: Icon, lit, size = 22 }: { icon: LucideIcon; lit: boolean; size?: number }) {
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full border transition-all duration-500",
        lit ? "border-violet-bright/80 bg-violet/25" : "border-violet/35 bg-violet/10",
      )}
      style={{ width: size, height: size }}
    >
      <Icon className="h-[52%] w-[52%] text-violet-bright" aria-hidden="true" />
    </span>
  );
}

function KpiValue({ value, suffix = "" }: { value: number; suffix?: string }) {
  const v = useAnimatedNumber(value, 900);
  return (
    <>
      {Math.round(v).toLocaleString("en-US")}
      {suffix}
    </>
  );
}

const cardBase = "border bg-card/80 transition-all duration-300";
const cardIdle = "border-white/[0.08] shadow-[0_0_18px_-10px_hsl(var(--violet)/0.6)]";
const cardLit = "border-violet-bright/60 shadow-[0_0_22px_-6px_hsl(var(--violet)/0.75)]";
const T_LABEL = "text-[clamp(8px,1.6cqw,10px)] font-medium uppercase tracking-[0.12em] text-muted-foreground";

export function AdminInsightVisual() {
  const uid = useId().replace(/:/g, "");
  const reduced = useReducedMotion();
  const animate = !reduced;
  const [stage, setStage] = useState(0);
  const [lit, setLit] = useState<Set<Node>>(new Set());
  const [cycle, setCycle] = useState(0);
  const [hover, setHover] = useState<Hover>(null);

  useEffect(() => {
    if (reduced) {
      setStage(99);
      return;
    }
    // 1 kpis · 2 values · 3 perf · 4 risk · 5 core+lines · 6 controls
    const ms = [120, 500, 750, 900, 1050, 1350];
    const t = ms.map((m, i) => setTimeout(() => setStage(i + 1), m));
    return () => t.forEach(clearTimeout);
  }, [reduced]);

  useEffect(() => {
    if (reduced) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = () => {
      setCycle((c) => c + 1);
      timers = SEQ.map(([ms, n]) =>
        setTimeout(() => {
          setLit((prev) => {
            if (n === null) return new Set();
            // the three controls light together; every other step replaces the last
            const keep = n === "users" || n === "policies" || n === "settings";
            const next = new Set<Node>(keep ? prev : []);
            next.add(n);
            return next;
          });
        }, ms),
      );
    };
    const first = setTimeout(run, 2500);
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

  const on = (n: Node) => lit.has(n) || hover === n;
  const coreHover = hover === "core";
  const isKpi = (n: Hover) => n === "campuses" || n === "students" || n === "rate";
  const lineOn = (l: Line) => hover === l || coreHover || lit.has(l);

  const kpis: { key: "campuses" | "students" | "rate"; icon: LucideIcon; label: string; value: number; suffix?: string }[] = [
    { key: "campuses", icon: Building2, label: "Campuses", value: KPIS.campuses },
    { key: "students", icon: GraduationCap, label: "Students", value: KPIS.students },
    { key: "rate", icon: TrendingUp, label: "Placement Rate", value: KPIS.rate, suffix: "%" },
  ];
  const controls: { key: "users" | "policies" | "settings"; icon: LucideIcon; label: [string, string] }[] = [
    { key: "users", icon: UserCog, label: ["Manage", "Users"] },
    { key: "policies", icon: ShieldCheck, label: ["Configure", "Policies"] },
    { key: "settings", icon: Settings, label: ["System", "Settings"] },
  ];
  const lineVisible = (l: Line) =>
    l === "users" || l === "policies" || l === "settings" ? stage >= 6 : stage >= 5;

  return (
    <figure
      className="relative m-0 w-full select-none [container-type:inline-size]"
      style={{ aspectRatio: `${W} / ${H}` }}
      aria-label="Ecosystem insight dashboard: campuses, students and placement rate feed performance trends and risk alerts into the Super Admin, who governs users, policies and system settings."
    >
      <div
        className="pointer-events-none absolute"
        style={{
          left: pctX(90),
          top: pctY(110),
          width: pctX(380),
          height: pctY(280),
          background: "radial-gradient(closest-side, hsl(var(--violet) / 0.15), transparent 80%)",
        }}
      />

      <svg viewBox={`0 0 ${W} ${H}`} className="absolute inset-0 h-full w-full overflow-visible" aria-hidden="true" focusable="false">
        {/* connection lines */}
        {LINES.map((l) => (
          <motion.path
            key={l}
            d={P[l]}
            fill="none"
            stroke="#A57BFF"
            strokeWidth={1.2}
            strokeDasharray={l === "campuses" || l === "students" || l === "rate" ? undefined : "3 4"}
            strokeLinecap="round"
            initial={{ pathLength: animate ? 0 : 1, opacity: 0 }}
            animate={{
              pathLength: lineVisible(l) ? 1 : animate ? 0 : 1,
              opacity: lineVisible(l) ? (lineOn(l) || hover === l ? 0.95 : 0.34) : 0,
            }}
            transition={{ pathLength: { duration: 0.7, ease: "easeOut" }, opacity: { duration: 0.35 } }}
          />
        ))}
        {/* KPI hover lights the shared trunk toward Super Admin */}
        {isKpi(hover) && (
          <path d={`M${CX} 84 L${CX} ${CY - CORE_R - 4}`} stroke="#A57BFF" strokeWidth={1.4} fill="none" opacity={0.95} />
        )}

        {/* Intelligence Pulse */}
        {animate &&
          cycle > 0 &&
          LINES.map((l) => <Comet key={`${l}${cycle}`} d={P[l]} delay={COMET[l][0]} duration={COMET[l][1]} />)}
      </svg>

      {/* KPI strip */}
      {kpis.map((k, i) => (
        <motion.div
          key={k.key}
          onPointerEnter={enter(k.key)}
          onPointerLeave={leave}
          initial={{ opacity: 0, y: animate ? 6 : 0 }}
          animate={{ opacity: stage >= 1 ? 1 : 0, y: stage >= 1 ? 0 : animate ? 6 : 0 }}
          transition={{ duration: 0.4, delay: animate ? i * 0.08 : 0 }}
          className="absolute"
          style={{ left: pctX(KPI_X[k.key] - 84), top: 0, width: pctX(168), height: pctY(58) }}
        >
          <div
            className={cn(
              "flex h-full w-full flex-col justify-center rounded-[10px] px-[7%]",
              cardBase,
              on(k.key) ? cardLit : cardIdle,
            )}
          >
            <div className="flex items-center gap-1.5">
              <IconChip icon={k.icon} lit={on(k.key)} size={16} />
              <span className={T_LABEL}>{k.label}</span>
            </div>
            <span className="mt-[3%] text-[clamp(15px,4.2cqw,24px)] font-semibold leading-none tracking-tight tabular-nums text-foreground">
              {stage >= 2 ? <KpiValue value={k.value} suffix={k.suffix} /> : <span className="opacity-0">0</span>}
            </span>
          </div>
        </motion.div>
      ))}

      {/* Performance Trends */}
      <motion.div
        onPointerEnter={enter("perf")}
        onPointerLeave={leave}
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 3 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute"
        style={{ left: pctX(14), top: pctY(112), width: pctX(200), height: pctY(116) }}
      >
        <div className={cn("flex h-full w-full flex-col justify-between rounded-[10px] p-[6%]", cardBase, on("perf") ? cardLit : cardIdle)}>
          <div className="flex items-center gap-1.5">
            <IconChip icon={TrendingUp} lit={on("perf")} size={18} />
            <span className="text-[clamp(9px,2.1cqw,12px)] font-semibold leading-[1.1] text-foreground">
              Performance
              <br />
              Trends
            </span>
          </div>
          <svg viewBox={`0 0 ${CH_W} ${CH_H}`} className="h-[44%] w-full overflow-visible" preserveAspectRatio="none" aria-hidden="true" focusable="false">
            <defs>
              <linearGradient id={`${uid}-a`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="#8B5CF6" stopOpacity="0.35" />
                <stop offset="1" stopColor="#8B5CF6" stopOpacity="0" />
              </linearGradient>
            </defs>
            <motion.path
              d={trendArea}
              fill={`url(#${uid}-a)`}
              initial={{ opacity: animate ? 0 : 1 }}
              animate={{ opacity: stage >= 3 ? 1 : animate ? 0 : 1 }}
              transition={{ duration: 0.8, delay: animate ? 0.5 : 0 }}
            />
            <motion.path
              d={trendLine}
              fill="none"
              stroke={on("perf") ? "#C4A6FF" : "#9D5CFF"}
              strokeWidth={1.8}
              strokeLinecap="round"
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
              initial={{ pathLength: animate ? 0 : 1 }}
              animate={{ pathLength: stage >= 3 ? 1 : animate ? 0 : 1 }}
              transition={{ duration: 0.9, ease: "easeOut" }}
            />
          </svg>
        </div>
      </motion.div>

      {/* Risk Alerts */}
      <motion.div
        onPointerEnter={enter("risk")}
        onPointerLeave={leave}
        initial={{ opacity: 0 }}
        animate={{ opacity: stage >= 4 ? 1 : 0 }}
        transition={{ duration: 0.4 }}
        className="absolute"
        style={{ left: pctX(346), top: pctY(112), width: pctX(200), height: pctY(116) }}
      >
        <div className={cn("flex h-full w-full flex-col justify-between rounded-[10px] p-[6%]", cardBase, on("risk") ? cardLit : cardIdle)}>
          <div className="flex items-center gap-1.5">
            <IconChip icon={ShieldAlert} lit={on("risk")} size={18} />
            <span className="text-[clamp(9px,2.1cqw,12px)] font-semibold leading-[1.1] text-foreground">
              Risk
              <br />
              Alerts
            </span>
          </div>
          <div className="flex h-[48%] items-end justify-between gap-[6%]">
            {RISK.map((r, i) => (
              <div key={r.level} className="flex h-full flex-1 flex-col items-center justify-end gap-1">
                <motion.div
                  className={cn(
                    "w-full origin-bottom rounded-[2px] transition-colors duration-300",
                    r.level === "HIGH" ? "bg-amber-400/80" : on("risk") ? "bg-violet-bright" : "bg-violet/80",
                  )}
                  style={{ height: `${Math.max(14, (r.count / riskMax) * 100)}%` }}
                  initial={{ scaleY: animate ? 0 : 1 }}
                  animate={{ scaleY: stage >= 4 ? 1 : animate ? 0 : 1 }}
                  transition={{ duration: 0.5, delay: animate ? i * 0.08 : 0, ease: "easeOut" }}
                />
                <span className="text-[clamp(7px,1.35cqw,8.5px)] uppercase tracking-wider text-muted-foreground">
                  {r.level === "MEDIUM" ? "Med" : r.level === "HIGH" ? "High" : "Low"}
                </span>
              </div>
            ))}
          </div>
        </div>
      </motion.div>

      {/* Super Admin core */}
      <motion.div
        onPointerEnter={enter("core")}
        onPointerLeave={leave}
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: stage >= 5 ? 1 : 0, scale: stage >= 5 ? 1 : 0.96 }}
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
        {animate && lit.has("core") && (
          <motion.div
            key={`ring${cycle}`}
            className="absolute inset-0 rounded-full border border-violet-bright/70"
            initial={{ scale: 1, opacity: 0.8 }}
            animate={{ scale: 1.5, opacity: 0 }}
            transition={{ duration: 0.9, ease: "easeOut" }}
          />
        )}
        <div
          className={cn(
            "relative flex h-full w-full flex-col items-center justify-center rounded-full border transition-shadow duration-500",
            on("core") ? "border-violet-bright/80 shadow-[0_0_38px_-2px_hsl(var(--violet)/0.8)]" : "border-violet/60 shadow-[0_0_26px_-4px_hsl(var(--violet)/0.55)]",
          )}
          style={{ background: "radial-gradient(circle at 50% 38%, hsl(var(--violet) / 0.36), hsl(var(--card)) 72%)" }}
        >
          <Landmark className="h-[18px] w-[18px] text-violet-bright" aria-hidden="true" />
          <span className="mt-1 text-center text-[clamp(8px,1.9cqw,11px)] font-semibold leading-[1.1] text-foreground">
            Super
            <br />
            Admin
          </span>
        </div>
        <span
          className={cn(
            "pointer-events-none absolute left-1/2 top-full mt-3 -translate-x-1/2 whitespace-nowrap rounded-full border border-violet/30 bg-card px-2.5 py-0.5 text-[8px] font-medium uppercase tracking-[0.2em] text-violet-bright transition-opacity duration-300",
            coreHover ? "opacity-100" : "opacity-0",
          )}
        >
          Ecosystem control
        </span>
      </motion.div>

      {/* Governance controls */}
      {controls.map((c, i) => (
        <motion.div
          key={c.key}
          onPointerEnter={enter(c.key)}
          onPointerLeave={leave}
          initial={{ opacity: 0, y: animate ? 6 : 0 }}
          animate={{ opacity: stage >= 6 ? 1 : 0, y: stage >= 6 ? 0 : animate ? 6 : 0 }}
          transition={{ duration: 0.4, delay: animate ? i * 0.08 : 0 }}
          className="absolute"
          style={{ left: pctX(CTRL_X[c.key] - 75), top: pctY(342), width: pctX(150), height: pctY(44) }}
        >
          <div
            className={cn(
              "flex h-full w-full items-center justify-center gap-2 rounded-[10px] px-2",
              cardBase,
              on(c.key) ? cardLit : cardIdle,
            )}
          >
            <IconChip icon={c.icon} lit={on(c.key)} size={20} />
            <span className="text-[clamp(8px,1.85cqw,11px)] font-medium uppercase leading-[1.15] tracking-wide text-foreground/90">
              {c.label[0]}
              <br />
              {c.label[1]}
            </span>
          </div>
        </motion.div>
      ))}
    </figure>
  );
}

/* ------------------------------------------------------------------ */
/* Compact version — tablet / mobile, shown above the form              */
/* ------------------------------------------------------------------ */

function Chip({ icon: Icon, label, value, core }: { icon: LucideIcon; label: string; value?: string; core?: boolean }) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-1 rounded-lg border px-1.5 py-2 text-center",
        core ? "border-violet/60 bg-violet/15 shadow-[0_0_22px_-4px_hsl(var(--violet)/0.5)]" : "border-white/[0.07] bg-card/60",
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0 text-violet-bright" aria-hidden="true" />
      {value && <span className="text-sm font-semibold tabular-nums leading-none text-foreground">{value}</span>}
      <span className="text-[10.5px] font-medium leading-tight text-foreground/85">{label}</span>
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

export function AdminInsightCompact() {
  return (
    <div className="relative overflow-hidden rounded-xl border border-white/[0.07] bg-elevated/70 p-4">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: "radial-gradient(60% 50% at 50% 50%, hsl(var(--violet) / 0.18), transparent 70%)" }}
      />
      <div className="relative mx-auto max-w-sm">
        <div className="grid grid-cols-3 gap-1.5">
          <Chip icon={Building2} label="Campuses" value={String(KPIS.campuses)} />
          <Chip icon={GraduationCap} label="Students" value={KPIS.students.toLocaleString("en-US")} />
          <Chip icon={TrendingUp} label="Placement Rate" value={`${KPIS.rate}%`} />
        </div>
        <Down />
        <div className="grid grid-cols-2 gap-1.5">
          <Chip icon={TrendingUp} label="Performance Trends" />
          <Chip icon={ShieldAlert} label="Risk Alerts" />
        </div>
        <Down />
        <div className="mx-auto w-fit min-w-[8rem]">
          <Chip icon={Landmark} label="Super Admin" core />
        </div>
        <Down />
        <div className="grid grid-cols-3 gap-1.5">
          <Chip icon={UserCog} label="Manage Users" />
          <Chip icon={ShieldCheck} label="Configure Policies" />
          <Chip icon={Settings} label="System Settings" />
        </div>
      </div>
    </div>
  );
}
