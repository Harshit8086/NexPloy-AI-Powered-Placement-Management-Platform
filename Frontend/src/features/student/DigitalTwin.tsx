"use client";

import { useState } from "react";
import Link from "next/link";
import dynamic from "next/dynamic";
import { motion } from "framer-motion";
import {
  RefreshCw,
  Fingerprint,
  TrendingUp,
  CheckCircle2,
  AlertTriangle,
  ShieldCheck,
  ShieldAlert,
  Target,
  Briefcase,
  ClipboardList,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/PageHeader";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { StagedRunner } from "@/components/intelligence/StagedRunner";
import {
  IntelligencePulse,
  IntelligencePulseBadge,
} from "@/components/intelligence/IntelligencePulse";
import { TrendAreaChart } from "@/components/charts/TrendAreaChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { showcaseEntrance } from "@/lib/motion-variants";
import { evaluateEligibility } from "@/lib/eligibility";
import { useApiResource } from "@/hooks/useApiResource";
import { getPrimaryStudent, refreshDigitalTwin, getRoadmap, type Roadmap } from "@/lib/api/students";
import { listDrives } from "@/lib/api/drives";
import { listApplicationsByStudent } from "@/lib/api/applications";
import { digitalTwinSteps } from "@/lib/simulate";
import type { DigitalTwinResult } from "@/lib/simulate";
import { riskTone } from "@/lib/status";
import { cn } from "@/lib/utils";
import type { Student, Application, Drive } from "@/types";

type RunState = "idle" | "running" | "done";

/** Compact key:value tile used for the twin's headline figures. */
function TwinStat({
  label,
  value,
  tone = "default",
}: {
  label: string;
  value: string;
  tone?: "default" | "violet" | "success" | "risk";
}) {
  const toneClass = {
    default: "text-foreground",
    violet: "text-violet-bright",
    success: "text-success",
    risk: "text-risk",
  }[tone];
  return (
    <div className="rounded-lg border border-border bg-elevated/60 p-3">
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className={cn("mt-1 text-lg font-semibold tabular-nums", toneClass)}>{value}</p>
    </div>
  );
}

function DigitalTwinSkeleton() {
  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-2.5">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-7 w-72" />
          <Skeleton className="h-4 w-96" />
        </div>
        <Skeleton className="h-10 w-52" />
      </div>
      <Skeleton className="h-[420px] w-full" />
      <Skeleton className="mt-5 h-[380px] w-full" />
      <div className="mt-5 grid gap-5 md:grid-cols-2">
        <Skeleton className="h-[280px] w-full" />
        <Skeleton className="h-[280px] w-full" />
      </div>
    </div>
  );
}

// Deferred out of this page's chunk — it's a client-only wrapper around the
// (already independently code-split) three.js constellation scene, and has
// no reason to block this page's own bundle from becoming interactive.
// Loading fallback matches the fixed h-[340px]/h-[380px] card it sits
// inside, so nothing shifts when the real canvas mounts.
const DigitalTwinCanvas = dynamic(
  () => import("@/components/three/DigitalTwinCanvas").then((m) => m.DigitalTwinCanvas),
  {
    ssr: false,
    loading: () => <Skeleton className="h-full w-full" />,
  },
);

interface TwinData {
  student: Student;
  twin: DigitalTwinResult;
  applications: Application[];
  eligibleDrives: Drive[];
  roadmap: Roadmap | null;
}

/**
 * Loads the signed-in student's digital twin from live endpoints. The profile (/students/me) is
 * the anchor: a 404 means no student profile yet → null → empty state (never fabricated data).
 * The twin summary is authoritative (server-derived from readinessScore). Applications and drives
 * are best-effort overlays; eligibility is evaluated client-side over live drives. No per-student
 * match signal exists in the backend yet, so no "Top Match" is shown (would be fabricated).
 */
async function loadTwin(): Promise<TwinData | null> {
  let student: Student;
  try {
    student = await getPrimaryStudent();
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status: number }).status === 404) {
      return null;
    }
    throw error;
  }

  const [twin, applications, allDrives, roadmap] = await Promise.all([
    refreshDigitalTwin(),
    listApplicationsByStudent(student.id).catch(() => [] as Application[]),
    listDrives().catch(() => [] as Drive[]),
    getRoadmap().catch(() => null),
  ]);

  const eligibleDrives = allDrives
    .filter((d) => d.status === "ACTIVE")
    .filter((d) => evaluateEligibility(student, d).eligible);

  return {
    student,
    twin,
    applications: applications.filter((a) => a.status !== "eligible"),
    eligibleDrives,
    roadmap,
  };
}

