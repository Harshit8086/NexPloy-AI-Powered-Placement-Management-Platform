"use client";

import { useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  Users,
  ShieldAlert,
  Briefcase,
  Award,
  TrendingUp,
  AlertTriangle,
  CalendarClock,
  ArrowUpRight,
  ClipboardList,
  GraduationCap,
  Plus,
  Download,
  IndianRupee,
  Building2,
  Target,
  Sparkles,
  CheckCircle2,
  Clock,
  FileText,
  FileCheck2,
} from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { PageHeader } from "@/components/layout/PageHeader";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { GlowLine } from "@/components/intelligence/GlowLine";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { VerticalBarChart } from "@/components/charts/VerticalBarChart";
import { HorizontalBarChart } from "@/components/charts/HorizontalBarChart";
import { DonutChart } from "@/components/charts/DonutChart";
import { TrendAreaChart } from "@/components/charts/TrendAreaChart";
import { showcaseEntrance } from "@/lib/motion-variants";
import { cn } from "@/lib/utils";
import { getAnalyticsForCampus, getSkillAnalytics } from "@/lib/api/analytics";
import { getCampus } from "@/lib/api/campuses";
import { listDrives } from "@/lib/api/drives";
import { listOffers } from "@/lib/api/offers";
import { listDocuments } from "@/lib/api/documents";
import { listScheduleConflicts, listScheduleEvents, formatEventTime } from "@/lib/api/schedules";
import { listRiskEntriesByCampus } from "@/lib/api/risk";
import { listStudents } from "@/lib/api/students";
import { listPlacementCycles } from "@/lib/api/placement-cycles";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { riskTone } from "@/lib/status";
import type { CampusAnalytics, CampusSummary, DocumentItem, Drive, Offer, PlacementCycle, SkillSupply, Student } from "@/types";

/** Zeroed analytics used until the live/mock fetch resolves, so first paint never sees undefined. */
const EMPTY_ANALYTICS: CampusAnalytics = {
  snapshot: {
    totalStudents: 0, placementReady: 0, atRisk: 0, activeDrives: 0, applications: 0,
    offersCount: 0, studentsPlaced: 0, placementRate: 0, recruitersCount: 0,
    averageCtc: "—", highestCtc: "—",
  },
  branchStats: [], funnel: [], offerPipeline: [], salaryTrend: [],
  readinessDistribution: [], riskDistribution: [], recruiterEngagement: [], alerts: [],
};

function branchColor(value: number) {
  if (value >= 80) return "142 71% 45%";
  if (value >= 65) return "38 92% 50%";
  return "0 84% 60%";
}

