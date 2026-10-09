"use client";

import { useEffect, useRef, useState } from "react";
import { motion, useInView } from "framer-motion";
import {
  ArrowRight,
  BadgeCheck,
  BookOpen,
  Compass,
  FolderGit2,
  GraduationCap,
  Handshake,
  ListChecks,
  MessagesSquare,
  ScanSearch,
  Sparkles,
  TrendingUp,
  UserRoundSearch,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/hooks/useReducedMotion";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import {
  showcaseStudent,
  showcaseTopMatch,
  showcaseEligibleDrives,
  showcaseJourney,
} from "@/components/landing/showcase-data";

/**
 * "Placement Intelligence Flow Map" — five connected modules that show how
 * NEXPLOY turns one student's information into a placement outcome:
 * Student Intelligence → Readiness Engine → Opportunity Intelligence →
 * Recruiter Connection → Placement Outcome.
 *
 * All values come from the illustrative showcase data (labelled "Demo Data"
 * in the UI) so this pre-auth marketing section reads as one coherent story.
 * Horizontal on desktop, vertical on tablet/mobile. SVG/CSS + Framer Motion.
 */

// ---- illustrative showcase data (labelled "Demo Data" in the UI) ----
const s = showcaseStudent;
const TOP = {
  company: showcaseTopMatch.company,
  role: showcaseTopMatch.role,
  fit: showcaseTopMatch.fit,
  label: showcaseTopMatch.fitLabel,
};
const ELIGIBLE = showcaseEligibleDrives;

const STAGES = 5;
const ARRIVE_MS = 380; // card reveal cadence
const HOP_MS = 850; // pulse hop cadence
const LOOP_MS = 16000;

function useReveal(reduced: boolean) {
  const ref = useRef<HTMLDivElement | null>(null);
  const inView = useInView(ref, { once: true, margin: "0px 0px -15% 0px" });
  const [shown, setShown] = useState(0); // how many cards are revealed (0..5)
  const [lit, setLit] = useState(-1); // card currently receiving the pulse
  const [hop, setHop] = useState(-1); // connector the pulse is travelling
  const [cycle, setCycle] = useState(0);

  useEffect(() => {
    if (reduced) {
      setShown(STAGES);
      return;
    }
    if (!inView) return;
    const t = Array.from({ length: STAGES }, (_, i) => setTimeout(() => setShown(i + 1), 200 + i * ARRIVE_MS));
    return () => t.forEach(clearTimeout);
  }, [inView, reduced]);

  const ready = !reduced && shown >= STAGES;
  useEffect(() => {
    if (!ready) return;
    let timers: ReturnType<typeof setTimeout>[] = [];
    const run = () => {
      setCycle((c) => c + 1);
      timers = [];
      for (let i = 0; i < STAGES; i++) {
        timers.push(setTimeout(() => setLit(i), i * HOP_MS));
        if (i < STAGES - 1) timers.push(setTimeout(() => setHop(i), i * HOP_MS + 330));
      }
      timers.push(setTimeout(() => { setLit(-1); setHop(-1); }, STAGES * HOP_MS + 700));
    };
    const first = setTimeout(run, 500);
    const loop = setInterval(run, LOOP_MS);
    return () => {
      clearTimeout(first);
      clearInterval(loop);
      timers.forEach(clearTimeout);
    };
  }, [ready]);

  return { ref, shown, lit, hop, cycle };
}

/* ---------- connector ---------- */