export function DigitalTwinPage() {
  const { data, loading, error, reload } = useApiResource<TwinData | null>(loadTwin, [], null);
  const [runState, setRunState] = useState<RunState>("idle");
  const [revealKey, setRevealKey] = useState(0);

  const s = data?.student;
  const result = data?.twin;
  const applications = data?.applications ?? [];
  const eligibleDrives = data?.eligibleDrives ?? [];
  const roadmap = data?.roadmap ?? null;

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!s || !result}
      onRetry={reload}
      loadingFallback={<DigitalTwinSkeleton />}
      emptyTitle="No student profile"
      emptyMessage="Your student profile isn't available yet. Once your profile is registered, your digital twin will populate here."
      className="mx-auto max-w-5xl"
    >
      {s && result && (
        <DigitalTwinView
          s={s}
          result={result}
          applications={applications}
          eligibleDrives={eligibleDrives}
          roadmap={roadmap}
          runState={runState}
          setRunState={setRunState}
          revealKey={revealKey}
          setRevealKey={setRevealKey}
          onRefresh={reload}
        />
      )}
    </AsyncBoundary>
  );
}

interface DigitalTwinViewProps {
  s: Student;
  result: DigitalTwinResult;
  applications: Application[];
  eligibleDrives: Drive[];
  roadmap: Roadmap | null;
  runState: RunState;
  setRunState: (state: RunState) => void;
  revealKey: number;
  setRevealKey: (fn: (k: number) => number) => void;
  onRefresh: () => void;
}