export function CommandCenter() {
  const campusId = useAppStore((s) => s.activeCampusId);

  const { data: campus } = useApiResource<CampusSummary | null>(
    () => (campusId ? getCampus(campusId).then((c) => c ?? null) : Promise.resolve(null)),
    [campusId],
    null,
  );

  // Dashboard analytics from the API layer (GET .../analytics/dashboard)
  const { data: analytics } = useApiResource<CampusAnalytics>(
    () => (campusId ? getAnalyticsForCampus(campusId) : Promise.resolve(EMPTY_ANALYTICS)),
    [campusId],
    EMPTY_ANALYTICS,
  );
  const a = analytics.snapshot;

  // Live skill demand & cohort supply
  const { data: skillData } = useApiResource<{ demand: { skill: string; demandScore: number }[]; supply: SkillSupply[] }>(
    () => (campusId ? getSkillAnalytics(campusId).then((r) => ({ demand: r.demand, supply: r.supply ?? [] })) : Promise.resolve({ demand: [], supply: [] })),
    [campusId],
    { demand: [], supply: [] },
  );

  // Tab state for Drives (Active vs Upcoming)
  const [driveTab, setDriveTab] = useState<"ACTIVE" | "UPCOMING">("ACTIVE");

  const { data: allDrives } = useApiResource<Drive[]>(() => listDrives(), [], []);
  const activeDrives = allDrives.filter((d) => (!campusId || d.campusId === campusId) && d.status === "ACTIVE");
  const upcomingDrives = allDrives.filter(
    (d) => (!campusId || d.campusId === campusId) && (d.status === "DRAFT" || (d.status as string) === "SCHEDULED"),
  );

  // Live offers data (Requirement F.3 & F.8)
  const { data: allOffers } = useApiResource<Offer[]>(() => listOffers(), [], []);

  // Live documents data (Requirement F.8)
  const { data: allDocs } = useApiResource<DocumentItem[]>(() => listDocuments(), [], []);

  // Campus risk roll-up (latest per student)
  const { data: campusRisk } = useApiResource(
    () => (campusId ? listRiskEntriesByCampus(campusId) : Promise.resolve([])),
    [campusId],
    [],
  );
  const highRisk = campusRisk.filter((r) => r.level === "HIGH");

  // Student directory joined for high-risk names & campus scoping
  const { data: allStudents } = useApiResource<Student[]>(() => listStudents(), [], []);
  const studentById = new Map(allStudents.map((s) => [s.id, s] as const));

  // Scoped offers breakdown (accepted, pending, received, declined)
  const campusStudentIds = new Set(allStudents.filter((s) => !campusId || s.campusId === campusId).map((s) => s.id));
  const campusOffers = allOffers.filter((o) => campusStudentIds.size === 0 || campusStudentIds.has(o.studentId));
  const activeOffers = campusOffers.length > 0 ? campusOffers : allOffers;

  const offersAccepted = activeOffers.filter((o) => o.status === "ACCEPTED").length;
  const offersPending = activeOffers.filter((o) => o.status === "PENDING ACCEPTANCE").length;
  const offersReceived = activeOffers.filter((o) => o.status === "OFFER RECEIVED").length;
  const offersDeclined = activeOffers.filter((o) => o.status === "DECLINED").length;

  // Documentation and Joining metrics
  const campusDocs = allDocs.filter((d) => campusStudentIds.size === 0 || campusStudentIds.has(d.studentId));
  const activeDocs = campusDocs.length > 0 ? campusDocs : allDocs;

  const docsVerified = activeDocs.filter((d) => d.status === "VERIFIED").length;
  const docsUnderReview = activeDocs.filter(
    (d) => d.status === "UNDER REVIEW" || d.status === "PENDING" || d.status === "UPLOADED",
  ).length;
  const docsNeedsUpdate = activeDocs.filter((d) => d.status === "NEEDS UPDATE").length;
  const docVerificationPct = activeDocs.length > 0 ? Math.round((docsVerified / activeDocs.length) * 100) : 0;

  const acceptedOffersList = activeOffers.filter((o) => o.status === "ACCEPTED");
  const joinedCandidates = acceptedOffersList.filter((o) => o.joined).length;
  const pendingJoining = acceptedOffersList.filter((o) => !o.joined).length;
  const offerDocsVerifiedCount = acceptedOffersList.filter((o) => o.documentsVerified).length;
  const joiningPct = acceptedOffersList.length > 0 ? Math.round((joinedCandidates / acceptedOffersList.length) * 100) : 0;

  // Placement cycles for target and progress
  const { data: cycles } = useApiResource<PlacementCycle[]>(
    () => (campusId ? listPlacementCycles(campusId) : Promise.resolve([])),
    [campusId],
    [],
  );
  const activeCycle = cycles.find((c) => c.status === "ACTIVE") ?? cycles[0];

  // Live schedule conflicts + events
  const { data: schedule } = useApiResource(
    async () => {
      if (!campusId) return { conflicts: [], events: [] };
      const [conflicts, events] = await Promise.all([
        listScheduleConflicts(campusId),
        listScheduleEvents(),
      ]);
      return { conflicts, events };
    },
    [campusId],
    { conflicts: [], events: [] },
  );
  const driveNameById = new Map(allDrives.map((d) => [d.id, d.companyName] as const));
  const eventById = new Map(schedule.events.map((e) => [e.id, e] as const));

  // CSV Report Export Handler
  const handleExportSummary = () => {
    const rows = [
      ["Campus", campus?.name ?? "Campus"],
      ["Report Date", new Date().toLocaleDateString()],
      ["", ""],
      ["Metric", "Value"],
      ["Total Students", String(a.totalStudents)],
      ["Placement Ready", String(a.placementReady)],
      ["At Risk", String(a.atRisk)],
      ["Active Drives", String(activeDrives.length || a.activeDrives)],
      ["Upcoming Drives", String(upcomingDrives.length)],
      ["Applications Submitted", String(a.applications)],
      ["Total Offers Extended", String(a.offersCount || activeOffers.length)],
      ["Offers Accepted", String(offersAccepted)],
      ["Offers Pending", String(offersPending + offersReceived)],
      ["Offers Declined", String(offersDeclined)],
      ["Students Placed", String(a.studentsPlaced)],
      ["Candidates Joined", String(joinedCandidates)],
      ["Documents Verified", String(docsVerified)],
      ["Documents Under Review", String(docsUnderReview)],
      ["Placement Rate (%)", `${a.placementRate}%`],
      ["Average CTC", a.averageCtc],
      ["Highest CTC", a.highestCtc],
    ];
    const csvContent = "data:text/csv;charset=utf-8," + rows.map((e) => e.join(",")).join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `Placement_Report_${campus?.name?.replace(/\s+/g, "_") ?? "Campus"}_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Salary trend formatted for TrendAreaChart
  const salaryTrendData = analytics.salaryTrend.map((s) => ({
    label: s.month,
    average: s.averageLpa,
    highest: s.highestLpa,
  }));

  // Target goal calculation (Institutional target default: 85%)
  const TARGET_RATE = 85;
  const targetPct = Math.min(100, Math.round((a.placementRate / TARGET_RATE) * 100));

  return (
    <motion.div
      initial="hidden"
      animate="visible"
      variants={showcaseEntrance}
      className="mx-auto max-w-7xl space-y-6"
    >
      {/* 1. Header with Quick Action Bar */}
      <PageHeader
        eyebrow={`${campus?.name ?? "Campus"} · ${activeCycle ? activeCycle.academicYear : "2025–2026"} Cycle`}
        title="Placement Command Center"
        subtitle="Live institutional intelligence across readiness, drives, outcomes, schedules and risk."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleExportSummary}
              className="border-border hover:bg-surface-elevated"
            >
              <Download className="h-4 w-4 text-muted-foreground" />
              <span>Export CSV</span>
            </Button>
            <Button variant="outline" size="sm" asChild>
              <Link href="/officer/orchestrator" className="flex items-center gap-1.5">
                <CalendarClock className="h-4 w-4 text-violet-bright" />
                <span>Orchestrator</span>
              </Link>
            </Button>
            <Button variant="outline" size="sm" asChild>
              <Link href="/officer/risk" className="flex items-center gap-1.5">
                <ShieldAlert className="h-4 w-4 text-risk" />
                <span>Risk Radar</span>
              </Link>
            </Button>
            <Button variant="glow" size="sm" asChild>
              <Link href="/officer/drives" className="flex items-center gap-1.5">
                <Plus className="h-4 w-4" />
                <span>New Drive</span>
              </Link>
            </Button>
          </div>
        }
      />

      {/* 2. Placement Cycle Target & Institutional Progress Banner */}
      <DepthCard className="p-4 sm:p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Target className="h-4 w-4 text-violet-bright" />
              <span className="text-sm font-semibold text-foreground">
                {activeCycle ? activeCycle.name : "Institutional Placement Drive Goal"}
              </span>
              <Badge variant="default" className="text-[10px] uppercase">
                {activeCycle ? activeCycle.status : "ACTIVE"}
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground">
              Target Institutional Benchmark:{" "}
              <span className="font-medium text-foreground">{TARGET_RATE}% Placed</span> · Current Progress:{" "}
              <span className="font-semibold text-violet-bright">{a.placementRate}%</span> ({a.studentsPlaced} of {a.totalStudents} placed)
            </p>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-6">
            <div className="w-full sm:w-60">
              <div className="mb-1 flex items-center justify-between text-xs">
                <span className="text-muted-foreground">Goal Achievement</span>
                <span className="font-semibold text-foreground">{targetPct}%</span>
              </div>
              <Progress value={targetPct} indicatorClassName="bg-violet-bright" className="h-2.5 bg-muted" />
            </div>

            <div className="flex items-center gap-3 border-t border-border pt-2 sm:border-l sm:border-t-0 sm:pl-4 sm:pt-0">
              <div className="text-left">
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground">Avg CTC</div>
                <div className="text-sm font-semibold text-foreground">{a.averageCtc}</div>
              </div>
              <div className="h-7 w-px bg-border" />
              <div className="text-left">
                <div className="text-[10px] uppercase tracking-wider text-muted-foreground">Highest CTC</div>
                <div className="text-sm font-semibold text-success">{a.highestCtc}</div>
              </div>
              <Button variant="ghost" size="sm" asChild className="ml-1 text-xs text-violet-bright">
                <Link href="/officer/cycles">
                  Cycle Details <ArrowUpRight className="h-3 w-3" />
                </Link>
              </Button>
            </div>
          </div>
        </div>
      </DepthCard>

      {/* 3. Structured Primary KPI Cards (Cohort, Pipeline, Offers, Outcomes) */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Card 1: Student Cohort Health */}
        <DepthCard className="flex flex-col justify-between p-4 transition-all hover:border-violet/40">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Student Cohort
              </span>
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
                <Users className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2.5 flex items-baseline gap-2">
              <span className="text-3xl font-bold tracking-tight text-foreground">{a.totalStudents}</span>
              <span className="text-xs text-muted-foreground">Total Enrolled</span>
            </div>
          </div>
          <div className="mt-4 flex items-center justify-between border-t border-border/60 pt-3 text-xs">
            <div className="flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-success" />
              <span className="text-muted-foreground">Ready:</span>
              <span className="font-semibold text-foreground">{a.placementReady}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-risk" />
              <span className="text-muted-foreground">At Risk:</span>
              <span className="font-semibold text-risk">{a.atRisk}</span>
            </div>
          </div>
        </DepthCard>

        {/* Card 2: Recruitment Pipeline */}
        <DepthCard className="flex flex-col justify-between p-4 transition-all hover:border-violet/40">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Recruitment Pipeline
              </span>
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
                <Briefcase className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2.5 flex items-baseline gap-2">
              <span className="text-3xl font-bold tracking-tight text-foreground">
                {activeDrives.length || a.activeDrives}
              </span>
              <span className="text-xs text-muted-foreground">
                Active ({upcomingDrives.length} Upcoming)
              </span>
            </div>
          </div>
          <div className="mt-4 flex items-center justify-between border-t border-border/60 pt-3 text-xs">
            <div className="flex items-center gap-1.5">
              <ClipboardList className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-muted-foreground">Applications:</span>
              <span className="font-semibold text-foreground">{a.applications}</span>
            </div>
            <Link href="/officer/drives" className="text-[11px] text-violet-bright hover:underline">
              View all drives →
            </Link>
          </div>
        </DepthCard>

        {/* Card 3: Offers & Status Breakdown (Requirement F.3) */}
        <DepthCard className="flex flex-col justify-between p-4 transition-all hover:border-violet/40">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Offers Extended
              </span>
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-success/10 text-success">
                <Award className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2.5 flex items-baseline gap-2">
              <span className="text-3xl font-bold tracking-tight text-foreground">
                {a.offersCount || activeOffers.length}
              </span>
              <span className="text-xs text-muted-foreground">Total Extended</span>
            </div>
          </div>
          <div className="mt-3 space-y-2 border-t border-border/60 pt-2.5 text-xs">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-muted-foreground">Avg CTC: </span>
                <span className="font-semibold text-foreground">{a.averageCtc}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Highest: </span>
                <span className="font-semibold text-success">{a.highestCtc}</span>
              </div>
            </div>
            <div className="flex items-center justify-between border-t border-border/40 pt-1.5 text-[11px]">
              <span className="flex items-center gap-1 font-medium text-success">
                <CheckCircle2 className="h-3 w-3" /> {offersAccepted} Accepted
              </span>
              <span className="flex items-center gap-1 font-medium text-warning">
                <Clock className="h-3 w-3" /> {offersPending + offersReceived} Pending
              </span>
              <Link href="/officer/offers" className="text-violet-bright hover:underline">
                Details →
              </Link>
            </div>
          </div>
        </DepthCard>

        {/* Card 4: Placement Outcome & Rate */}
        <DepthCard className="flex flex-col justify-between p-4 transition-all hover:border-violet/40">
          <div>
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Placement Outcome
              </span>
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
                <TrendingUp className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2.5 flex items-baseline gap-2">
              <span className="text-3xl font-bold tracking-tight text-foreground">{a.placementRate}%</span>
              <span className="text-xs text-muted-foreground">Placed ({a.studentsPlaced}/{a.totalStudents})</span>
            </div>
          </div>
          <div className="mt-4 flex items-center justify-between border-t border-border/60 pt-3 text-xs">
            <div className="flex items-center gap-1.5">
              <GraduationCap className="h-3.5 w-3.5 text-success" />
              <span className="text-muted-foreground">Placed:</span>
              <span className="font-semibold text-foreground">{a.studentsPlaced}</span>
            </div>
            <Badge variant={a.placementRate >= 60 ? "success" : "warning"} className="text-[10px] px-1.5 py-0">
              {a.placementRate >= 60 ? "On Track" : "In Progress"}
            </Badge>
          </div>
        </DepthCard>
      </div>

      {/* 4. Placement Funnel & Salary Progression Trend */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-3">
        <DataRevealItem className="lg:col-span-2">
          <DepthCard className="p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-violet-bright" />
                <CardTitle>Placement Pipeline &amp; Conversion Funnel</CardTitle>
              </div>
              <span className="text-xs text-muted-foreground">From Applied to Placed</span>
            </div>
            <VerticalBarChart data={analytics.funnel} xKey="stage" yKey="count" height={260} />
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <IndianRupee className="h-4 w-4 text-success" />
                <CardTitle>Salary Trend (LPA)</CardTitle>
              </div>
            </div>
            {salaryTrendData.length > 0 ? (
              <>
                <TrendAreaChart data={salaryTrendData} dataKey="average" xKey="label" height={210} colorVar="--success" />
                <p className="mt-2 text-xs text-muted-foreground">
                  Monthly average CTC curve across completed drives. Peak reached{" "}
                  <span className="font-semibold text-foreground">
                    ₹{Math.max(...salaryTrendData.map((s) => s.highest))} LPA
                  </span>.
                </p>
              </>
            ) : (
              <div className="flex h-52 flex-col items-center justify-center text-center text-xs text-muted-foreground">
                <Award className="mb-2 h-6 w-6 text-muted-foreground/60" />
                <p>Salary trend will populate as offers are finalized this cycle.</p>
              </div>
            )}
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* 5. Branch Standing & Recruiter Engagement */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-2">
        <DataRevealItem>
          <DepthCard className="p-5">
            <div className="mb-3 flex items-center justify-between">
              <CardTitle>Branch Readiness &amp; Placement Rate</CardTitle>
              <span className="text-xs text-muted-foreground">% Placed</span>
            </div>
            <HorizontalBarChart
              data={analytics.branchStats.map((b) => ({ label: b.branch, value: b.placementRate }))}
              xKey="value"
              yKey="label"
              colorByValue={branchColor}
              height={260}
            />
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Building2 className="h-4 w-4 text-violet-bright" />
                <CardTitle>Top Hiring Partners (Offers Released)</CardTitle>
              </div>
              <Link href="/officer/recruiters" className="text-xs text-violet-bright hover:underline">
                Recruiter Directory →
              </Link>
            </div>
            {analytics.recruiterEngagement.length > 0 ? (
              <HorizontalBarChart
                data={analytics.recruiterEngagement.map((r) => ({ label: r.company, value: r.offers }))}
                xKey="value"
                yKey="label"
                height={260}
              />
            ) : (
              <div className="flex h-56 items-center justify-center text-xs text-muted-foreground">
                No recruiter offer data recorded yet.
              </div>
            )}
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* 6. Skill Demand vs Cohort Coverage Gap & Conversion Analysis (Requirement F.4) */}
      <DataReveal>
        <DepthCard className="p-5">
          <div className="mb-4 flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
            <div>
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-violet-bright" />
                <CardTitle>Skill-wise Conversion Analysis: Market Demand vs. Student Cohort Supply</CardTitle>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                Evaluates cohort skill coverage against recruiter market demand to identify conversion bottlenecks and supply readiness.
              </p>
            </div>
            <div className="flex items-center gap-3 text-xs">
              <span className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-violet" /> Recruiter Demand
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-2.5 w-2.5 rounded-full bg-success" /> Cohort Supply
              </span>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {skillData.supply.length > 0 ? (
              skillData.supply.slice(0, 6).map((s) => {
                const gap = s.demand - s.cohortCoverage;
                const conversionEfficiency = Math.min(100, Math.round((s.cohortCoverage / Math.max(1, s.demand)) * 100));
                return (
                  <div key={s.skill} className="rounded-lg border border-border bg-surface-elevated/40 p-3.5 space-y-2.5">
                    <div className="flex items-center justify-between text-xs font-medium">
                      <span className="text-foreground">{s.skill}</span>
                      <Badge variant={gap > 35 ? "risk" : gap > 15 ? "warning" : "success"} className="text-[10px]">
                        {gap > 35 ? `Bottleneck (+${gap}%)` : gap > 15 ? `Gap: +${gap}%` : "High Conversion"}
                      </Badge>
                    </div>
                    <SkillBar label="Market Demand" value={s.demand} colorClass="bg-violet" />
                    <SkillBar
                      label="Campus Supply"
                      value={s.cohortCoverage}
                      colorClass={s.cohortCoverage >= 65 ? "bg-success" : "bg-warning"}
                    />
                    <div className="flex items-center justify-between border-t border-border/40 pt-1.5 text-[11px] text-muted-foreground">
                      <span>Conversion Efficiency</span>
                      <span
                        className={cn(
                          "font-semibold",
                          conversionEfficiency >= 75
                            ? "text-success"
                            : conversionEfficiency >= 50
                              ? "text-warning"
                              : "text-risk",
                        )}
                      >
                        {conversionEfficiency}%
                      </span>
                    </div>
                  </div>
                );
              })
            ) : skillData.demand.length > 0 ? (
              skillData.demand.slice(0, 6).map((d) => (
                <div key={d.skill} className="rounded-lg border border-border bg-surface-elevated/40 p-3 space-y-2">
                  <div className="flex items-center justify-between text-xs font-medium">
                    <span className="text-foreground">{d.skill}</span>
                    <span className="text-muted-foreground">{d.demandScore}%</span>
                  </div>
                  <SkillBar label="Requirement Score" value={d.demandScore} colorClass="bg-violet" />
                </div>
              ))
            ) : (
              <p className="col-span-full py-6 text-center text-xs text-muted-foreground">
                No skill requirements parsed from active drives yet.
              </p>
            )}
          </div>
        </DepthCard>
      </DataReveal>

      {/* 7. Readiness, Risk Overview & Offer Pipeline */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-3">
        <DataRevealItem>
          <DepthCard className="p-5">
            <CardTitle className="mb-3">Readiness Distribution</CardTitle>
            <VerticalBarChart
              data={analytics.readinessDistribution}
              xKey="band"
              yKey="count"
              height={220}
            />
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="flex h-full flex-col items-center p-5">
            <CardTitle className="mb-3 self-start">Risk Overview</CardTitle>
            <DonutChart
              data={analytics.riskDistribution.map((r) => ({ name: r.level, value: r.count }))}
              colors={["--success", "--warning", "--risk"]}
              centerValue={a.atRisk}
              centerLabel="At Risk"
            />
            <div className="mt-3 flex gap-3 text-[11px]">
              <span className="flex items-center gap-1 text-muted-foreground">
                <span className="h-2 w-2 rounded-full bg-success" /> Low
              </span>
              <span className="flex items-center gap-1 text-muted-foreground">
                <span className="h-2 w-2 rounded-full bg-warning" /> Medium
              </span>
              <span className="flex items-center gap-1 text-muted-foreground">
                <span className="h-2 w-2 rounded-full bg-risk" /> High
              </span>
            </div>
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="p-5">
            <CardTitle className="mb-3">Monthly Offer Volume</CardTitle>
            <VerticalBarChart
              data={analytics.offerPipeline}
              xKey="month"
              yKey="offers"
              height={220}
              colorVar="--success"
            />
            <p className="mt-2 text-xs text-muted-foreground">
              Total Offers Extended: <span className="font-semibold text-foreground">{a.offersCount}</span>
            </p>
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* 8. Active and Upcoming Drives + Actionable High-Risk Students (Requirement F.2) */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-2">
        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Briefcase className="h-4 w-4 text-violet-bright" />
                <CardTitle>Drives Pipeline</CardTitle>
              </div>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => setDriveTab("ACTIVE")}
                  className={cn(
                    "rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                    driveTab === "ACTIVE"
                      ? "bg-violet/15 text-violet-bright font-semibold"
                      : "text-muted-foreground hover:bg-surface-elevated hover:text-foreground",
                  )}
                >
                  Active ({activeDrives.length})
                </button>
                <button
                  type="button"
                  onClick={() => setDriveTab("UPCOMING")}
                  className={cn(
                    "rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                    driveTab === "UPCOMING"
                      ? "bg-violet/15 text-violet-bright font-semibold"
                      : "text-muted-foreground hover:bg-surface-elevated hover:text-foreground",
                  )}
                >
                  Upcoming ({upcomingDrives.length})
                </button>
                <Link
                  href="/officer/drives"
                  className="ml-1 flex items-center gap-0.5 text-xs text-violet-bright hover:underline"
                >
                  All <ArrowUpRight className="h-3 w-3" />
                </Link>
              </div>
            </div>
            <div className="space-y-2.5">
              {driveTab === "ACTIVE" ? (
                activeDrives.length > 0 ? (
                  activeDrives.slice(0, 5).map((d) => (
                    <Link
                      key={d.id}
                      href="/officer/drives"
                      className="flex items-center justify-between gap-2 rounded-lg border border-border p-3 transition-colors hover:bg-surface-elevated/60"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-foreground">{d.companyName}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {d.role} · Closes {d.deadline}
                        </p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <Badge variant="muted">
                          {d.expectedHiring} openings
                        </Badge>
                      </div>
                    </Link>
                  ))
                ) : (
                  <p className="py-8 text-center text-xs text-muted-foreground">
                    No active drives running on this campus right now.
                  </p>
                )
              ) : upcomingDrives.length > 0 ? (
                upcomingDrives.slice(0, 5).map((d) => (
                  <Link
                    key={d.id}
                    href="/officer/drives"
                    className="flex items-center justify-between gap-2 rounded-lg border border-border/80 bg-surface-elevated/30 p-3 transition-colors hover:bg-surface-elevated/60"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="truncate text-sm font-medium text-foreground">{d.companyName}</p>
                        <Badge variant="outline" className="text-[10px] uppercase text-violet-bright border-violet/30">
                          {d.status}
                        </Badge>
                      </div>
                      <p className="truncate text-xs text-muted-foreground">
                        {d.role} · {d.driveDate ? `Drive Date: ${d.driveDate}` : `Scheduled: ${d.deadline}`}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <Badge variant="muted">
                        {d.expectedHiring} openings
                      </Badge>
                    </div>
                  </Link>
                ))
              ) : (
                <p className="py-8 text-center text-xs text-muted-foreground">
                  No upcoming or scheduled drives found.
                </p>
              )}
            </div>
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShieldAlert className="h-4 w-4 text-risk" />
                <CardTitle>Highest-Risk Students</CardTitle>
              </div>
              <Link
                href="/officer/risk"
                className="flex items-center gap-1 text-xs text-violet-bright hover:underline"
              >
                Open Risk Radar <ArrowUpRight className="h-3 w-3" />
              </Link>
            </div>
            <div className="space-y-2.5">
              {highRisk.slice(0, 5).map((r) => {
                const student = studentById.get(r.studentId);
                if (!student) return null;
                return (
                  <div
                    key={r.studentId}
                    className="flex items-center justify-between gap-3 rounded-lg border border-risk/30 bg-risk/5 p-3"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="truncate text-sm font-medium text-foreground">{student.name}</p>
                        <span className="text-[11px] text-muted-foreground">({student.branch})</span>
                      </div>
                      <p className="truncate text-xs text-muted-foreground">{r.primaryReason}</p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <Badge variant="risk">
                        {student.readiness}
                      </Badge>
                      <Button variant="ghost" size="sm" asChild className="h-7 px-2 text-xs text-violet-bright hover:bg-violet/10">
                        <Link href="/officer/risk">
                          Intervene
                        </Link>
                      </Button>
                    </div>
                  </div>
                );
              })}
              {highRisk.length === 0 && (
                <p className="py-8 text-center text-xs text-muted-foreground">
                  No high-risk students flagged on this campus.
                </p>
              )}
            </div>
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* 9. Documentation & Joining-Status Summary (Requirement F.8) */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-2">
        {/* Sub-Card A: Student Documentation Pipeline */}
        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileCheck2 className="h-4 w-4 text-violet-bright" />
                <CardTitle>Document Verification Summary</CardTitle>
              </div>
              <Link
                href="/officer/documents"
                className="flex items-center gap-1 text-xs text-violet-bright hover:underline"
              >
                All documents <ArrowUpRight className="h-3 w-3" />
              </Link>
            </div>

            <div className="space-y-4">
              <div className="rounded-lg border border-border/80 bg-surface-elevated/40 p-3.5">
                <div className="mb-1.5 flex items-center justify-between text-xs">
                  <span className="text-muted-foreground font-medium">Compliance Verification Rate</span>
                  <span className="font-semibold text-foreground">{docVerificationPct}%</span>
                </div>
                <Progress value={docVerificationPct} indicatorClassName="bg-success" className="h-2 bg-muted" />
                <p className="mt-2 text-[11px] text-muted-foreground">
                  {docsVerified} of {activeDocs.length} total student documents verified &amp; cleared.
                </p>
              </div>

              <div className="grid grid-cols-3 gap-2 text-center">
                <div className="rounded-lg border border-success/30 bg-success/5 p-2.5">
                  <div className="text-lg font-bold text-success">{docsVerified}</div>
                  <div className="text-[10px] uppercase font-medium text-muted-foreground">Verified</div>
                </div>
                <div className="rounded-lg border border-warning/30 bg-warning/5 p-2.5">
                  <div className="text-lg font-bold text-warning">{docsUnderReview}</div>
                  <div className="text-[10px] uppercase font-medium text-muted-foreground">Under Review</div>
                </div>
                <div className="rounded-lg border border-risk/30 bg-risk/5 p-2.5">
                  <div className="text-lg font-bold text-risk">{docsNeedsUpdate}</div>
                  <div className="text-[10px] uppercase font-medium text-muted-foreground">Needs Update</div>
                </div>
              </div>

              <div className="flex items-center justify-between pt-1 text-xs text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <FileText className="h-3.5 w-3.5 text-muted-foreground" />
                  <span>Mark sheets, Resumes &amp; Certificates</span>
                </span>
                <Button variant="ghost" size="sm" asChild className="h-7 px-2 text-xs text-violet-bright hover:bg-violet/10">
                  <Link href="/officer/documents">Review Queue →</Link>
                </Button>
              </div>
            </div>
          </DepthCard>
        </DataRevealItem>

        {/* Sub-Card B: Candidate Joining & Onboarding Status */}
        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <GraduationCap className="h-4 w-4 text-success" />
                <CardTitle>Joining &amp; Onboarding Status</CardTitle>
              </div>
              <Link
                href="/officer/offers"
                className="flex items-center gap-1 text-xs text-violet-bright hover:underline"
              >
                Manage offers <ArrowUpRight className="h-3 w-3" />
              </Link>
            </div>

            <div className="space-y-4">
              <div className="rounded-lg border border-border/80 bg-surface-elevated/40 p-3.5">
                <div className="mb-1.5 flex items-center justify-between text-xs">
                  <span className="text-muted-foreground font-medium">Joined Conversion Ratio</span>
                  <span className="font-semibold text-foreground">{joiningPct}%</span>
                </div>
                <Progress value={joiningPct} indicatorClassName="bg-violet-bright" className="h-2 bg-muted" />
                <p className="mt-2 text-[11px] text-muted-foreground">
                  {joinedCandidates} joined out of {offersAccepted} accepted placements.
                </p>
              </div>

              <div className="grid grid-cols-3 gap-2 text-center">
                <div className="rounded-lg border border-success/30 bg-success/5 p-2.5">
                  <div className="text-lg font-bold text-success">{joinedCandidates}</div>
                  <div className="text-[10px] uppercase font-medium text-muted-foreground">Joined</div>
                </div>
                <div className="rounded-lg border border-warning/30 bg-warning/5 p-2.5">
                  <div className="text-lg font-bold text-warning">{pendingJoining}</div>
                  <div className="text-[10px] uppercase font-medium text-muted-foreground">Pending Join</div>
                </div>
                <div className="rounded-lg border border-violet/30 bg-violet/5 p-2.5">
                  <div className="text-lg font-bold text-violet-bright">
                    {offerDocsVerifiedCount}/{offersAccepted || activeOffers.length}
                  </div>
                  <div className="text-[10px] uppercase font-medium text-muted-foreground">Offer Docs OK</div>
                </div>
              </div>

              <div className="flex items-center justify-between pt-1 text-xs text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <CheckCircle2 className="h-3.5 w-3.5 text-success" />
                  <span>Pre-joining verification pipeline</span>
                </span>
                <Button variant="ghost" size="sm" asChild className="h-7 px-2 text-xs text-violet-bright hover:bg-violet/10">
                  <Link href="/officer/offers">Track Joining →</Link>
                </Button>
              </div>
            </div>
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* 10. Live Operational Radar: Schedule Conflicts & Critical Alerts */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-2">
        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <CalendarClock className="h-4 w-4 text-risk" />
                <CardTitle>Upcoming Conflicts ({schedule.conflicts.length})</CardTitle>
              </div>
              <Link
                href="/officer/orchestrator"
                className="flex items-center gap-1 text-xs text-violet-bright hover:underline"
              >
                Resolve in Orchestrator <ArrowUpRight className="h-3 w-3" />
              </Link>
            </div>
            <GlowLine tone="risk" className="mb-3" />
            {schedule.conflicts.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-8 text-center">
                <CheckCircle2 className="h-7 w-7 text-success mb-2" />
                <p className="text-xs text-foreground font-medium">All venues and panels conflict-free</p>
                <p className="text-[11px] text-muted-foreground">Schedule optimizer detected zero hard violations.</p>
              </div>
            ) : (
              <div className="space-y-2">
                {schedule.conflicts.slice(0, 4).map((c, i) => {
                  const event = eventById.get(c.eventId);
                  const label = event ? driveNameById.get(event.driveId) ?? event.driveId : c.eventId;
                  return (
                    <div
                      key={`${c.eventId}-${c.conflictingEventId ?? c.resourceId ?? i}`}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-risk/30 bg-risk/5 p-3"
                    >
                      <div className="min-w-0">
                        <p className="text-sm font-medium">
                          {label}
                          {event ? ` · ${formatEventTime(event.startsAt)}` : ""}
                          {event?.venueName ? ` · ${event.venueName}` : ""}
                        </p>
                        <p className="text-xs text-muted-foreground">{c.message}</p>
                      </div>
                      <Badge variant="risk">{c.type.replace(/_/g, " ")}</Badge>
                    </div>
                  );
                })}
              </div>
            )}
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 text-warning" />
                <CardTitle>System &amp; Cohort Alerts ({analytics.alerts.length})</CardTitle>
              </div>
              <Link
                href="/officer/notifications"
                className="text-xs text-violet-bright hover:underline"
              >
                All notifications →
              </Link>
            </div>
            <div className="space-y-2.5">
              {analytics.alerts.slice(0, 4).map((alert) => (
                <div
                  key={alert.id}
                  className={cn(
                    "flex items-start gap-2.5 rounded-lg border border-border p-2.5",
                    alert.severity === "HIGH" &&
                      "border-risk/30 shadow-[0_0_16px_-6px_hsl(var(--risk)/0.5)]",
                  )}
                >
                  <Badge variant={riskTone[alert.severity]} className="mt-0.5 shrink-0">
                    {alert.severity}
                  </Badge>
                  <p className="text-xs text-foreground">{alert.message}</p>
                </div>
              ))}
              {analytics.alerts.length === 0 && (
                <p className="py-8 text-center text-xs text-muted-foreground">
                  No active system alerts for this campus.
                </p>
              )}
            </div>
          </DepthCard>
        </DataRevealItem>
      </DataReveal>
    </motion.div>
  );
}
