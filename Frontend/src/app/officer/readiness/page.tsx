"use client";

import { useState } from "react";
import { Gauge, Users, ShieldCheck, ShieldAlert, Search, RefreshCw, ChevronLeft, ChevronRight } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { VerticalBarChart } from "@/components/charts/VerticalBarChart";
import { HorizontalBarChart } from "@/components/charts/HorizontalBarChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { listStudents } from "@/lib/api/students";
import { recomputeReadiness } from "@/lib/api/students";
import { getAnalyticsForCampus } from "@/lib/api/analytics";
import { getCampus } from "@/lib/api/campuses";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { readinessBand, riskTone } from "@/lib/status";
import type { Student, CampusAnalytics } from "@/types";

const DIMENSIONS = [
  { key: "academic", label: "Academics" },
  { key: "aptitude", label: "Aptitude" },
  { key: "communication", label: "Communication" },
  { key: "projects", label: "Projects" },
  { key: "interview", label: "Mock Interview" },
] as const;

export default function OfficerReadinessPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const [query, setQuery] = useState("");
  const [band, setBand] = useState("all");
  const [branchFilter, setBranchFilter] = useState("all");
  const [batchFilter, setBatchFilter] = useState("all");
  const [riskFilter, setRiskFilter] = useState("all");
  // Table pagination over the filtered rows (client-side, like the Students directory page).
  const [pageSize, setPageSize] = useState<number>(25);
  const [page, setPage] = useState<number>(1);
  // Which student's readiness is being recomputed right now (disables just that row's button).
  const [recomputingId, setRecomputingId] = useState<string | null>(null);

  // Live: roster + campus analytics (readiness distribution, branch stats) + campus label.
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [students, analytics, campus] = await Promise.all([
        listStudents(),
        getAnalyticsForCampus(campusId).catch(() => null),
        getCampus(campusId).catch(() => undefined),
      ]);
      return { students: students.filter((s) => s.campusId === campusId), analytics, campus };
    },
    [campusId],
    { students: [] as Student[], analytics: null as CampusAnalytics | null, campus: undefined },
  );
  const roster = data.students;
  const campus = data.campus;
  const readinessDistribution = data.analytics?.readinessDistribution ?? [];
  const branchStats = data.analytics?.branchStats ?? [];

  /** Cohort dimension averages come from the visible roster, so the chart
   * can never disagree with the table below it. */
  const cohortAverages = DIMENSIONS.map((d) => ({
    label: d.label,
    value: roster.length
      ? Math.round(roster.reduce((sum, s) => sum + s.breakdown[d.key], 0) / roster.length)
      : 0,
  }));

  const filtered = roster
    .filter((s) => (query ? s.name.toLowerCase().includes(query.toLowerCase()) : true))
    .filter((s) => (band === "all" ? true : readinessBand(s.readiness).label === band))
    .filter((s) => (branchFilter === "all" ? true : s.branchCode === branchFilter))
    .filter((s) => (batchFilter === "all" ? true : s.batch === batchFilter))
    .filter((s) => (riskFilter === "all" ? true : s.riskLevel === riskFilter))
    .sort((a, b) => b.readiness - a.readiness);

  // Client-side pagination over the filtered rows (mirrors the Students directory page).
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const paginated = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const ready = roster.filter((s) => s.readiness >= 80).length;
  const needsWork = roster.filter((s) => s.readiness < 65).length;
  const avgReadiness = roster.length
    ? Math.round(roster.reduce((n, s) => n + s.readiness, 0) / roster.length)
    : 0;

  const bands = Array.from(new Set(roster.map((s) => readinessBand(s.readiness).label)));
  const branches = Array.from(new Set(roster.map((s) => s.branchCode))).sort();
  const batches = Array.from(new Set(roster.map((s) => s.batch))).sort();

  // Reset to page 1 whenever any filter changes.
  const handleFilterChange = (setter: (val: string) => void, val: string) => {
    setter(val);
    setPage(1);
  };

  // Officer recompute: rederive readiness from the student's existing data (assessments, academics,
  // projects) on the server, then reload the roster so the score/breakdown columns reflect the result.
  async function handleRecompute(studentId: string) {
    setRecomputingId(studentId);
    try {
      await recomputeReadiness(studentId);
      await reload();
    } finally {
      setRecomputingId(null);
    }
  }

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Students"
        title="Student Readiness Overview"
        subtitle={`Employability across the ${campus?.name ?? "campus"} cohort, dimension by dimension.`}
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={roster.length === 0}
        onRetry={reload}
        emptyTitle="No readiness data"
        emptyMessage="No students are registered for this campus yet."
      >
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Students Tracked" value={roster.length} icon={Users} />
        <StatCard label="Placement Ready" value={ready} icon={ShieldCheck} accent="success" />
        <StatCard label="Needs Support" value={needsWork} icon={ShieldAlert} accent="risk" />
        <StatCard label="Average Readiness" value={avgReadiness} suffix="/100" icon={Gauge} accent="violet" />
      </div>

      <DataReveal stagger className="mt-5 grid gap-5 lg:grid-cols-2">
        <DataRevealItem>
          <DepthCard className="p-5">
            <div className="mb-3 flex items-center justify-between">
              <CardTitle>Cohort Dimension Averages</CardTitle>
            </div>
            <HorizontalBarChart data={cohortAverages} xKey="value" yKey="label" height={280} />
          </DepthCard>
        </DataRevealItem>
        <DataRevealItem>
          <DepthCard className="p-5">
            <div className="mb-3 flex items-center justify-between">
              <CardTitle>Readiness Distribution</CardTitle>
            </div>
            <VerticalBarChart
              data={readinessDistribution}
              xKey="band"
              yKey="count"
              height={280}
            />
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      <Card className="mt-5 p-5">
        <CardTitle className="mb-3">Branch Readiness Benchmarks</CardTitle>
        <div className="grid gap-3 sm:grid-cols-2">
          {branchStats.map((b) => (
            <SkillBar
              key={b.branchCode}
              label={`${b.branch} (${b.placementRate}% placed)`}
              value={b.placementRate}
              colorClass={
                b.placementRate >= 80 ? "bg-success" : b.placementRate >= 65 ? "bg-violet" : "bg-warning"
              }
            />
          ))}
        </div>
        <p className="mt-3 text-xs text-muted-foreground">
          Aggregate campus figures from campus analytics.
        </p>
      </Card>

      <div className="mb-4 mt-5 flex flex-wrap items-center gap-3">
        <div className="relative min-w-[200px] flex-1">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search student..."
            className="pl-8"
            value={query}
            onChange={(e) => handleFilterChange(setQuery, e.target.value)}
            aria-label="Search students"
          />
        </div>
        <Select value={band} onValueChange={(val) => handleFilterChange(setBand, val)}>
          <SelectTrigger className="w-48" aria-label="Filter by readiness band">
            <SelectValue placeholder="Readiness band" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All bands</SelectItem>
            {bands.map((b) => (
              <SelectItem key={b} value={b}>
                {b}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={branchFilter} onValueChange={(val) => handleFilterChange(setBranchFilter, val)}>
          <SelectTrigger className="w-40" aria-label="Filter by branch">
            <SelectValue placeholder="Branch" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All branches</SelectItem>
            {branches.map((b) => (
              <SelectItem key={b} value={b}>
                {b}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={batchFilter} onValueChange={(val) => handleFilterChange(setBatchFilter, val)}>
          <SelectTrigger className="w-40" aria-label="Filter by batch">
            <SelectValue placeholder="Batch" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All batches</SelectItem>
            {batches.map((b) => (
              <SelectItem key={b} value={b}>
                {b}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={riskFilter} onValueChange={(val) => handleFilterChange(setRiskFilter, val)}>
          <SelectTrigger className="w-40" aria-label="Filter by risk level">
            <SelectValue placeholder="Risk level" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All risk levels</SelectItem>
            {(["HIGH", "MEDIUM", "LOW"] as const).map((r) => (
              <SelectItem key={r} value={r}>
                {r}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Card className="overflow-x-auto p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Student</TableHead>
              <TableHead>Readiness</TableHead>
              <TableHead>Band</TableHead>
              <TableHead>Academics</TableHead>
              <TableHead>Aptitude</TableHead>
              <TableHead>Communication</TableHead>
              <TableHead>Projects</TableHead>
              <TableHead>Mock Interview</TableHead>
              <TableHead>Risk</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {paginated.map((s) => {
              const b = readinessBand(s.readiness);
              return (
                <TableRow key={s.id}>
                  <TableCell>
                    <div className="flex items-center gap-2.5">
                      <Avatar className="h-7 w-7">
                        <AvatarFallback className="text-[10px]">{s.avatarInitials}</AvatarFallback>
                      </Avatar>
                      <span className="font-medium">{s.name}</span>
                    </div>
                  </TableCell>
                  <TableCell className="font-semibold tabular-nums">{s.readiness}</TableCell>
                  <TableCell>
                    <Badge variant={b.tone}>{b.label}</Badge>
                  </TableCell>
                  {DIMENSIONS.map((d) => (
                    <TableCell key={d.key} className="tabular-nums">
                      {s.breakdown[d.key]}
                    </TableCell>
                  ))}
                  <TableCell>
                    <Badge variant={riskTone[s.riskLevel]}>{s.riskLevel}</Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleRecompute(s.id)}
                      disabled={recomputingId !== null}
                      aria-label={`Recompute readiness for ${s.name}`}
                    >
                      <RefreshCw
                        className={`h-3.5 w-3.5 ${recomputingId === s.id ? "animate-spin" : ""}`}
                      />
                      {recomputingId === s.id ? "Recomputing" : "Recompute"}
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
            {filtered.length === 0 && (
              <TableRow>
                <TableCell colSpan={10} className="py-10 text-center text-sm text-muted-foreground">
                  No students match these filters.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>

          {/* Table pagination & rows per page */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border/60 px-4 py-3 text-xs text-muted-foreground">
            <div className="flex items-center gap-2">
              <span>Rows per page:</span>
              <Select
                value={String(pageSize)}
                onValueChange={(val) => {
                  setPageSize(Number(val));
                  setPage(1);
                }}
              >
                <SelectTrigger className="h-7 w-16 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                  <SelectItem value="100">100</SelectItem>
                </SelectContent>
              </Select>
              <span className="ml-2">
                Showing {filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1} to{" "}
                {Math.min(currentPage * pageSize, filtered.length)} of {filtered.length} students
              </span>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs">
                Page {currentPage} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                className="h-7 w-7 p-0"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={currentPage <= 1}
                aria-label="Previous page"
              >
                <ChevronLeft className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="h-7 w-7 p-0"
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={currentPage >= totalPages}
                aria-label="Next page"
              >
                <ChevronRight className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
      </Card>
      </AsyncBoundary>
    </div>
  );
}
