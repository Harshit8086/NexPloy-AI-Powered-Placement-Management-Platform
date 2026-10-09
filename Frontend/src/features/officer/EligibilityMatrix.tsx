"use client";

import { useEffect, useMemo, useState } from "react";
import { motion } from "framer-motion";
import {
  Search,
  ShieldCheck,
  ShieldX,
  ListChecks,
  Users,
  Percent,
  Zap,
  Sparkles,
  ChevronLeft,
  ChevronRight,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  RotateCcw,
  SlidersHorizontal,
} from "lucide-react";
import { toast } from "sonner";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { EligibilityChecklist } from "@/components/intelligence/EligibilityChecklist";
import { StagedRunner } from "@/components/intelligence/StagedRunner";
import { IntelligencePulse, IntelligencePulseBadge } from "@/components/intelligence/IntelligencePulse";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { listDrivesByCampus, checkEligibility } from "@/lib/api/drives";
import { listStudentsByCampus } from "@/lib/api/students";
import { listMatchesForDrive, autoShortlistDrive } from "@/lib/api/matches";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { getDriveSkillFit } from "@/lib/api/skillgap";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility, primaryFailureReason } from "@/lib/eligibility";
import { buildAdvisory } from "@/lib/eligibility-advisory";
import { eligibilityCheckSteps } from "@/lib/simulate";
import { applicationStatusLabel, applicationStatusTone, employabilityTier } from "@/lib/status";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";

type Filter = "all" | "eligible" | "not-eligible";
type RunState = "idle" | "running" | "done";
type SortField = "eligible" | "match" | "cgpa" | "name" | "backlogs";
type SortDir = "asc" | "desc";

/** Backend eligibility result keyed by studentId. */
type BackendEligibilityMap = Map<string, { eligible: boolean; failureReasons: string[] }>;

