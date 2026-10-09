"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { useState, useCallback } from "react";
import {
  Fingerprint,
  Target,
  ListChecks,
  Briefcase,
  ClipboardList,
  ArrowUpRight,
  Gauge,
  CalendarClock,
  Award,
  ShieldCheck,
  Loader2,
} from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { GlowLine } from "@/components/intelligence/GlowLine";
import { DataReveal } from "@/components/intelligence/DataReveal";
import { TrendAreaChart } from "@/components/charts/TrendAreaChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { staggerContainer, staggerItem } from "@/lib/motion-variants";
import { useApiResource } from "@/hooks/useApiResource";
import { getPrimaryStudent } from "@/lib/api/students";
import { listDrives } from "@/lib/api/drives";
import { listApplicationsByStudent } from "@/lib/api/applications";
import { listInterviewsByStudent } from "@/lib/api/interviews";
import { listOffersByStudent } from "@/lib/api/offers";
import { evaluateEligibility } from "@/lib/eligibility";
import { applicationStatusLabel, applicationStatusTone, readinessBand } from "@/lib/status";
import { getRoleSkillGap } from "@/lib/api/skillgap";
import type { Application, Drive, InterviewSlot, Offer, SkillGap, Student } from "@/types";

const DIMENSION_LABELS: Record<string, string> = {
  academic: "Academics",
  aptitude: "Aptitude",
  communication: "Communication",
  projects: "Projects",
  interview: "Mock Interview",
};

interface DashboardData {
  student: Student;
  applications: Application[];
  /** id → drive, so application/eligible rows can resolve company + role. */
  driveMap: Record<string, Drive>;
  eligibleDrives: Drive[];
  nextInterview?: InterviewSlot;
  offers: Offer[];
  targetCompanyName?: string | null;
  targetRoleName?: string | null;
  selectedDriveId?: string | null;
}

/**
 * Loads the signed-in student's dashboard entirely from live endpoints. The profile
 * (/students/me) is the anchor: a 404 means the authenticated account has no student
 * profile yet, which resolves to `null` → AsyncBoundary shows an empty state (never
 * fabricated data, per the single-source-of-truth rule). Any other profile failure
 * propagates to the error/retry state. The secondary lists are best-effort overlays —
 * one module being unavailable renders as "none" rather than blanking the whole page.
 */
async function loadDashboard(): Promise<DashboardData | null> {
  let student: Student;
  try {
    student = await getPrimaryStudent();
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status: number }).status === 404) {
      return null;
    }
    throw error;
  }

  const [allDrives, applications, interviews, offers, roleSkillGap] = await Promise.all([
    listDrives().catch(() => [] as Drive[]),
    listApplicationsByStudent(student.id).catch(() => [] as Application[]),
    listInterviewsByStudent(student.id).catch(() => [] as InterviewSlot[]),
    listOffersByStudent(student.id).catch(() => [] as Offer[]),
    getRoleSkillGap(student.id).catch(() => null),
  ]);

  let skillGaps: SkillGap[] = roleSkillGap?.gaps ?? [];

  // Fallback: If no drive-specific gap was returned yet, populate from student's critical gaps
  // so the dashboard intelligence card displays live identified gap areas.
  if (skillGaps.length === 0 && student.criticalGaps && student.criticalGaps.length > 0) {
    skillGaps = student.criticalGaps.map((skill) => ({
      skill,
      currentLevel: null,
      targetLevel: 80,
      currentLabel: "Needs Practice",
      targetLabel: "Target Level",
      priority: "HIGH",
      whyItMatters: `${skill} is a critical gap identified for your target role (${student.targetRole || "Engineering"}).`,
      recommendedAction: `Focus on projects and practice modules in ${skill} to bridge readiness.`,
      evidenceStatus: "NO_EVIDENCE",
    }));
  }

  const studentWithGaps: Student = {
    ...student,
    skillGaps,
  };

  const driveMap: Record<string, Drive> = Object.fromEntries(allDrives.map((d) => [d.id, d]));
  const eligibleDrives = allDrives
    .filter((d) => d.status === "ACTIVE")
    .filter((d) => evaluateEligibility(studentWithGaps, d).eligible);
  const nextInterview = interviews.find((i) => i.status === "SCHEDULED");

  const targetCompanyName = roleSkillGap?.companyName || null;
  const targetRoleName = roleSkillGap?.targetRole || student.targetRole || null;
  const selectedDriveId = roleSkillGap?.driveId || null;

  return {
    student: studentWithGaps,
    applications,
    driveMap,
    eligibleDrives,
    nextInterview,
    offers,
    targetCompanyName,
    targetRoleName,
    selectedDriveId,
  };
}

