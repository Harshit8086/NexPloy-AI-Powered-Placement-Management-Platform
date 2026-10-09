"use client";

import { useState, useMemo } from "react";
import {
  BarChart3,
  TrendingUp,
  IndianRupee,
  ShieldAlert,
  Users,
  Award,
  Percent,
  Download,
  RotateCcw,
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Search,
  Sparkles,
  Layers,
  Building2,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Progress } from "@/components/ui/progress";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { HorizontalBarChart } from "@/components/charts/HorizontalBarChart";
import { VerticalBarChart } from "@/components/charts/VerticalBarChart";
import { TrendAreaChart } from "@/components/charts/TrendAreaChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getAnalyticsForCampus, getSkillAnalytics } from "@/lib/api/analytics";
import { getCampus } from "@/lib/api/campuses";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import type { CampusAnalytics, SkillSupply } from "@/types";

export default function OfficerAnalyticsPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  // Live: campus dashboard analytics + skill demand/coverage + campus label.
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [analytics, skills, campus] = await Promise.all([
        getAnalyticsForCampus(campusId).catch(() => null),
        getSkillAnalytics(campusId).catch(() => null),
        getCampus(campusId).catch(() => undefined),
      ]);
      return { analytics, skillSupply: skills?.supply ?? [], campus };
    },
    [campusId],
    { analytics: null as CampusAnalytics | null, skillSupply: [] as SkillSupply[], campus: undefined },
  );

  // Export report to CSV
  const handleExportCsv = () => {
    if (!data.analytics) return;
    const a = data.analytics.snapshot;
    const campusName = data.campus?.name ?? "Campus";
    const dateStr = new Date().toISOString().split("T")[0];

    const lines: string[] = [
      `"NexPloy Placement Intelligence Report - ${campusName}"`,
      `"Generated On",${new Date().toLocaleString()}`,
      "",
      `"PLACEMENT EXECUTIVE SUMMARY"`,
      `"Total Cohort",${a.totalStudents}`,
      `"Placement Ready",${a.placementReady}`,
      `"Students Placed",${a.studentsPlaced}`,
      `"Placement Rate",${a.placementRate}%`,
      `"Average CTC",${a.averageCtc}`,
      `"Highest CTC",${a.highestCtc}`,
      `"Total Offers",${a.offersCount}`,
      `"Active Recruitment Drives",${a.activeDrives}`,
      `"Participating Recruiters",${a.recruitersCount}`,
      `"Students At Risk",${a.atRisk}`,
      "",
      `"BRANCH-WISE PERFORMANCE"`,
      `"Branch","Branch Code","Total Students","Students Placed","Placement Rate"`,
      ...data.analytics.branchStats.map(
        (b) => `"${b.branch}","${b.branchCode}",${b.totalStudents},${b.studentsPlaced},${b.placementRate}%`,
      ),
      "",
      `"PLACEMENT PIPELINE FUNNEL"`,
      `"Stage","Candidates Count"`,
      ...data.analytics.funnel.map((f) => `"${f.stage}",${f.count}`),
    ];

    const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", `Placement_Report_${campusName.replace(/\s+/g, "_")}_${dateStr}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    pushToast("Report Downloaded", `Exported placement report for ${campusName}.`);
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 pb-12">
      <PageHeader
        eyebrow="Intelligence"
        title="Placement Analytics"
        subtitle={`Real-time placement intelligence, cohort readiness, and recruitment trends across ${data.campus?.name ?? "the campus"}.`}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => reload()} disabled={loading}>
              <RotateCcw className={`h-3.5 w-3.5 mr-1.5 ${loading ? "animate-spin" : ""}`} /> Refresh
            </Button>
            <Button
              variant="glow"
              size="sm"
              onClick={handleExportCsv}
              disabled={!data.analytics || loading}
            >
              <Download className="h-3.5 w-3.5 mr-1.5" /> Export Report (CSV)
            </Button>
          </div>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!data.analytics}
        onRetry={reload}
        emptyTitle="No analytics available"
        emptyMessage="No placement data has been aggregated for this campus yet."
      >
        {data.analytics && (
          <AnalyticsView
            analytics={data.analytics}
            skillSupply={data.skillSupply}
          />
        )}
      </AsyncBoundary>
    </div>
  );
}