function DigitalTwinView({
  s,
  result,
  applications,
  eligibleDrives,
  roadmap,
  runState,
  setRunState,
  revealKey,
  setRevealKey,
  onRefresh,
}: DigitalTwinViewProps) {
  const trendData = s.readinessTrend.map((v, i) => ({ label: `W${i + 1}`, value: v }));

  // Next best actions come from the live Career Roadmap: pending tasks in the current focus
  // phase (or the first non-done phase when no focus is set). Best-effort — null roadmap
  // shows the honest empty state.
  const focusPhase =
    roadmap?.phases.find((p) => p.status === "CURRENT") ??
    roadmap?.phases.find((p) => p.status !== "DONE") ??
    null;
  const pendingActions = (focusPhase?.tasks ?? []).filter((t) => t.status === "PLANNED").slice(0, 3);

  const twinModules = [
    { label: "Academics", value: s.breakdown.academic },
    { label: "Aptitude", value: s.breakdown.aptitude },
    { label: "Communication", value: s.breakdown.communication },
    { label: "Projects", value: s.breakdown.projects },
    { label: "Mock Interview", value: s.breakdown.interview },
  ];

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Intelligence"
        title="Placement Digital Twin"
        subtitle="A continuously updated digital representation of a student's placement readiness and trajectory."
        actions={
          <Button
            variant="glow"
            onClick={() => setRunState("running")}
            disabled={runState === "running"}
          >
            <RefreshCw className={cn("h-4 w-4", runState === "running" && "animate-spin")} />
            Refresh Digital Twin
          </Button>
        }
      />

      <IntelligencePulse active={runState === "running"}>
        <Card className="p-6">
          {runState === "running" ? (
            <div className="flex flex-col items-center gap-6 py-6">
              <IntelligencePulseBadge label="Refreshing Digital Twin" />
              <div className="w-full max-w-sm">
                <StagedRunner
                  steps={digitalTwinSteps}
                  onDone={() => {
                    setRunState("done");
                    setRevealKey((k) => k + 1);
                    onRefresh();
                    window.setTimeout(() => setRunState("idle"), 1800);
                  }}
                />
              </div>
            </div>
          ) : (
            <>
              {runState === "done" && (
                <motion.div
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="mb-5 flex items-center gap-2 rounded-lg border border-success/40 bg-success/10 px-3 py-2 text-sm font-medium text-success"
                >
                  <CheckCircle2 className="h-4 w-4" />
                  {result.label}
                </motion.div>
              )}
              <motion.div
                key={revealKey}
                initial="hidden"
                animate="visible"
                variants={showcaseEntrance}
              >
                <div className="grid gap-8 md:grid-cols-[auto_1fr]">
                  <div className="flex flex-col items-center gap-3">
                    <ScoreRing
                      value={s.readiness}
                      size={168}
                      strokeWidth={12}
                      label="Readiness"
                      sublabel="out of 100"
                      animateKey={revealKey}
                    />
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                      <Fingerprint className="h-3.5 w-3.5 text-violet-bright" /> Target:{" "}
                      {s.targetRole}
                    </div>
                  </div>

                  <div className="space-y-5">
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                      <TwinStat
                        label="Placement Probability"
                        value={`${s.placementProbability}%`}
                        tone="success"
                      />
                      <TwinStat label="Risk" value={s.riskLevel} tone={s.riskLevel === "LOW" ? "success" : "risk"} />
                      <TwinStat label="Active Applications" value={`${applications.length}`} />
                      <TwinStat label="Eligible Drives" value={`${eligibleDrives.length}`} />
                      <TwinStat label="Resume Score" value={`${s.resumeScore}/100`} />
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                      <div>
                        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-success">
                          <CheckCircle2 className="h-3.5 w-3.5" /> Strong Areas
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {s.strengths.map((st) => (
                            <Badge key={st} variant="success">
                              {st}
                            </Badge>
                          ))}
                        </div>
                      </div>
                      <div>
                        <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-risk">
                          <AlertTriangle className="h-3.5 w-3.5" /> Improvement Areas
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {s.criticalGaps.map((g) => (
                            <Badge key={g} variant="risk">
                              {g}
                            </Badge>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-6">
                  <p className="mb-2.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Signals feeding the twin
                  </p>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <SkillBar label="Academics" value={s.breakdown.academic} />
                    <SkillBar label="Aptitude" value={s.breakdown.aptitude} />
                    <SkillBar label="Communication" value={s.breakdown.communication} />
                    <SkillBar label="Projects" value={s.breakdown.projects} />
                    <SkillBar label="Mock Interview" value={s.breakdown.interview} />
                  </div>
                </div>
              </motion.div>
            </>
          )}
        </Card>
      </IntelligencePulse>

      <Card className="relative mt-5 overflow-hidden p-5">
        <div className="mb-1 flex items-center justify-between">
          <div>
            <CardTitle>Intelligence Constellation</CardTitle>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Live signal feeding into the Digital Twin from every readiness input.
            </p>
          </div>
        </div>
        <div key={revealKey} className="relative h-[340px] w-full sm:h-[380px]">
          <DigitalTwinCanvas readiness={s.readiness} modules={twinModules} />
        </div>
      </Card>

      <div className="mt-5 grid gap-5 md:grid-cols-2">
        <Card className="p-5">
          <div className="mb-3 flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-violet-bright" />
            <CardTitle>Readiness Trend</CardTitle>
          </div>
          <TrendAreaChart data={trendData} dataKey="value" xKey="label" />
        </Card>

        <Card className="p-5">
          <div className="mb-3 flex items-center gap-2">
            {s.riskLevel === "LOW" ? (
              <ShieldCheck className="h-4 w-4 text-success" />
            ) : (
              <ShieldAlert className="h-4 w-4 text-risk" />
            )}
            <CardTitle>Next Best Actions</CardTitle>
          </div>
          <div className="mb-3 flex items-center gap-2">
            <Badge variant={riskTone[s.riskLevel]}>{s.riskLevel} RISK</Badge>
            <span className="text-xs text-muted-foreground">{s.riskReason}</span>
          </div>
          <div className="space-y-2.5">
            {pendingActions.length > 0 ? (
              pendingActions.map((task) => (
                <div
                  key={task.id}
                  className="flex items-start gap-3 rounded-lg border border-border p-3"
                >
                  <span className="mt-0.5 shrink-0 rounded-full bg-violet/15 px-2 py-0.5 text-[10px] font-semibold text-violet-bright">
                    {task.kind}
                  </span>
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{task.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {task.detail ?? task.addressesGap ?? focusPhase?.title}
                    </p>
                  </div>
                  {task.skill && (
                    <span className="ml-auto shrink-0 text-xs font-medium text-success">
                      {task.skill}
                    </span>
                  )}
                </div>
              ))
            ) : (
              <p className="py-6 text-center text-sm text-muted-foreground">
                No roadmap actions yet.
              </p>
            )}
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm">
              <Link href="/student/roadmap">
                <Target className="h-3.5 w-3.5" /> Roadmap
              </Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link href="/student/drives">
                <Briefcase className="h-3.5 w-3.5" /> Drives
              </Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link href="/student/applications">
                <ClipboardList className="h-3.5 w-3.5" /> Applications
              </Link>
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}