function Connector({ index, shown, hop, cycle, active, animate }: { index: number; shown: number; hop: number; cycle: number; active: boolean; animate: boolean }) {
  const visible = shown > index + 1;
  const bright = visible && (hop === index || active);
  return (
    <div
      className="relative flex shrink-0 items-center justify-center max-lg:h-10 max-lg:w-full lg:h-auto lg:w-9 xl:w-12"
      aria-hidden="true"
    >
      {/* horizontal (desktop) */}
      <div className="absolute inset-x-0 top-1/2 hidden h-px -translate-y-1/2 lg:block">
        <motion.div
          className="h-full origin-left bg-gradient-to-r from-violet/40 via-violet-bright/70 to-violet/40"
          initial={{ scaleX: animate ? 0 : 1 }}
          animate={{ scaleX: visible ? 1 : animate ? 0 : 1, opacity: bright ? 1 : 0.6 }}
          transition={{ scaleX: { duration: 0.35, ease: "easeOut" }, opacity: { duration: 0.3 } }}
        />
        {animate && hop === index && (
          <motion.span
            key={`h${cycle}`}
            className="absolute top-1/2 h-1.5 w-1.5 -translate-y-1/2 rounded-full bg-[#F3EBFF] shadow-[0_0_8px_2px_hsl(var(--violet-bright))]"
            initial={{ left: "0%", opacity: 0 }}
            animate={{ left: "100%", opacity: [0, 1, 1, 0] }}
            transition={{ duration: 0.5, ease: "linear" }}
          />
        )}
      </div>
      {/* vertical (tablet / mobile) */}
      <div className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 lg:hidden">
        <motion.div
          className="h-full origin-top bg-gradient-to-b from-violet/40 via-violet-bright/70 to-violet/40"
          initial={{ scaleY: animate ? 0 : 1 }}
          animate={{ scaleY: visible ? 1 : animate ? 0 : 1, opacity: bright ? 1 : 0.6 }}
          transition={{ scaleY: { duration: 0.35, ease: "easeOut" }, opacity: { duration: 0.3 } }}
        />
        {animate && hop === index && (
          <motion.span
            key={`v${cycle}`}
            className="absolute left-1/2 h-1.5 w-1.5 -translate-x-1/2 rounded-full bg-[#F3EBFF] shadow-[0_0_8px_2px_hsl(var(--violet-bright))]"
            initial={{ top: "0%", opacity: 0 }}
            animate={{ top: "100%", opacity: [0, 1, 1, 0] }}
            transition={{ duration: 0.5, ease: "linear" }}
          />
        )}
      </div>
      <span
        className={cn(
          "relative z-10 flex h-5 w-5 items-center justify-center rounded-full border bg-background transition-colors duration-300",
          bright ? "border-violet-bright/80 text-violet-bright" : "border-violet/40 text-violet/80",
        )}
      >
        <ArrowRight className="h-3 w-3 max-lg:rotate-90" />
      </span>
    </div>
  );
}

/* ---------- card shell ---------- */

function StageCard({
  n,
  title,
  icon: Icon,
  visible,
  lit,
  hovered,
  onEnter,
  onLeave,
  animate,
  className,
  children,
  tone = "violet",
}: {
  n: number;
  title: [string, string];
  icon: LucideIcon;
  visible: boolean;
  lit: boolean;
  hovered: boolean;
  onEnter: (e: React.PointerEvent) => void;
  onLeave: () => void;
  animate: boolean;
  className?: string;
  children: React.ReactNode;
  tone?: "violet" | "success";
}) {
  const on = lit || hovered;
  return (
    <motion.article
      onPointerEnter={onEnter}
      onPointerLeave={onLeave}
      initial={{ opacity: 0, y: animate ? 14 : 0 }}
      animate={{ opacity: visible ? 1 : 0, y: visible ? (hovered && animate ? -3 : 0) : animate ? 14 : 0 }}
      transition={{ duration: 0.45, ease: "easeOut" }}
      className={cn(
        "relative flex min-w-0 flex-col overflow-hidden rounded-2xl border bg-gradient-to-b from-elevated to-card p-4 transition-[border-color,box-shadow] duration-500 xl:p-5",
        on
          ? tone === "success"
            ? "border-success/50 shadow-[0_0_34px_-8px_hsl(var(--success)/0.45),0_0_28px_-10px_hsl(var(--violet)/0.7)]"
            : "border-violet-bright/60 shadow-[0_0_34px_-8px_hsl(var(--violet)/0.7)]"
          : "border-white/[0.08] shadow-[0_10px_30px_-18px_hsl(0_0%_0%/0.9)]",
        className,
      )}
    >
      {/* violet edge illumination */}
      <span
        className={cn(
          "pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent to-transparent transition-opacity duration-500",
          tone === "success" ? "via-success/70" : "via-violet-bright/80",
          on ? "opacity-100" : "opacity-50",
        )}
      />
      <header className="flex items-center gap-2.5">
        <span
          className={cn(
            "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border transition-colors duration-500",
            on ? "border-violet-bright/70 bg-violet/25" : "border-violet/30 bg-violet/10",
          )}
        >
          <Icon className={cn("h-4 w-4", tone === "success" && on ? "text-success" : "text-violet-bright")} aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <p className="text-[10px] font-medium tabular-nums text-muted-foreground/70">0{n}</p>
          <h3 className="text-[11px] font-semibold uppercase leading-[1.15] tracking-[0.12em] text-foreground">
            {title[0]}
            <br />
            {title[1]}
          </h3>
        </div>
      </header>
      <div className="mt-4 flex flex-1 flex-col">{children}</div>
    </motion.article>
  );
}