function AnalyticsView({
  analytics,
  skillSupply,
}: {
  analytics: CampusAnalytics;
  skillSupply: SkillSupply[];
}) {
  const a = analytics.snapshot;

  // Branch filtering & sorting state
  const [branchSearch, setBranchSearch] = useState("");
  const [branchSort, setBranchSort] = useState<"RATE_DESC" | "PLACED_DESC" | "NAME_ASC">("RATE_DESC");

  // Skill Pagination & Search state
  const [skillPage, setSkillPage] = useState(1);
  const [skillSearch, setSkillSearch] = useState("");
  const skillPageSize = 5;

  const applicationConversion = a.applications
    ? Math.round((a.offersCount / a.applications) * 100)
    : 0;
  const joinedStage = analytics.funnel.find((f) => f.stage === "Joined")?.count ?? 0;
  const offeredStage = analytics.funnel.find((f) => f.stage === "Offered")?.count ?? 1;
  const joiningRate = Math.round((joinedStage / offeredStage) * 100);
  const interviewedStage = analytics.funnel.find((f) => f.stage === "Interviewed")?.count ?? 1;
  const offerConversion = Math.round((offeredStage / interviewedStage) * 100);

  // Dynamic salary trend
  const salaryTrend = analytics.salaryTrend.map((s) => ({
    label: s.month,
    average: s.averageLpa,
    highest: s.highestLpa,
  }));

  // Filtered branches
  const filteredBranches = useMemo(() => {
    let list = analytics.branchStats.filter((b) => {
      if (!branchSearch.trim()) return true;
      const q = branchSearch.toLowerCase();
      return b.branch.toLowerCase().includes(q) || b.branchCode.toLowerCase().includes(q);
    });

    if (branchSort === "RATE_DESC") {
      list = [...list].sort((x, y) => y.placementRate - x.placementRate);
    } else if (branchSort === "PLACED_DESC") {
      list = [...list].sort((x, y) => y.studentsPlaced - x.studentsPlaced);
    } else if (branchSort === "NAME_ASC") {
      list = [...list].sort((x, y) => x.branch.localeCompare(y.branch));
    }

    return list;
  }, [analytics.branchStats, branchSearch, branchSort]);

  // Filtered & Paginated Skills
  const filteredSkills = useMemo(() => {
    if (!skillSearch.trim()) return skillSupply;
    const q = skillSearch.toLowerCase().trim();
    return skillSupply.filter((s) => s.skill.toLowerCase().includes(q));
  }, [skillSupply, skillSearch]);

  const totalSkillPages = Math.max(1, Math.ceil(filteredSkills.length / skillPageSize));
  const currentSkillPage = Math.min(skillPage, totalSkillPages);

  const paginatedSkills = useMemo(() => {
    const start = (currentSkillPage - 1) * skillPageSize;
    return filteredSkills.slice(start, start + skillPageSize);
  }, [filteredSkills, currentSkillPage, skillPageSize]);

  // Recruiter offers dynamic max
  const recruiterData = analytics.recruiterEngagement.map((r) => ({
    label: r.company,
    value: r.offers,
    drives: r.drives,
  }));

  return (
    <div className="space-y-6">
      {/* Automated Placement Intelligence Alerts */}
      {analytics.alerts && analytics.alerts.length > 0 && (
        <DepthCard className="p-4 border-warning/30 bg-warning/5">
          <div className="flex items-center gap-2 mb-2.5">
            <Sparkles className="h-4 w-4 text-warning" />
            <span className="text-xs font-semibold text-foreground uppercase tracking-wider">
              Automated Placement Intelligence Alerts
            </span>
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            {analytics.alerts.map((alert) => (
              <div
                key={alert.id}
                className="flex items-start gap-2.5 rounded-lg border border-border/80 bg-card/60 p-2.5 text-xs"
              >
                <AlertTriangle
                  className={`h-4 w-4 shrink-0 mt-0.5 ${
                    alert.severity === "HIGH"
                      ? "text-risk"
                      : alert.severity === "MEDIUM"
                        ? "text-warning"
                        : "text-violet-bright"
                  }`}
                />
                <div className="min-w-0">
                  <div className="flex items-center gap-1.5">
                    <Badge
                      variant={
                        alert.severity === "HIGH"
                          ? "risk"
                          : alert.severity === "MEDIUM"
                            ? "warning"
                            : "muted"
                      }
                      className="text-[10px] px-1.5 py-0"
                    >
                      {alert.severity}
                    </Badge>
                  </div>
                  <p className="mt-1 text-foreground/90 font-medium leading-relaxed">{alert.message}</p>
                </div>
              </div>
            ))}
          </div>
        </DepthCard>
      )}

      {/* Primary KPI Metrics Cards */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-5">
        <StatCard
          label="Placement Rate"
          value={a.placementRate}
          suffix="%"
          icon={TrendingUp}
          accent="violet"
          trend={`${a.studentsPlaced} of ${a.totalStudents} placed`}
          trendPositive={a.placementRate >= 60}
        />
        <StatCard
          label="Average CTC"
          value={a.averageCtc}
          icon={IndianRupee}
          accent="success"
        />
        <StatCard
          label="Highest CTC"
          value={a.highestCtc}
          icon={Award}
          accent="success"
        />
        <StatCard
          label="Cohort At Risk"
          value={a.atRisk}
          icon={ShieldAlert}
          accent="risk"
          trend={a.atRisk > 0 ? "Needs intervention" : "All on track"}
          trendPositive={a.atRisk === 0}
        />
        <StatCard
          label="Placement Ready"
          value={a.placementReady}
          icon={CheckCircle2}
          accent="violet"
          trend={`${a.activeDrives} active drives`}
          trendPositive={true}
        />
      </div>

      {/* Conversion Funnel Mini Metrics */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard label="Total Applications" value={a.applications} icon={Users} />
        <StatCard label="Total Offers Extended" value={a.offersCount} icon={Award} accent="success" />
        <StatCard
          label="Application → Offer"
          value={applicationConversion}
          suffix="%"
          icon={Percent}
          accent="violet"
          trend="Overall funnel yield"
        />
        <StatCard
          label="Offer → Joining"
          value={joiningRate}
          suffix="%"
          icon={Percent}
          accent="violet"
          trend="Acceptance conversion"
        />
      </div>

      {/* Full Placement Funnel Visualizer (NEW) */}
      <DepthCard className="p-5">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Layers className="h-4 w-4 text-violet-bright" />
            <CardTitle className="text-base font-semibold">End-to-End Placement Funnel</CardTitle>
          </div>
          <span className="text-xs text-muted-foreground">
            {a.totalStudents.toLocaleString()} total cohort candidates
          </span>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {analytics.funnel.map((f, idx) => {
            const prevCount = idx > 0 ? analytics.funnel[idx - 1].count : null;
            const stepConversion = prevCount && prevCount > 0 ? Math.round((f.count / prevCount) * 100) : null;

            return (
              <div
                key={f.stage}
                className="relative rounded-lg border border-border/80 bg-muted/20 p-3.5 transition hover:border-violet/40"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                    {f.stage}
                  </span>
                  {idx < analytics.funnel.length - 1 && (
                    <ArrowRight className="h-3.5 w-3.5 text-muted-foreground/40 hidden lg:block" />
                  )}
                </div>
                <p className="mt-2 text-2xl font-bold tabular-nums text-foreground">
                  {f.count.toLocaleString()}
                </p>
                <div className="mt-2 flex items-center justify-between text-[11px] text-muted-foreground">
                  <span>
                    {a.totalStudents > 0
                      ? `${Math.round((f.count / a.totalStudents) * 100)}% of cohort`
                      : "—"}
                  </span>
                  {stepConversion !== null && (
                    <span className="font-medium text-violet-bright tabular-nums">
                      {stepConversion}% step
                    </span>
                  )}
                </div>
                <Progress
                  value={a.totalStudents > 0 ? (f.count / a.totalStudents) * 100 : 0}
                  className="mt-2 h-1.5"
                />
              </div>
            );
          })}
        </div>
      </DepthCard>

      {/* Charts Grid: Offer Pipeline & Salary Trend */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-2">
        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <BarChart3 className="h-4 w-4 text-violet-bright" />
                <CardTitle className="text-base">Monthly Offer Pipeline</CardTitle>
              </div>
              <Badge variant="outline" className="text-xs">
                {analytics.offerPipeline.reduce((acc, curr) => acc + curr.offers, 0)} total offers
              </Badge>
            </div>
            <VerticalBarChart
              data={analytics.offerPipeline}
              xKey="month"
              yKey="offers"
              colorVar="--success"
            />
            <p className="mt-2 text-xs text-muted-foreground">
              Interview → offer conversion:{" "}
              <span className="font-semibold text-foreground">{offerConversion}%</span>
            </p>
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <CardTitle className="text-base">Salary Trend (LPA)</CardTitle>
              <Badge variant="muted" className="text-xs">
                Highest: ₹{Math.max(0, ...salaryTrend.map((s) => s.highest))} LPA
              </Badge>
            </div>
            <TrendAreaChart
              data={salaryTrend}
              dataKey="average"
              xKey="label"
              height={220}
              domain={[0, "auto"]}
            />
            {salaryTrend.length > 0 && (
              <p className="mt-2 text-xs text-muted-foreground">
                Average salary progressed from{" "}
                <span className="font-semibold text-foreground">
                  ₹{salaryTrend[0].average} LPA
                </span>{" "}
                to{" "}
                <span className="font-semibold text-foreground">
                  ₹{salaryTrend[salaryTrend.length - 1].average} LPA
                </span>{" "}
                this placement cycle.
              </p>
            )}
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* Charts Grid: Recruiter Engagement & Student Readiness Bands */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-2">
        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Building2 className="h-4 w-4 text-violet-bright" />
                <CardTitle className="text-base">Recruiter Engagement (Top Hires)</CardTitle>
              </div>
              <Badge variant="muted" className="text-xs">
                {recruiterData.length} active recruiters
              </Badge>
            </div>
            <HorizontalBarChart
              data={recruiterData}
              xKey="value"
              yKey="label"
              height={260}
              domain={[0, "auto"]}
            />
            <p className="mt-2 text-xs text-muted-foreground">
              Confirmed candidate offers extended per recruiter organization across all campus drives.
            </p>
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-3 flex items-center justify-between">
              <CardTitle className="text-base">Cohort Readiness Score Bands</CardTitle>
              <Badge variant="outline" className="text-xs">
                {analytics.readinessDistribution.reduce((acc, curr) => acc + curr.count, 0)} scored
              </Badge>
            </div>
            <div className="space-y-3.5 my-auto pt-1">
              {analytics.readinessDistribution.map((band) => {
                const totalScored = Math.max(
                  1,
                  analytics.readinessDistribution.reduce((acc, curr) => acc + curr.count, 0),
                );
                const sharePct = Math.round((band.count / totalScored) * 100);

                return (
                  <div key={band.band} className="space-y-1">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-foreground">Score Band: {band.band}</span>
                      <span className="text-muted-foreground tabular-nums">
                        {band.count} candidates ({sharePct}%)
                      </span>
                    </div>
                    <Progress
                      value={sharePct}
                      className="h-2"
                    />
                  </div>
                );
              })}
              {analytics.readinessDistribution.length === 0 && (
                <p className="py-8 text-center text-xs text-muted-foreground">
                  Readiness scores have not been aggregated for this cohort yet.
                </p>
              )}
            </div>
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* Skill Analytics — Demand vs. Cohort Coverage */}
      <Card className="p-5 border border-border">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle className="text-base">Skill Analytics — Recruiter Demand vs. Cohort Coverage</CardTitle>
            <p className="text-xs text-muted-foreground mt-0.5">
              Comparing industry demand against batch competency coverage ({filteredSkills.length} total mapped).
            </p>
          </div>

          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="Search skill..."
                value={skillSearch}
                onChange={(e) => {
                  setSkillSearch(e.target.value);
                  setSkillPage(1);
                }}
                className="pl-8 text-xs h-8 w-40"
              />
            </div>
            <Badge variant="muted" className="text-xs shrink-0">
              Page {currentSkillPage} of {totalSkillPages}
            </Badge>
          </div>
        </div>

        <div className="space-y-3.5">
          {paginatedSkills.map((s) => {
            const gap = s.demand - s.cohortCoverage;
            return (
              <div key={s.skill} className="rounded-lg border border-border/60 bg-muted/10 p-3">
                <div className="mb-2 flex items-center justify-between text-xs">
                  <span className="font-semibold text-foreground text-sm">{s.skill}</span>
                  <Badge
                    variant={gap > 35 ? "risk" : gap > 15 ? "warning" : "success"}
                    className="text-[11px]"
                  >
                    {gap > 0 ? `Gap: -${gap}%` : "Sufficient Coverage"}
                  </Badge>
                </div>
                <SkillBar label="Recruiter Demand" value={s.demand} colorClass="bg-violet" />
                <div className="mt-2">
                  <SkillBar
                    label="Cohort Student Coverage"
                    value={s.cohortCoverage}
                    colorClass={s.cohortCoverage >= 65 ? "bg-success" : "bg-warning"}
                  />
                </div>
              </div>
            );
          })}
          {filteredSkills.length === 0 && (
            <p className="py-6 text-center text-xs text-muted-foreground">
              {skillSearch ? "No skills matched your search." : "No skill demand telemetry aggregated for this campus yet."}
            </p>
          )}
        </div>

        {/* Pagination Controls */}
        {totalSkillPages > 1 && (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 pt-3 border-t border-border/60 text-xs">
            <span className="text-muted-foreground">
              Showing {(currentSkillPage - 1) * skillPageSize + 1}–
              {Math.min(currentSkillPage * skillPageSize, filteredSkills.length)} of {filteredSkills.length} skills
            </span>

            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2.5 text-xs"
                disabled={currentSkillPage <= 1}
                onClick={() => setSkillPage((p) => Math.max(1, p - 1))}
              >
                <ChevronLeft className="h-3.5 w-3.5 mr-1" /> Previous
              </Button>

              <div className="flex items-center gap-1">
                {Array.from({ length: totalSkillPages }).map((_, idx) => {
                  const pNum = idx + 1;
                  return (
                    <button
                      key={pNum}
                      type="button"
                      onClick={() => setSkillPage(pNum)}
                      className={`h-7 w-7 rounded-md text-xs font-medium transition ${
                        pNum === currentSkillPage
                          ? "bg-violet-bright/15 text-violet-bright font-semibold border border-violet-bright/30"
                          : "text-muted-foreground hover:bg-muted/40 hover:text-foreground"
                      }`}
                    >
                      {pNum}
                    </button>
                  );
                })}
              </div>

              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2.5 text-xs"
                disabled={currentSkillPage >= totalSkillPages}
                onClick={() => setSkillPage((p) => Math.min(totalSkillPages, p + 1))}
              >
                Next <ChevronRight className="h-3.5 w-3.5 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Branch Analytics Table with Search and Sorting */}
      <Card className="overflow-x-auto p-5 border border-border">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle className="text-base">Branch-Wise Placement Performance</CardTitle>
            <p className="text-xs text-muted-foreground mt-0.5">
              Placement velocity and standing across academic departments.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            {/* Search */}
            <div className="relative">
              <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="Filter branch..."
                value={branchSearch}
                onChange={(e) => setBranchSearch(e.target.value)}
                className="pl-8 text-xs h-8 w-44"
              />
            </div>

            {/* Sorting */}
            <Select value={branchSort} onValueChange={(v) => setBranchSort(v as typeof branchSort)}>
              <SelectTrigger className="h-8 text-xs w-48" aria-label="Sort branches">
                <SelectValue placeholder="Sort Branches" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="RATE_DESC">Placement Rate (Highest)</SelectItem>
                <SelectItem value="PLACED_DESC">Students Placed (Most)</SelectItem>
                <SelectItem value="NAME_ASC">Branch Name (A-Z)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40 hover:bg-muted/40">
              <TableHead>Branch</TableHead>
              <TableHead>Code</TableHead>
              <TableHead>Placement Rate</TableHead>
              <TableHead>Students Placed</TableHead>
              <TableHead>Total Cohort</TableHead>
              <TableHead>Standing</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredBranches.map((b) => (
              <TableRow key={b.branchCode} className="transition hover:bg-muted/30">
                <TableCell className="font-semibold text-xs text-foreground">{b.branch}</TableCell>
                <TableCell className="text-xs text-muted-foreground">{b.branchCode}</TableCell>
                <TableCell>
                  <div className="flex items-center gap-2.5">
                    <span className="font-semibold text-xs text-violet-bright tabular-nums">
                      {b.placementRate}%
                    </span>
                    <Progress value={b.placementRate} className="h-1.5 w-20" />
                  </div>
                </TableCell>
                <TableCell className="text-xs tabular-nums font-medium">{b.studentsPlaced}</TableCell>
                <TableCell className="text-xs tabular-nums text-muted-foreground">{b.totalStudents}</TableCell>
                <TableCell>
                  <Badge
                    variant={
                      b.placementRate >= a.placementRate
                        ? "success"
                        : b.placementRate >= a.placementRate - 12
                          ? "warning"
                          : "risk"
                    }
                    className="text-[10px]"
                  >
                    {b.placementRate >= a.placementRate ? "At or above average" : "Below average"}
                  </Badge>
                </TableCell>
              </TableRow>
            ))}
            {filteredBranches.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="py-8 text-center text-xs text-muted-foreground">
                  No academic branches matched your search filter.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>

        <p className="mt-3 text-xs text-muted-foreground">
          Campus placement rate: <span className="font-semibold text-foreground">{a.placementRate}%</span>{" "}
          across {a.totalStudents.toLocaleString()} total students. All figures reflect live database aggregations.
        </p>
      </Card>
    </div>
  );
}