function DashboardSkeleton() {
  return (
    <div className="mx-auto max-w-6xl">
      <div className="mb-6 space-y-2.5">
        <Skeleton className="h-3 w-36" />
        <Skeleton className="h-7 w-72" />
        <Skeleton className="h-4 w-96" />
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-6">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton key={i} className="h-[92px] w-full" />
        ))}
      </div>
      <div className="mt-5 grid gap-5 lg:grid-cols-3">
        <Skeleton className="h-[340px] w-full lg:col-span-1" />
        <Skeleton className="h-[340px] w-full lg:col-span-2" />
      </div>
      <div className="mt-4 grid gap-5 lg:grid-cols-3">
        <Skeleton className="h-[260px] w-full lg:col-span-2" />
        <Skeleton className="h-[260px] w-full" />
      </div>
      <Skeleton className="mt-5 h-[220px] w-full" />
    </div>
  );
}

const SKILL_GAP_DRIVE_KEY = "nexploy.dashboard.skillGapDriveId";

export function StudentDashboard() {
  const { data, loading, error, reload } = useApiResource<DashboardData | null>(
    loadDashboard,
    [],
    null,
  );

  // Option 1: Drive selector state for Skill Gaps card
  const [gapDriveId, setGapDriveId] = useState<string>("");
  const [gapSkills, setGapSkills] = useState<SkillGap[] | null>(null);
  const [gapCompany, setGapCompany] = useState<string | null>(null);
  const [gapRole, setGapRole] = useState<string | null>(null);
  const [gapLoading, setGapLoading] = useState(false);

  // When data first loads, seed the dropdown with the auto-selected drive
  const initGapState = useCallback((d: DashboardData) => {
    const storedId = (() => { try { return localStorage.getItem(SKILL_GAP_DRIVE_KEY); } catch { return null; } })();
    const hasDrive = d.driveMap && Object.keys(d.driveMap).length > 0;
    const resolvedId = storedId && hasDrive && d.driveMap[storedId] ? storedId : (d.selectedDriveId ?? "");
    setGapDriveId(resolvedId);
    setGapCompany(d.targetCompanyName ?? null);
    setGapRole(d.targetRoleName ?? null);
    setGapSkills(d.student.skillGaps);
  }, []);

  // Fire once when data lands
  const [initialized, setInitialized] = useState(false);
  if (data && !initialized) {
    setInitialized(true);
    initGapState(data);
  }

  const handleDriveChange = async (driveId: string) => {
    if (!data?.student?.id) return;
    setGapDriveId(driveId);
    try { localStorage.setItem(SKILL_GAP_DRIVE_KEY, driveId); } catch { /* ignore */ }
    setGapLoading(true);
    try {
      const res = await getRoleSkillGap(data.student.id, driveId || undefined);
      setGapSkills(res.gaps);
      setGapCompany(res.companyName ?? null);
      setGapRole(res.targetRole ?? null);
    } catch {
      // keep existing gaps on error
    } finally {
      setGapLoading(false);
    }
  };

  const s = data?.student;
  const driveMap = data?.driveMap ?? {};
  const applications = (data?.applications ?? []).filter((a) => a.status !== "eligible");
  const eligibleDrives = data?.eligibleDrives ?? [];
  const nextInterview = data?.nextInterview;
  const offers = data?.offers ?? [];

  // Sorted active drives for the dropdown (applied ones first)
  const activeDrives = Object.values(driveMap).filter((d) => d.status === "ACTIVE");
  const appliedDriveIds = new Set((data?.applications ?? []).map((a) => a.driveId));
  const sortedDrives = [
    ...activeDrives.filter((d) => appliedDriveIds.has(d.id)),
    ...activeDrives.filter((d) => !appliedDriveIds.has(d.id)),
  ];

  const displayedGaps = gapSkills ?? s?.skillGaps ?? [];

  const trendData = (s?.readinessTrend ?? []).map((v, i) => ({ label: `W${i + 1}`, value: v }));
  const band = s ? readinessBand(s.readiness) : null;

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!s}
      onRetry={reload}
      loadingFallback={<DashboardSkeleton />}
      emptyTitle="No student profile"
      emptyMessage="Your student profile isn't available yet. Once your profile is registered, your dashboard will populate here."
      className="mx-auto max-w-6xl"
    >
      {s && band && (
        <motion.div initial="hidden" animate="visible" variants={staggerContainer}>
          <motion.div variants={staggerItem}>
            <PageHeader
              eyebrow="Student Workspace"
              title={`Welcome, ${s.name.split(" ")[0]}.`}
              subtitle="Here's how your placement journey is progressing."
              actions={<Badge variant={band.tone}>{band.label}</Badge>}
            />
          </motion.div>

          <motion.div variants={staggerItem} className="grid grid-cols-2 gap-4 lg:grid-cols-6">
            <StatCard label="Readiness" value={s.readiness} suffix="/100" icon={Gauge} accent="violet" />
            <StatCard
              label="Placement Probability"
              value={s.placementProbability}
              suffix="%"
              icon={Target}
              accent="success"
            />
            <StatCard label="Eligible Drives" value={eligibleDrives.length} icon={ShieldCheck} accent="violet" />
            <StatCard label="Applications" value={applications.length} icon={ClipboardList} />
            <StatCard label="Offers" value={offers.length} icon={Award} accent="success" />
            <StatCard label="Risk Level" value={s.riskLevel} accent={s.riskLevel === "LOW" ? "success" : "risk"} />
          </motion.div>

          {nextInterview && (
            <motion.div variants={staggerItem} className="mt-4">
              <DepthCard className="flex flex-wrap items-center justify-between gap-3 border-violet/30 p-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet/10">
                    <CalendarClock className="h-4 w-4 text-violet-bright" />
                  </div>
                  <div>
                    <p className="text-sm font-medium">
                      Next up: {nextInterview.companyName} — {nextInterview.round}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {nextInterview.date} at {nextInterview.time} · {nextInterview.venue}
                    </p>
                  </div>
                </div>
                <Button asChild variant="outline" size="sm">
                  <Link href="/student/interview-prep">
                    Prepare <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </Button>
              </DepthCard>
            </motion.div>
          )}

          <div className="mt-5 grid gap-5 lg:grid-cols-3">
            {/* Digital twin summary */}
            <motion.div variants={staggerItem} className="lg:col-span-1">
              <DepthCard className="h-full p-5">
                <div className="flex items-center gap-2">
                  <Fingerprint className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Placement Digital Twin</CardTitle>
                </div>
                <div className="mt-4 flex flex-col items-center">
                  <ScoreRing value={s.readiness} size={128} label="Readiness" sublabel="out of 100" />
                </div>
                <div className="mt-4 space-y-1.5">
                  {Object.entries(s.breakdown).map(([k, v]) => (
                    <div key={k} className="flex items-center justify-between text-xs">
                      <span className="text-muted-foreground">{DIMENSION_LABELS[k] ?? k}</span>
                      <span className="font-medium tabular-nums">{v}</span>
                    </div>
                  ))}
                </div>
                <Button asChild variant="outline" size="sm" className="mt-4 w-full">
                  <Link href="/student/digital-twin">
                    View Digital Twin <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </Button>
              </DepthCard>
            </motion.div>

            {/* Skill gaps + recommended actions — with Company Dropdown (Option 1) */}
            <motion.div variants={staggerItem} className="lg:col-span-2">
              <DepthCard className="h-full p-5">
                {/* Card header row */}
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-start gap-2.5">
                    <Target className="h-4 w-4 text-violet-bright mt-0.5 shrink-0" />
                    <div>
                      <CardTitle>Skill Gaps &amp; Recommended Actions</CardTitle>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {gapCompany ? (
                          <>
                            Targeted for{" "}
                            <span className="font-semibold text-foreground">{gapCompany}</span>
                            {gapRole ? ` • ${gapRole}` : ""}
                          </>
                        ) : gapRole ? (
                          <>
                            Role:{" "}
                            <span className="font-semibold text-foreground">{gapRole}</span>
                          </>
                        ) : (
                          "Based on active campus placement benchmarks"
                        )}
                      </p>
                    </div>
                  </div>
                  <Button asChild variant="link" size="sm" className="px-0 shrink-0 self-start">
                    <Link href={gapDriveId ? `/student/skill-gap?drive=${gapDriveId}` : "/student/skill-gap"}>
                      View all
                    </Link>
                  </Button>
                </div>

                {/* Company / Drive selector dropdown */}
                {sortedDrives.length > 1 && (
                  <div className="mt-3">
                    <Select
                      value={gapDriveId}
                      onValueChange={handleDriveChange}
                      disabled={gapLoading}
                    >
                      <SelectTrigger
                        className="h-8 w-full border-border/60 bg-muted/30 text-xs"
                        id="skill-gap-drive-select"
                      >
                        <SelectValue placeholder="Select a company drive…" />
                      </SelectTrigger>
                      <SelectContent>
                        {sortedDrives.map((d) => (
                          <SelectItem key={d.id} value={d.id} className="text-xs">
                            <span className="font-medium">{d.companyName}</span>
                            {" "}
                            <span className="text-muted-foreground">• {d.role}</span>
                            {appliedDriveIds.has(d.id) && (
                              <Badge variant="default" className="ml-1.5 text-[9px] py-0 px-1">
                                Applied
                              </Badge>
                            )}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {/* Gap items or loading / empty state */}
                {gapLoading ? (
                  <div className="mt-5 flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Loading gaps…
                  </div>
                ) : displayedGaps.length > 0 ? (
                  <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    {displayedGaps.slice(0, 4).map((g) => (
                      <div key={g.skill} className="rounded-lg border border-border p-3 flex flex-col justify-between">
                        <div>
                          <div className="flex items-center justify-between gap-2">
                            <span className="text-sm font-medium text-foreground">{g.skill}</span>
                            <Badge variant={g.priority === "HIGH" ? "risk" : g.priority === "MEDIUM" ? "warning" : "muted"}>
                              {g.priority}
                            </Badge>
                          </div>
                          <p className="mt-1 text-[11px] text-muted-foreground">
                            {g.currentLabel || "Needs Practice"} → {g.targetLabel || "Required"}
                          </p>
                          <p className="mt-1.5 text-xs text-muted-foreground line-clamp-2">
                            {g.recommendedAction || g.whyItMatters}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="py-8 text-center text-sm text-muted-foreground">
                    No skill gaps identified yet. Add skills to your profile or select a different drive.
                  </p>
                )}
              </DepthCard>
            </motion.div>
          </div>

          {/* Readiness → Skill Gap → Opportunity signal line */}
          <motion.div variants={staggerItem} className="mt-4 flex items-center gap-3 px-1">
            <span className="shrink-0 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
              Readiness
            </span>
            <GlowLine className="w-8" />
            <span className="shrink-0 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
              Skill Gap
            </span>
            <GlowLine className="flex-1" />
            <span className="shrink-0 text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
              Opportunity
            </span>
          </motion.div>

          <div className="mt-4 grid gap-5 lg:grid-cols-3">
            {/* Readiness trend — chart when ≥2 pts, snapshot breakdown when single baseline only */}
            <motion.div variants={staggerItem} className="lg:col-span-2">
              <DepthCard className="p-5">
                <div className="flex items-center justify-between pb-3">
                  <CardTitle>
                    {trendData.length >= 2 ? "Readiness Trend" : "Readiness Snapshot"}
                  </CardTitle>
                  {trendData.length < 2 && (
                    <span className="text-[10px] text-muted-foreground tracking-wide">
                      Trend builds as you improve
                    </span>
                  )}
                </div>

                {trendData.length >= 2 ? (
                  <DataReveal>
                    <TrendAreaChart data={trendData} dataKey="value" xKey="label" />
                  </DataReveal>
                ) : s.breakdown ? (
                  /* Rich 5-dimension breakdown bars when no history yet */
                  <div className="mt-1 space-y-3">
                    {(
                      [
                        ["Academic",      s.breakdown.academic,      "bg-violet-500"],
                        ["Aptitude",      s.breakdown.aptitude,      "bg-blue-500"],
                        ["Communication", s.breakdown.communication, "bg-emerald-500"],
                        ["Projects",      s.breakdown.projects,      "bg-amber-500"],
                        ["Mock Interview",s.breakdown.interview,     "bg-rose-500"],
                      ] as [string, number, string][]
                    ).map(([label, score, color]) => (
                      <div key={label}>
                        <div className="flex items-center justify-between mb-1">
                          <span className="text-xs text-muted-foreground">{label}</span>
                          <span className="text-xs font-semibold text-foreground">{score ?? 0}/100</span>
                        </div>
                        <div className="h-1.5 w-full rounded-full bg-muted overflow-hidden">
                          <motion.div
                            className={`h-full rounded-full ${color}`}
                            initial={{ width: 0 }}
                            animate={{ width: `${score ?? 0}%` }}
                            transition={{ duration: 0.8, ease: "easeOut" }}
                          />
                        </div>
                      </div>
                    ))}
                    <p className="pt-1 text-[11px] text-muted-foreground">
                      Overall readiness: <span className="font-semibold text-foreground">{s.readiness}/100</span>
                      {" · "}Your trend will chart here week-over-week as scores update.
                    </p>
                  </div>
                ) : (
                  <p className="py-12 text-center text-sm text-muted-foreground">
                    Complete your profile to see your readiness snapshot.
                  </p>
                )}
              </DepthCard>
            </motion.div>

            {/* Eligible drives — deterministic eligibility over live drives. No match %
                is shown: there is no per-student match signal from the backend yet, and
                fabricating one would violate the single-source-of-truth rule. */}
            <motion.div variants={staggerItem}>
              <DepthCard className="h-full p-5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Briefcase className="h-4 w-4 text-violet-bright" />
                    <CardTitle>Eligible Drives</CardTitle>
                  </div>
                  <Button asChild variant="link" size="sm" className="px-0">
                    <Link href="/student/drives">View all</Link>
                  </Button>
                </div>
                {eligibleDrives.length > 0 ? (
                  <div className="mt-3 space-y-2.5">
                    {eligibleDrives.slice(0, 3).map((drive) => (
                      <Link
                        key={drive.id}
                        href={`/student/drives/${drive.id}`}
                        className="flex items-center justify-between gap-2 rounded-lg border border-border p-2.5 transition-colors hover:bg-accent"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">{drive.companyName}</p>
                          <p className="truncate text-xs text-muted-foreground">{drive.role}</p>
                        </div>
                        <ArrowUpRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                      </Link>
                    ))}
                  </div>
                ) : (
                  <p className="py-8 text-center text-sm text-muted-foreground">
                    No eligible drives right now.
                  </p>
                )}
              </DepthCard>
            </motion.div>
          </div>

          {/* Application progress */}
          <motion.div variants={staggerItem} className="mt-5">
            <DepthCard className="p-5">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ListChecks className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Application Progress</CardTitle>
                </div>
                <Button asChild variant="link" size="sm" className="px-0">
                  <Link href="/student/applications">View all</Link>
                </Button>
              </div>
              {applications.length > 0 ? (
                <div className="mt-3 divide-y divide-border">
                  {applications.map((a) => {
                    const drive = driveMap[a.driveId];
                    return (
                      <div key={a.id} className="flex items-center justify-between gap-3 py-2.5">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">
                            {drive?.companyName ?? "Unknown company"}
                          </p>
                          <p className="truncate text-xs text-muted-foreground">{drive?.role ?? "—"}</p>
                        </div>
                        <Badge variant={applicationStatusTone[a.status]}>
                          {applicationStatusLabel[a.status]}
                        </Badge>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  No applications yet.
                </p>
              )}
            </DepthCard>
          </motion.div>
        </motion.div>
      )}
    </AsyncBoundary>
  );
}