function Chip({ icon: Icon, label, value, on }: { icon: LucideIcon; label: string; value: string; on: boolean }) {
  return (
    <li
      className={cn(
        "flex items-center gap-2 rounded-lg border px-2 py-1.5 transition-colors duration-300",
        on ? "border-violet/50 bg-violet/15" : "border-white/[0.07] bg-card/70",
      )}
    >
      <Icon className="h-3.5 w-3.5 shrink-0 text-violet-bright" aria-hidden="true" />
      <span className="min-w-0 leading-tight">
        <span className="block text-[11px] font-medium text-foreground">{label}</span>
        <span className="block truncate text-[10px] text-muted-foreground">{value}</span>
      </span>
    </li>
  );
}

function Step({ icon: Icon, label, done, last }: { icon: LucideIcon; label: string; done: boolean; last?: boolean }) {
  return (
    <li className="relative flex items-center gap-2.5 pb-3 last:pb-0">
      {!last && <span className="absolute left-[9px] top-5 h-[calc(100%-12px)] w-px bg-violet/30" aria-hidden="true" />}
      <span
        className={cn(
          "relative flex h-[19px] w-[19px] shrink-0 items-center justify-center rounded-full border",
          done ? "border-violet-bright/70 bg-violet/25" : "border-white/15 bg-card",
        )}
      >
        <Icon className={cn("h-2.5 w-2.5", done ? "text-violet-bright" : "text-muted-foreground")} aria-hidden="true" />
      </span>
      <span className="text-[12px] text-foreground/90">{label}</span>
    </li>
  );
}