export function EligibilityMatrix() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const [driveId, setDriveId] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  const [branch, setBranch] = useState("all");
  const [query, setQuery] = useState("");
  const [minCgpa, setMinCgpa] = useState<number>(0);
  const [minMatch, setMinMatch] = useState<number>(0);
  const [appStatus, setAppStatus] = useState<string>("all");
  const [sortBy, setSortBy] = useState<SortField>("eligible");
  const [sortDir, setSortDir] = useState<SortDir>("desc");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);
  const [selectedStudentId, setSelectedStudentId] = useState<string | null>(null);
  const [runState, setRunState] = useState<RunState>("idle");
  const [shortlistOpen, setShortlistOpen] = useState(false);
  const [minFit, setMinFit] = useState(75);
  const [shortlisting, setShortlisting] = useState(false);
  /** Stores backend-verified eligibility results after "Run Eligibility Check" completes. */
  const [backendResults, setBackendResults] = useState<BackendEligibilityMap>(new Map());

  // Live: campus drives (non-draft) + campus roster.
  const { data: base, loading, error, reload } = useApiResource(
    async () => {
      const [campusDrives, campusStudents] = await Promise.all([
        listDrivesByCampus(campusId),
        listStudentsByCampus(campusId),
      ]);
      return {
        drives: campusDrives.filter((d) => d.status !== "DRAFT"),
        students: campusStudents,
      };
    },
    [campusId],
    { drives: [], students: [] },
  );

  const activeDriveId = driveId || base.drives[0]?.id || "";
  const drive = base.drives.find((d) => d.id === activeDriveId);
  const campusStudents = base.students;

  // Reset backend results and pagination on drive change.
  useEffect(() => {
    setBackendResults(new Map());
    setPage(1);
  }, [activeDriveId]);

  // Reset page when any search/filter criteria change.
  useEffect(() => {
    setPage(1);
  }, [query, branch, filter, minCgpa, minMatch, appStatus, sortBy, sortDir]);

  // Live: matches + applications for the selected drive, keyed by student id.
  const { data: driveData, reload: reloadDrive } = useApiResource(
    async () => {
      if (!activeDriveId) return { matches: [], applications: [] };
      const [matches, applications] = await Promise.all([
        listMatchesForDrive(activeDriveId),
        listApplicationsByDrive(activeDriveId),
      ]);
      return { matches, applications };
    },
    [activeDriveId],
    { matches: [], applications: [] },
  );
  const fitByStudent = useMemo(
    () => new Map(driveData.matches.map((m) => [m.studentId, m.overallFit] as const)),
    [driveData.matches],
  );
  const matchByStudent = useMemo(
    () => new Map(driveData.matches.map((m) => [m.studentId, m] as const)),
    [driveData.matches],
  );
  const appByStudent = useMemo(
    () => new Map(driveData.applications.map((a) => [a.studentId, a] as const)),
    [driveData.applications],
  );

  // Live: catalog-driven skill fit for the student open in the drawer (advisory context only —
  // never affects the eligibility verdict). Degrades to undefined when the role has no catalog
  // entry or the endpoint 404s, so the advisory block self-suppresses rather than erroring.
  const { data: driveSkillFit } = useApiResource(
    async () => {
      if (!selectedStudentId || !activeDriveId) return undefined;
      try {
        return await getDriveSkillFit(selectedStudentId, activeDriveId);
      } catch {
        return undefined;
      }
    },
    [selectedStudentId, activeDriveId],
    undefined,
  );

  const branches = useMemo(
    () => Array.from(new Set(campusStudents.map((s) => s.branchCode))).sort(),
    [campusStudents],
  );

  const rows = useMemo(
    () =>
      !drive
        ? []
        : campusStudents
            .map((student) => {
              // After "Run Eligibility Check" completes, backend results override the frontend engine.
              const backend = backendResults.get(student.id);
              const frontend = evaluateEligibility(student, drive);
              const eligible = backend !== undefined ? backend.eligible : frontend.eligible;
              const reason =
                backend !== undefined
                  ? (backend.eligible ? "All criteria met" : backend.failureReasons[0] ?? "Not eligible")
                  : primaryFailureReason(student, drive);
              return {
                student,
                eligible,
                reason,
                match: fitByStudent.get(student.id),
                application: appByStudent.get(student.id),
              };
            })
            .filter((r) => (branch === "all" ? true : r.student.branchCode === branch))
            .filter((r) =>
              filter === "all" ? true : filter === "eligible" ? r.eligible : !r.eligible,
            )
            .filter((r) =>
              query ? r.student.name.toLowerCase().includes(query.toLowerCase()) : true,
            )
            .filter((r) => (minCgpa === 0 ? true : r.student.cgpa >= minCgpa))
            .filter((r) => (minMatch === 0 ? true : (r.match ?? 0) >= minMatch))
            .filter((r) => {
              if (appStatus === "all") return true;
              if (appStatus === "not-applied") return !r.application;
              if (appStatus === "has-applied") return !!r.application;
              if (appStatus === "selected-offer") {
                return (
                  r.application &&
                  (r.application.status === "selected" ||
                    r.application.status === "offer" ||
                    r.application.status === "accepted")
                );
              }
              return r.application?.status === appStatus;
            })
            .sort((a, b) => {
              let cmp = 0;
              switch (sortBy) {
                case "eligible":
                  cmp = Number(a.eligible) - Number(b.eligible);
                  if (cmp === 0) {
                    cmp = (a.match ?? 0) - (b.match ?? 0);
                  }
                  break;
                case "match":
                  cmp = (a.match ?? 0) - (b.match ?? 0);
                  break;
                case "cgpa":
                  cmp = a.student.cgpa - b.student.cgpa;
                  break;
                case "name":
                  cmp = a.student.name.localeCompare(b.student.name);
                  break;
                case "backlogs":
                  cmp = a.student.backlogs - b.student.backlogs;
                  break;
              }
              return sortDir === "asc" ? cmp : -cmp;
            }),
    [
      campusStudents,
      drive,
      branch,
      filter,
      query,
      minCgpa,
      minMatch,
      appStatus,
      sortBy,
      sortDir,
      fitByStudent,
      appByStudent,
      backendResults,
    ],
  );

  // Stats: prefer backend-verified results if available (after Run Eligibility Check), else frontend.
  const allForDrive = drive
    ? campusStudents.map((s) => {
        const backend = backendResults.get(s.id);
        return backend !== undefined ? backend.eligible : evaluateEligibility(s, drive).eligible;
      })
    : [];
  const eligibleCount = allForDrive.filter(Boolean).length;
  const notEligibleCount = allForDrive.length - eligibleCount;
  const eligibilityRate = allForDrive.length
    ? Math.round((eligibleCount / allForDrive.length) * 100)
    : 0;

  const selected = selectedStudentId
    ? campusStudents.find((s) => s.id === selectedStudentId)
    : undefined;

  // Advisory context for the drawer — soft skill-gap + mock-interview signals, never the verdict.
  const advisory = useMemo(
    () =>
      selected
        ? buildAdvisory({
            student: selected,
            driveSkillFit,
            matchBreakdown: matchByStudent.get(selected.id)?.breakdown,
          })
        : null,
    [selected, driveSkillFit, matchByStudent],
  );

  // Auto-shortlist pool preview: eligible applicants with a match score at or above the fit floor.
  // The server re-verifies eligibility as the hard gate; this preview just sets expectations.
  const shortlistPool = useMemo(
    () => rows.filter((r) => r.eligible && r.application && (r.match ?? 0) >= minFit),
    [rows, minFit],
  );

  const runCheck = async () => {
    if (!drive || campusStudents.length === 0) return;
    setRunState("running");

    // Fetch eligibility from backend for all students in parallel while animation plays.
    const results = await Promise.allSettled(
      campusStudents.map((s) => checkEligibility(s.id, drive.id)),
    );

    const map: BackendEligibilityMap = new Map();
    campusStudents.forEach((s, i) => {
      const outcome = results[i];
      if (outcome.status === "fulfilled" && outcome.value !== undefined) {
        map.set(s.id, {
          eligible: outcome.value.eligible,
          failureReasons: outcome.value.failureReasons,
        });
      }
    });

    setBackendResults(map);
    // runState transitions to "done" when StagedRunner fires onDone — animation paces the reveal.
  };

  const runAutoShortlist = async () => {
    if (!activeDriveId) return;
    setShortlisting(true);
    try {
      const result = await autoShortlistDrive(activeDriveId, minFit);
      toast.success(
        `Auto-shortlist complete: ${result.shortlisted} shortlisted · ` +
          `${result.skippedIneligible} ineligible · ${result.skippedLowFit} below ${minFit}% fit.`,
      );
      setShortlistOpen(false);
      reloadDrive();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Auto-shortlist failed.");
    } finally {
      setShortlisting(false);
    }
  };

  // Pagination and sorting helpers:
  const totalItems = rows.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const safePage = Math.min(page, totalPages);
  const paginatedRows = useMemo(() => {
    const start = (safePage - 1) * pageSize;
    return rows.slice(start, start + pageSize);
  }, [rows, safePage, pageSize]);

  const handleSort = (field: SortField) => {
    if (sortBy === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortBy(field);
      setSortDir(field === "name" || field === "backlogs" ? "asc" : "desc");
    }
  };

  const hasActiveFilters =
    query !== "" ||
    branch !== "all" ||
    filter !== "all" ||
    minCgpa > 0 ||
    minMatch > 0 ||
    appStatus !== "all" ||
    sortBy !== "eligible" ||
    sortDir !== "desc";

  const resetFilters = () => {
    setQuery("");
    setBranch("all");
    setFilter("all");
    setMinCgpa(0);
    setMinMatch(0);
    setAppStatus("all");
    setSortBy("eligible");
    setSortDir("desc");
    setPage(1);
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Placement"
        title="Eligibility Engine"
        subtitle="Deterministic rule checks against each drive's published criteria — scored separately from the AI match."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              onClick={() => setShortlistOpen(true)}
              disabled={!drive}
            >
              <Sparkles className="h-4 w-4" /> Auto-shortlist recommended
            </Button>
            <Button variant="glow" onClick={runCheck} disabled={runState === "running"}>
              <Zap className="h-4 w-4" /> Run Eligibility Check
            </Button>
          </div>
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <Select value={activeDriveId} onValueChange={setDriveId}>
          <SelectTrigger className="w-full sm:w-72" aria-label="Select drive">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {base.drives.map((d) => (
              <SelectItem key={d.id} value={d.id}>
                {d.companyName} — {d.role}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {drive && (
          <Badge variant="muted">
            CGPA {drive.criteria.minCgpa}+ · {drive.criteria.allowedBranches.join("/")} · max{" "}
            {drive.criteria.maxBacklogs} backlog{drive.criteria.maxBacklogs === 1 ? "" : "s"} ·{" "}
            {drive.criteria.graduationYear}
          </Badge>
        )}
      </div>

      <IntelligencePulse active={runState === "running"} className="mb-5">
        {runState === "running" ? (
          <Card className="flex flex-col items-center gap-5 p-8">
            <IntelligencePulseBadge label="Checking eligibility" />
            <div className="w-full max-w-xs">
              <StagedRunner
                steps={eligibilityCheckSteps}
                onDone={() => setRunState("done")}
              />
            </div>
          </Card>
        ) : (
          <motion.div
            initial={runState === "done" ? { opacity: 0, y: 8 } : false}
            animate={{ opacity: 1, y: 0 }}
            className="grid grid-cols-2 gap-4 sm:grid-cols-4"
          >
            <StatCard label="Students Checked" value={allForDrive.length} icon={Users} />
            <StatCard label="Eligible" value={eligibleCount} icon={ShieldCheck} accent="success" />
            <StatCard label="Not Eligible" value={notEligibleCount} icon={ShieldX} accent="risk" />
            <StatCard
              label="Eligibility Rate"
              value={eligibilityRate}
              suffix="%"
              icon={Percent}
              accent="violet"
            />
          </motion.div>
        )}
      </IntelligencePulse>

      {/* Search & Multi-Tier Filter Controls */}
      <div className="mb-4 space-y-3">
        {/* Tier 1: Search, Branch, Quick Eligibility Switcher */}
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative min-w-[200px] flex-1">
            <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search student by name..."
              className="pl-8"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Search students"
            />
          </div>
          <Select value={branch} onValueChange={setBranch}>
            <SelectTrigger className="w-40" aria-label="Filter by branch">
              <SelectValue placeholder="Branch" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Branches</SelectItem>
              {branches.map((b) => (
                <SelectItem key={b} value={b}>
                  {b}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div className="flex gap-1.5">
            {(["all", "eligible", "not-eligible"] as Filter[]).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={cn(
                  "rounded-full border px-3 py-1.5 text-xs font-medium transition-colors",
                  filter === f
                    ? "border-violet/50 bg-violet/10 text-violet-bright"
                    : "border-border text-muted-foreground hover:bg-accent",
                )}
              >
                {f === "all" ? "All" : f === "eligible" ? "Eligible only" : "Not eligible"}
              </button>
            ))}
          </div>
        </div>

        {/* Tier 2: Advanced Criteria (CGPA Floor, Match Score Floor, Application Status, Reset) */}
        <div className="flex flex-wrap items-center gap-2.5 text-xs">
          <div className="flex items-center gap-1.5 text-muted-foreground">
            <SlidersHorizontal className="h-3.5 w-3.5" />
            <span className="font-medium">Filters:</span>
          </div>

          {/* CGPA Floor Filter */}
          <Select
            value={String(minCgpa)}
            onValueChange={(v) => setMinCgpa(Number(v))}
          >
            <SelectTrigger className="h-8 w-32 text-xs" aria-label="Filter by CGPA">
              <SelectValue placeholder="Min CGPA" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="0">All CGPA</SelectItem>
              <SelectItem value="6">CGPA ≥ 6.0</SelectItem>
              <SelectItem value="7">CGPA ≥ 7.0</SelectItem>
              <SelectItem value="7.5">CGPA ≥ 7.5</SelectItem>
              <SelectItem value="8">CGPA ≥ 8.0</SelectItem>
              <SelectItem value="8.5">CGPA ≥ 8.5</SelectItem>
              <SelectItem value="9">CGPA ≥ 9.0</SelectItem>
            </SelectContent>
          </Select>

          {/* Match Score Filter */}
          <Select
            value={String(minMatch)}
            onValueChange={(v) => setMinMatch(Number(v))}
          >
            <SelectTrigger className="h-8 w-36 text-xs" aria-label="Filter by Match score">
              <SelectValue placeholder="Min Match %" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="0">All Matches</SelectItem>
              <SelectItem value="50">Match ≥ 50%</SelectItem>
              <SelectItem value="60">Match ≥ 60%</SelectItem>
              <SelectItem value="70">Match ≥ 70%</SelectItem>
              <SelectItem value="80">Match ≥ 80%</SelectItem>
              <SelectItem value="90">Match ≥ 90%</SelectItem>
            </SelectContent>
          </Select>

          {/* Application Status Filter */}
          <Select value={appStatus} onValueChange={setAppStatus}>
            <SelectTrigger className="h-8 w-44 text-xs" aria-label="Filter by Application status">
              <SelectValue placeholder="Application Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Statuses</SelectItem>
              <SelectItem value="has-applied">Applied (Any Stage)</SelectItem>
              <SelectItem value="not-applied">Not Applied</SelectItem>
              <SelectItem value="shortlisted">Shortlisted</SelectItem>
              <SelectItem value="interview">Interviewing</SelectItem>
              <SelectItem value="selected-offer">Selected / Offer</SelectItem>
              <SelectItem value="rejected">Not Selected</SelectItem>
            </SelectContent>
          </Select>

          {/* Reset Filters Button */}
          {hasActiveFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={resetFilters}
              className="h-8 gap-1.5 px-2.5 text-xs text-muted-foreground hover:text-foreground"
            >
              <RotateCcw className="h-3 w-3" /> Reset filters
            </Button>
          )}

          <div className="ml-auto text-xs text-muted-foreground">
            {totalItems} student{totalItems === 1 ? "" : "s"} found
          </div>
        </div>
      </div>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={campusStudents.length === 0 || !drive}
        onRetry={reload}
        emptyTitle="No eligibility data"
        emptyMessage="No drives or students are available for this campus yet."
      >
      <Card className="overflow-x-auto p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead
                className="cursor-pointer select-none hover:text-foreground"
                onClick={() => handleSort("name")}
              >
                <div className="flex items-center gap-1.5">
                  <span>Student</span>
                  {sortBy === "name" ? (
                    sortDir === "asc" ? (
                      <ArrowUp className="h-3.5 w-3.5 text-violet-bright" />
                    ) : (
                      <ArrowDown className="h-3.5 w-3.5 text-violet-bright" />
                    )
                  ) : (
                    <ArrowUpDown className="h-3 w-3 opacity-30" />
                  )}
                </div>
              </TableHead>
              <TableHead
                className="cursor-pointer select-none hover:text-foreground"
                onClick={() => handleSort("cgpa")}
              >
                <div className="flex items-center gap-1.5">
                  <span>CGPA</span>
                  {sortBy === "cgpa" ? (
                    sortDir === "asc" ? (
                      <ArrowUp className="h-3.5 w-3.5 text-violet-bright" />
                    ) : (
                      <ArrowDown className="h-3.5 w-3.5 text-violet-bright" />
                    )
                  ) : (
                    <ArrowUpDown className="h-3 w-3 opacity-30" />
                  )}
                </div>
              </TableHead>
              <TableHead>Branch</TableHead>
              <TableHead
                className="cursor-pointer select-none hover:text-foreground"
                onClick={() => handleSort("backlogs")}
              >
                <div className="flex items-center gap-1.5">
                  <span>Backlogs</span>
                  {sortBy === "backlogs" ? (
                    sortDir === "asc" ? (
                      <ArrowUp className="h-3.5 w-3.5 text-violet-bright" />
                    ) : (
                      <ArrowDown className="h-3.5 w-3.5 text-violet-bright" />
                    )
                  ) : (
                    <ArrowUpDown className="h-3 w-3 opacity-30" />
                  )}
                </div>
              </TableHead>
              <TableHead
                className="cursor-pointer select-none hover:text-foreground"
                onClick={() => handleSort("eligible")}
              >
                <div className="flex items-center gap-1.5">
                  <span>Eligibility</span>
                  {sortBy === "eligible" ? (
                    sortDir === "asc" ? (
                      <ArrowUp className="h-3.5 w-3.5 text-violet-bright" />
                    ) : (
                      <ArrowDown className="h-3.5 w-3.5 text-violet-bright" />
                    )
                  ) : (
                    <ArrowUpDown className="h-3 w-3 opacity-30" />
                  )}
                </div>
              </TableHead>
              <TableHead>Readiness</TableHead>
              <TableHead
                className="cursor-pointer select-none hover:text-foreground"
                onClick={() => handleSort("match")}
              >
                <div className="flex items-center gap-1.5">
                  <span>Match</span>
                  {sortBy === "match" ? (
                    sortDir === "asc" ? (
                      <ArrowUp className="h-3.5 w-3.5 text-violet-bright" />
                    ) : (
                      <ArrowDown className="h-3.5 w-3.5 text-violet-bright" />
                    )
                  ) : (
                    <ArrowUpDown className="h-3 w-3 opacity-30" />
                  )}
                </div>
              </TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {paginatedRows.map((r) => (
              <TableRow
                key={r.student.id}
                className={cn("cursor-pointer", !r.eligible && "bg-risk/5")}
                onClick={() => setSelectedStudentId(r.student.id)}
              >
                <TableCell>
                  <div className="flex items-center gap-2.5">
                    <Avatar className="h-7 w-7">
                      <AvatarFallback className="text-[10px]">
                        {r.student.avatarInitials}
                      </AvatarFallback>
                    </Avatar>
                    <span className="font-medium">{r.student.name}</span>
                  </div>
                </TableCell>
                <TableCell className="tabular-nums">{r.student.cgpa.toFixed(2)}</TableCell>
                <TableCell className="text-muted-foreground">{r.student.branchCode}</TableCell>
                <TableCell
                  className={cn("tabular-nums", r.student.backlogs > 0 && "text-risk")}
                >
                  {r.student.backlogs}
                </TableCell>
                <TableCell>
                  <Badge variant={r.eligible ? "success" : "risk"} className="gap-1">
                    {r.eligible ? (
                      <ShieldCheck className="h-3 w-3" />
                    ) : (
                      <ShieldX className="h-3 w-3" />
                    )}
                    {r.eligible ? "ELIGIBLE" : "NOT ELIGIBLE"}
                  </Badge>
                </TableCell>
                <TableCell>
                  {(() => {
                    const tier = employabilityTier(r.student.readiness);
                    return (
                      <Badge variant={tier.tone} className="whitespace-nowrap">
                        {tier.scored ? tier.label : `${tier.label} (unscored)`}
                      </Badge>
                    );
                  })()}
                </TableCell>
                <TableCell className="font-semibold tabular-nums text-violet-bright">
                  {r.match !== undefined ? `${r.match}%` : "—"}
                </TableCell>
                <TableCell>
                  {r.application ? (
                    <Badge variant={applicationStatusTone[r.application.status]}>
                      {applicationStatusLabel[r.application.status]}
                    </Badge>
                  ) : (
                    <span className="text-xs text-muted-foreground">Not applied</span>
                  )}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedStudentId(r.student.id);
                    }}
                  >
                    <ListChecks className="h-3.5 w-3.5" /> Explain
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {paginatedRows.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} className="py-12 text-center text-sm text-muted-foreground">
                  <div className="flex flex-col items-center justify-center gap-2">
                    <p>No students match the current filter criteria.</p>
                    {hasActiveFilters && (
                      <Button variant="outline" size="sm" onClick={resetFilters} className="mt-1">
                        <RotateCcw className="mr-1.5 h-3.5 w-3.5" /> Reset all filters
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>

        {/* Pagination Footer */}
        <div className="flex flex-col gap-3 border-t border-border px-4 py-3 sm:flex-row sm:items-center sm:justify-between text-xs text-muted-foreground">
          <div>
            Showing{" "}
            <span className="font-semibold text-foreground">
              {totalItems === 0 ? 0 : (safePage - 1) * pageSize + 1}–{Math.min(safePage * pageSize, totalItems)}
            </span>{" "}
            of <span className="font-semibold text-foreground">{totalItems}</span> students
            {totalItems !== campusStudents.length && (
              <span className="ml-1 opacity-75">
                (filtered from {campusStudents.length})
              </span>
            )}
          </div>

          <div className="flex items-center gap-4">
            {/* Rows Per Page */}
            <div className="flex items-center gap-1.5">
              <span>Rows per page:</span>
              <Select
                value={String(pageSize)}
                onValueChange={(v) => {
                  setPageSize(Number(v));
                  setPage(1);
                }}
              >
                <SelectTrigger className="h-8 w-16 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="15">15</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Prev / Next Page Buttons */}
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="sm"
                disabled={safePage <= 1}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                className="h-8 w-8 p-0"
                aria-label="Previous page"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="px-2 font-medium text-foreground">
                Page {safePage} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={safePage >= totalPages}
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                className="h-8 w-8 p-0"
                aria-label="Next page"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      </Card>
      </AsyncBoundary>

      <DepthCard className="mt-5 p-5">
        <CardTitle className="mb-2">Why eligibility and match are separate</CardTitle>
        <p className="text-sm text-muted-foreground">
          Eligibility is a hard rule check: CGPA, branch, backlogs and batch are compared against the
          drive&apos;s published criteria, and every comparison is shown alongside its verdict. The
          match score is a prototype estimate of fit. A candidate can be a 91% match and still be
          blocked by a single failing rule — the matrix above always shows both.
        </p>
      </DepthCard>

      <Sheet open={!!selected} onOpenChange={(open) => !open && setSelectedStudentId(null)}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-md">
          {selected && drive && (
            <>
              <SheetHeader>
                <SheetTitle>{selected.name}</SheetTitle>
                <SheetDescription>
                  {selected.rollNumber} · {selected.branch} · CGPA {selected.cgpa.toFixed(2)}
                </SheetDescription>
                {(() => {
                  const tier = employabilityTier(selected.readiness);
                  return (
                    <div className="mt-1 flex items-center gap-2">
                      <Badge variant={tier.tone}>
                        {tier.scored ? tier.label : `${tier.label} (unscored)`}
                      </Badge>
                      <span className="text-[11px] text-muted-foreground">
                        Readiness tier — student-level, not part of the eligibility gate
                      </span>
                    </div>
                  );
                })()}
              </SheetHeader>
              <div className="mt-5">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {drive.companyName} — {drive.role}
                </p>
                <EligibilityChecklist student={selected} drive={drive} advisory={advisory} />
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>

      <Dialog open={shortlistOpen} onOpenChange={setShortlistOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Auto-shortlist recommended candidates</DialogTitle>
            <DialogDescription>
              Shortlists eligible applicants whose match fit is at or above the floor. Eligibility is
              re-verified on the server as the hard gate — the pool is eligible <strong>applicants</strong>{" "}
              with a computed match score, so students who never applied are not affected.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <div className="mb-1 flex items-center justify-between">
                <label htmlFor="minfit" className="text-sm font-medium">
                  Minimum match fit
                </label>
                <span className="text-sm font-semibold tabular-nums text-violet-bright">
                  {minFit}%
                </span>
              </div>
              <input
                id="minfit"
                type="range"
                min={0}
                max={100}
                step={5}
                value={minFit}
                onChange={(e) => setMinFit(Number(e.target.value))}
                className="w-full accent-violet"
              />
            </div>

            <div className="rounded-lg border border-border bg-accent/30 px-3 py-2.5 text-sm">
              <p className="font-medium">
                {shortlistPool.length} candidate{shortlistPool.length === 1 ? "" : "s"} match this floor
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Eligible applicants with a match score ≥ {minFit}%. The server may shortlist fewer if
                its re-verified eligibility differs.
              </p>
              {shortlistPool.length > 0 && (
                <ul className="mt-2 max-h-40 space-y-1 overflow-y-auto">
                  {shortlistPool.map((r) => (
                    <li key={r.student.id} className="flex items-center justify-between text-xs">
                      <span>{r.student.name}</span>
                      <span className="tabular-nums text-violet-bright">{r.match}%</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button variant="ghost" onClick={() => setShortlistOpen(false)} disabled={shortlisting}>
              Cancel
            </Button>
            <Button variant="glow" onClick={runAutoShortlist} disabled={shortlisting}>
              <Sparkles className="h-4 w-4" />
              {shortlisting ? "Shortlisting…" : `Shortlist ${shortlistPool.length}`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