export function PlacementFlowMap() {
  const reduced = useReducedMotion();
  const animate = !reduced;
  const { ref, shown, lit, hop, cycle } = useReveal(reduced);
  const [hover, setHover] = useState<number | null>(null);

  const enter = (i: number) => (e: React.PointerEvent) => {
    if (e.pointerType === "mouse") setHover(i);
  };
  const leave = () => setHover(null);
  const card = (i: number) => ({
    n: i + 1,
    visible: shown > i,
    lit: lit === i,
    hovered: hover === i,
    onEnter: enter(i),
    onLeave: leave,
    animate,
  });
  // a hovered card brightens the connectors on either side of it
  const connActive = (i: number) => hover === i || hover === i + 1;

  const gaps = s.criticalGaps.join(" · ");

  return (
    <div ref={ref} className="relative">
      {/* atmosphere: strongest behind Readiness + Opportunity */}
      <div
        className="pointer-events-none absolute inset-x-0 top-1/2 -z-0 hidden h-[110%] -translate-y-1/2 lg:block"
        style={{ background: "radial-gradient(45% 50% at 47% 50%, hsl(var(--violet) / 0.16), transparent 75%)" }}
        aria-hidden="true"
      />

      <div className="relative z-10 mx-auto flex max-w-[1240px] flex-col items-stretch max-lg:max-w-md lg:flex-row lg:items-stretch">
        {/* 1 — Student Intelligence */}
        <StageCard {...card(0)} title={["Student", "Intelligence"]} icon={UserRoundSearch} className="lg:flex-1">
          <div className="flex items-center gap-3">
            <span className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-violet/60 bg-card shadow-[0_0_22px_-4px_hsl(var(--violet)/0.7)]">
              <span className="text-sm font-semibold text-violet-bright">{s.avatarInitials}</span>
            </span>
            <div className="leading-tight">
              <p className="text-sm font-semibold text-foreground">{s.name}</p>
              <p className="text-[11px] text-muted-foreground">B.Tech {s.branchCode}</p>
              <p className="mt-0.5 text-[9px] font-semibold uppercase tracking-[0.16em] text-violet-bright">Digital Twin</p>
            </div>
          </div>
          <ul className="mt-4 grid grid-cols-1 gap-1.5">
            <Chip icon={BookOpen} label="Academics" value={`${s.cgpa.toFixed(2)} CGPA`} on={hover === 0} />
            <Chip icon={FolderGit2} label="Projects" value={`${s.projectsCount} built`} on={hover === 0} />
            <Chip icon={Sparkles} label="Skills" value={`${s.strengthsCount} strengths`} on={hover === 0} />
            <Chip icon={Compass} label="Interests" value={s.primaryInterest} on={hover === 0} />
          </ul>
          <p className="mt-auto pt-4 text-[10px] uppercase tracking-wider text-muted-foreground/70">Raw potential</p>
        </StageCard>

        <Connector index={0} shown={shown} hop={hop} cycle={cycle} active={connActive(0)} animate={animate} />

        {/* 2 — Readiness Engine */}
        <StageCard {...card(1)} title={["Readiness", "Engine"]} icon={ScanSearch} className="lg:flex-1">
          <div className="flex flex-col items-center">
            {shown >= 2 ? (
              <ScoreRing value={s.readiness} size={112} strokeWidth={8} label="/ 100" colorClass="stroke-violet-bright" />
            ) : (
              <div style={{ width: 112, height: 112 }} />
            )}
            <span className="mt-3 rounded-full border border-success/40 bg-success/10 px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-success">
              {s.riskReason}
            </span>
          </div>
          <ul className="mt-4 space-y-1.5">
            <li className={cn("flex items-center gap-2 rounded-lg border px-2 py-1.5 text-[11px] font-medium", hover === 1 ? "border-violet/50 bg-violet/15" : "border-white/[0.07] bg-card/70")}>
              <TrendingUp className="h-3.5 w-3.5 text-violet-bright" aria-hidden="true" /> Skill Analysis
            </li>
            <li className={cn("rounded-lg border px-2 py-1.5", hover === 1 ? "border-violet/50 bg-violet/15" : "border-white/[0.07] bg-card/70")}>
              <span className="flex items-center gap-2 text-[11px] font-medium">
                <ListChecks className="h-3.5 w-3.5 text-violet-bright" aria-hidden="true" /> Gap Identification
              </span>
              <span className="mt-0.5 block pl-[22px] text-[10px] text-muted-foreground">{gaps}</span>
            </li>
          </ul>
        </StageCard>

        <Connector index={1} shown={shown} hop={hop} cycle={cycle} active={connActive(1)} animate={animate} />

        {/* 3 — Opportunity Intelligence (hero card) */}
        <StageCard {...card(2)} title={["Opportunity", "Intelligence"]} icon={Sparkles} className="lg:flex-[1.2] lg:-my-2 lg:py-6">
          <div className={cn("rounded-xl border p-3 transition-colors duration-500", lit === 2 || hover === 2 ? "border-violet-bright/60 bg-violet/15" : "border-violet/30 bg-violet/[0.07]")}>
            <p className="text-lg font-semibold leading-tight text-foreground">{TOP.company}</p>
            <p className="mt-1 flex items-baseline gap-1.5">
              <span className="bg-gradient-to-r from-violet-bright to-[#C4A6FF] bg-clip-text text-4xl font-semibold tabular-nums leading-none text-transparent">
                {TOP.fit}%
              </span>
              <span className="text-[11px] font-semibold uppercase tracking-wider text-violet-bright">Match</span>
            </p>
          </div>
          <ul className="mt-4 space-y-2 text-[12px]">
            <li className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">Role</span>
              <span className="font-medium text-foreground">{TOP.role}</span>
            </li>
            <li className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">Eligible Drives</span>
              <span className="font-medium tabular-nums text-foreground">{ELIGIBLE}</span>
            </li>
            <li className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">Fit</span>
              <span className="rounded-full border border-violet/40 bg-violet/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-violet-bright">
                {TOP.label}
              </span>
            </li>
          </ul>
          <p className="mt-auto pt-4 text-[10px] uppercase tracking-wider text-muted-foreground/70">
            Readiness → Eligibility → Matching
          </p>
        </StageCard>

        <Connector index={2} shown={shown} hop={hop} cycle={cycle} active={connActive(2)} animate={animate} />

        {/* 4 — Recruiter Connection */}
        <StageCard {...card(3)} title={["Recruiter", "Connection"]} icon={Handshake} className="lg:flex-1">
          <div>
            <span
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wider transition-colors duration-500",
                lit === 3 || hover === 3 ? "border-violet-bright/70 bg-violet/25 text-foreground" : "border-violet/40 bg-violet/10 text-violet-bright",
              )}
            >
              <span className="h-1.5 w-1.5 rounded-full bg-violet-bright" aria-hidden="true" /> Shortlisted
            </span>
            <p className="mt-1.5 text-[11px] text-muted-foreground">Interviewing</p>
          </div>
          <ul className="mt-4">
            <Step icon={ListChecks} label="Assessment" done={showcaseJourney.assessmentDone} />
            <Step icon={MessagesSquare} label="Interview" done={showcaseJourney.interviewDone} />
            <Step icon={Handshake} label="Hiring Process" done={showcaseJourney.offerDone} last />
          </ul>
          <p className="mt-auto pt-4 text-[10px] leading-snug text-muted-foreground/70">Recruiters &amp; placement teams decide.</p>
        </StageCard>

        <Connector index={3} shown={shown} hop={hop} cycle={cycle} active={connActive(3)} animate={animate} />

        {/* 5 — Placement Outcome */}
        <StageCard {...card(4)} title={["Placement", "Outcome"]} icon={GraduationCap} tone="success" className="lg:flex-1">
          <div className="flex flex-col items-center text-center">
            <span
              className={cn(
                "flex h-14 w-14 items-center justify-center rounded-full border transition-all duration-500",
                lit === 4 || hover === 4
                  ? "border-success/60 bg-success/15 shadow-[0_0_26px_-4px_hsl(var(--success)/0.6)]"
                  : "border-violet/40 bg-violet/10",
              )}
            >
              <BadgeCheck className={cn("h-7 w-7 transition-colors duration-500", lit === 4 || hover === 4 ? "text-success" : "text-violet-bright")} aria-hidden="true" />
            </span>
            <p className="mt-3 text-lg font-semibold tracking-wide text-foreground">OFFER</p>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-success">Success</p>
          </div>
          <ul className="mt-4 space-y-1.5 text-[12px] text-foreground/90">
            {["Career Growth", "Better Opportunities", "Stronger Outcomes"].map((t) => (
              <li key={t} className="flex items-center gap-2 rounded-lg border border-white/[0.07] bg-card/70 px-2 py-1.5">
                <span className="h-1 w-1 rounded-full bg-violet-bright" aria-hidden="true" /> {t}
              </li>
            ))}
          </ul>
        </StageCard>
      </div>
    </div>
  );
}
