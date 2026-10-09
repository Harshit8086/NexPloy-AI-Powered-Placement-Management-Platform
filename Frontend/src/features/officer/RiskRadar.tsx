"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ShieldAlert,
  UserPlus,
  MessageSquareText,
  Map as MapIcon,
  Briefcase,
  Search,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  Loader2,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";
import { PageHeader } from "@/components/layout/PageHeader";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AnimatedMetric } from "@/components/intelligence/AnimatedMetric";
import { DonutChart } from "@/components/charts/DonutChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import {
  listRiskEntriesByCampus,
  assessCampusRisk,
  predictStudentRisk,
  type RiskSignalsInput,
} from "@/lib/api/risk";
import { listStudents } from "@/lib/api/students";
import {
  listMentors,
  createInterventionPlan,
  RECOMMENDED_PLAN_TEMPLATE,
  type MentorAccount,
} from "@/lib/api/mentors";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";
import { riskOrder, riskTone } from "@/lib/status";
import type { RiskEntry, RiskLevel, Student } from "@/types";

const actionMeta: Record<string, typeof UserPlus> = {
  "Assign Mentor": UserPlus,
  "Schedule Mock Interview": MessageSquareText,
  "Update Roadmap": MapIcon,
  "Recommend Relevant Drives": Briefcase,
};

/** Optional overrides for activity signals derived from the student's saved records. */
const SIGNAL_FIELDS: { key: keyof RiskSignalsInput; label: string }[] = [
  { key: "applicationsSubmitted", label: "Applications submitted" },
  { key: "interviewsAttended", label: "Interviews attended" },
  { key: "interviewsMissed", label: "Interviews missed" },
  { key: "daysSinceLastActivity", label: "Days since last activity" },
  { key: "mockInterviewsCompleted", label: "Mock interviews completed" },
];

const EMPTY_SIGNALS: Record<keyof RiskSignalsInput, string> = {
  applicationsSubmitted: "",
  interviewsAttended: "",
  interviewsMissed: "",
  daysSinceLastActivity: "",
  mockInterviewsCompleted: "",
};

export function RiskRadar() {
  const router = useRouter();
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // Table controls (client-side, same pattern as the Officer Readiness page).
  const [query, setQuery] = useState("");
  const [levelFilter, setLevelFilter] = useState("all");
  const [pageSize, setPageSize] = useState<number>(25);
  const [page, setPage] = useState<number>(1);

  // Predict form (per selected student in the detail sheet).
  const [signalInputs, setSignalInputs] = useState(EMPTY_SIGNALS);
  const [predicting, setPredicting] = useState(false);
  const [predictError, setPredictError] = useState<string | null>(null);
  const [prediction, setPrediction] = useState<{
    band: string;
    supportProbability: number;
    recommendedInterventions: string[];
    modelVersion: string;
  } | null>(null);

  // Intervention proposal seeded from a clicked recommended action.
  const [planAction, setPlanAction] = useState<string | null>(null);
  const [planActions, setPlanActions] = useState<string[]>([]);
  const [planMentor, setPlanMentor] = useState("");
  const [planNotes, setPlanNotes] = useState("");
  const [creatingPlan, setCreatingPlan] = useState(false);

  // Batch risk assessment across all students on the campus.
  const [batchAssessing, setBatchAssessing] = useState(false);
  const [batchProgress, setBatchProgress] = useState<{ current: number; total: number } | null>(null);

  // Campus risk roll-up (latest per student): live GET /campuses/{id}/risk-entries. Guarded on a
  // non-empty campusId — the store seeds activeCampusId to "" until the signed-in user's campus
  // resolves, and firing with it empty produces a /api/campuses//risk-entries request that 404s.
  const {
    data: campusRisk,
    loading: riskLoading,
    error: riskError,
    reload: reloadRisk,
  } = useApiResource<RiskEntry[]>(
    () => (campusId ? listRiskEntriesByCampus(campusId) : Promise.resolve([])),
    [campusId],
    [],
  );
  // Student directory joined for names/readiness — so risk entries render named.
  const {
    data: allStudents,
    loading: studentsLoading,
    error: studentsError,
    reload: reloadStudents,
  } = useApiResource<Student[]>(() => listStudents(), [], []);
  const studentById = useMemo(() => {
    const map = new Map<string, Student>();
    allStudents.forEach((s) => map.set(s.id, s));
    return map;
  }, [allStudents]);
  const riskByStudent = useMemo(
    () => new Map(campusRisk.map((entry) => [entry.studentId, entry])),
    [campusRisk],
  );
  // Mentor accounts the officer can hand an intervention plan to (OFFICER-only endpoint).
  const { data: mentors } = useApiResource<MentorAccount[]>(
    () => (campusId ? listMentors(campusId) : Promise.resolve([])),
    [campusId],
    [],
  );

  const filtered = useMemo(
    () =>
      allStudents
        .filter((student) => {
          if (campusId && student.campusId !== campusId) return false;
          if (query && !student.name.toLowerCase().includes(query.toLowerCase())) return false;
          const risk = riskByStudent.get(student.id);
          return levelFilter === "all" || risk?.level === levelFilter;
        })
        .sort((a, b) => {
          const riskA = riskByStudent.get(a.id);
          const riskB = riskByStudent.get(b.id);
          const orderA = riskA ? riskOrder[riskA.level] : 3;
          const orderB = riskB ? riskOrder[riskB.level] : 3;
          return orderA - orderB || a.name.localeCompare(b.name);
        }),
    [allStudents, campusId, levelFilter, query, riskByStudent],
  );

  // Client-side pagination over the filtered rows (mirrors the Officer Readiness page).
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const paginated = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const selectedStudent = selectedId ? studentById.get(selectedId) : undefined;
  const selectedRisk = selectedId ? riskByStudent.get(selectedId) : undefined;

  const counts: Record<RiskLevel, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 };
  for (const r of campusRisk) counts[r.level]++;

  // Reset to page 1 whenever any filter changes.
  const handleFilterChange = (setter: (val: string) => void, val: string) => {
    setter(val);
    setPage(1);
  };

  const closeSheet = () => {
    setSelectedId(null);
    setSignalInputs(EMPTY_SIGNALS);
    setPredictError(null);
    setPrediction(null);
    setPlanAction(null);
    setPlanActions([]);
    setPlanMentor("");
    setPlanNotes("");
  };

  /**
   * Runs POST /students/:id/risk with the signals the officer filled in (blanks are omitted —
   * the ML service records missing signals rather than assuming high risk), then refreshes the
   * roll-up so the new append-only entry shows up in the radar.
   */
  async function handlePredict() {
    if (!selectedStudent) return;
    setPredicting(true);
    setPredictError(null);
    try {
      const signals: RiskSignalsInput = {};
      for (const f of SIGNAL_FIELDS) {
        const raw = signalInputs[f.key].trim();
        if (raw === "") continue;
        const n = Number(raw);
        if (Number.isFinite(n) && n >= 0) signals[f.key] = Math.round(n);
      }
      const result = await predictStudentRisk(selectedStudent.id, signals);
      setPrediction(result);
      await reloadRisk();
      pushToast(
        "Risk prediction saved",
        `${selectedStudent.name}: ${result.band} (support probability ${Math.round(result.supportProbability * 100)}%).`,
      );
    } catch (error) {
      setPredictError(error instanceof Error ? error.message : "Prediction failed.");
    } finally {
      setPredicting(false);
    }
  }

  /**
   * One-click Risk Assessment for all students on the campus.
   * Calls backend POST /api/campuses/:id/risk-entries/assess-all with automatic graceful
   * client-side parallel fallback.
   */
  async function handleBatchAssess() {
    if (!campusId && allStudents.length === 0) return;
    setBatchAssessing(true);
    setBatchProgress(null);
    try {
      let usedFallback = false;
      if (campusId) {
        try {
          await assessCampusRisk(campusId);
        } catch {
          usedFallback = true;
        }
      } else {
        usedFallback = true;
      }

      if (usedFallback) {
        // Fallback: evaluate students in chunks of 3
        const targetStudents = allStudents.filter(
          (s) => !campusId || s.campusId === campusId,
        );
        let completed = 0;
        setBatchProgress({ current: 0, total: targetStudents.length });
        const chunkSize = 3;
        for (let i = 0; i < targetStudents.length; i += chunkSize) {
          const chunk = targetStudents.slice(i, i + chunkSize);
          await Promise.allSettled(chunk.map((s) => predictStudentRisk(s.id)));
          completed += chunk.length;
          setBatchProgress({
            current: Math.min(completed, targetStudents.length),
            total: targetStudents.length,
          });
        }
      }

      await reloadRisk();
      pushToast(
        "Risk Assessment Complete",
        `Successfully evaluated risk status for ${allStudents.length} students.`,
      );
    } catch (error) {
      pushToast(
        "Assessment failed",
        error instanceof Error ? error.message : "Could not complete risk assessment.",
      );
    } finally {
      setBatchAssessing(false);
      setBatchProgress(null);
    }
  }

  const runAction = (action: string) => {
    if (!selectedStudent) return;

    // Assigning a mentor and proposing an intervention plan is an OFFICER action that must
    // persist server-side — it happens on the Mentor Assignment page, not by fabricating a
    // client-only plan here. Send the officer there with the student pre-noted.
    if (action === "Assign Mentor") {
      router.push("/officer/mentors");
      return;
    }

    // Every other recommendation becomes a tracked intervention plan: the clicked action seeds
    // the checklist, the officer picks a mentor, and the plan is proposed server-side (PROPOSED
    // until the mentor activates it), linked to the risk entry it was raised from.
    setPlanAction(action);
    setPlanActions([action]);
  };

  const togglePlanAction = (label: string) => {
    setPlanActions((prev) =>
      prev.includes(label) ? prev.filter((l) => l !== label) : [...prev, label],
    );
  };

  // Checklist candidates: the clicked recommendation plus the standard office template.
  const planCandidates = useMemo(() => {
    const base = planAction ? [planAction, ...RECOMMENDED_PLAN_TEMPLATE] : [...RECOMMENDED_PLAN_TEMPLATE];
    return Array.from(new Set(base));
  }, [planAction]);

  async function handleProposePlan() {
    if (!selectedRisk || !selectedStudent || !planMentor || planActions.length === 0) return;
    setCreatingPlan(true);
    try {
      await createInterventionPlan(selectedRisk.studentId, {
        mentorUsername: planMentor,
        focusAreas: [selectedRisk.primaryReason],
        actionLabels: planActions,
        riskEntryId: selectedRisk.id,
        notes: planNotes.trim() || undefined,
      });
      pushToast(
        "Intervention proposed",
        `Plan for ${selectedStudent.name} assigned to ${planMentor} — starts PROPOSED until the mentor activates it.`,
      );
      setPlanAction(null);
      setPlanActions([]);
      setPlanMentor("");
      setPlanNotes("");
    } catch (error) {
      pushToast(
        "Could not propose plan",
        error instanceof Error ? error.message : "Unexpected error.",
      );
    } finally {
      setCreatingPlan(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Students"
        title="Risk Radar"
        subtitle="Students flagged by placement risk, with recommended interventions."
        actions={
          <Button
            onClick={handleBatchAssess}
            disabled={batchAssessing || allStudents.length === 0}
            className="gap-2 bg-gradient-to-r from-violet-600 to-indigo-600 hover:from-violet-500 hover:to-indigo-500 text-white shadow-sm"
          >
            {batchAssessing ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Sparkles className="h-4 w-4" />
            )}
            {batchAssessing
              ? batchProgress
                ? `Assessing (${batchProgress.current}/${batchProgress.total})...`
                : "Assessing All..."
              : "Assess All Students"}
          </Button>
        }
      />

      <AsyncBoundary
        loading={riskLoading || studentsLoading}
        error={riskError ?? studentsError}
        empty={allStudents.length === 0}
        onRetry={() => {
          reloadRisk();
          reloadStudents();
        }}
        emptyTitle="No students found"
        emptyMessage="There are no students available on this campus to run a risk prediction for."
      >
        <div className="mb-5 grid gap-5 sm:grid-cols-[auto_1fr]">
          <DataReveal>
            <DepthCard
              className={cn(
                "group flex cursor-pointer flex-col items-center justify-center p-5 transition-all duration-200 hover:scale-[1.02]",
                levelFilter === "all"
                  ? "ring-2 ring-primary/60 border-primary/50 shadow-md"
                  : "hover:border-primary/40",
              )}
              onClick={() => handleFilterChange(setLevelFilter, "all")}
              title="Click to view all students"
            >
              <DonutChart
                data={[
                  { name: "High", value: counts.HIGH },
                  { name: "Medium", value: counts.MEDIUM },
                  { name: "Low", value: counts.LOW },
                ]}
                colors={["--risk", "--warning", "--success"]}
                size={140}
                centerValue={campusRisk.length}
                centerLabel="flagged"
              />
              <span className="mt-2 text-[11px] font-medium text-muted-foreground group-hover:text-primary transition-colors">
                {levelFilter === "all" ? "• Showing All" : "Click to show all"}
              </span>
            </DepthCard>
          </DataReveal>
          <DataReveal stagger className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {(["HIGH", "MEDIUM", "LOW"] as RiskLevel[]).map((level) => {
              const isSelected = levelFilter === level;
              return (
                <DataRevealItem key={level}>
                  <DepthCard
                    className={cn(
                      "group h-full p-4 cursor-pointer transition-all duration-200 hover:scale-[1.02]",
                      level === "HIGH" && !isSelected && "border-risk/30 hover:border-risk/60",
                      level === "MEDIUM" && !isSelected && "hover:border-warning/60",
                      level === "LOW" && !isSelected && "hover:border-success/60",
                      isSelected && "ring-2 ring-primary border-primary bg-primary/5 shadow-md",
                    )}
                    onClick={() => handleFilterChange(setLevelFilter, isSelected ? "all" : level)}
                    title={`Click to filter by ${level} risk`}
                  >
                    <div className="flex items-center justify-between">
                      <p className="text-2xl font-semibold tabular-nums">
                        <AnimatedMetric value={counts[level]} />
                      </p>
                      {isSelected ? (
                        <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary">
                          Filtered
                        </span>
                      ) : (
                        <span className="text-[10px] text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity">
                          Click to filter
                        </span>
                      )}
                    </div>
                    <div className="mt-1.5 flex items-center gap-1.5">
                      <Badge variant={riskTone[level]}>{level}</Badge>
                    </div>
                    <p className="mt-2 text-[11px] text-muted-foreground">
                      Driven by readiness, applications, interview performance &amp; skill gap signals.
                    </p>
                  </DepthCard>
                </DataRevealItem>
              );
            })}
          </DataReveal>
        </div>

        <div className="mb-4 flex flex-wrap items-center gap-3">
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
          <Select value={levelFilter} onValueChange={(val) => handleFilterChange(setLevelFilter, val)}>
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
          {levelFilter !== "all" && (
            <Button
              variant="outline"
              size="sm"
              className="h-9 text-xs"
              onClick={() => handleFilterChange(setLevelFilter, "all")}
            >
              Reset filter ({levelFilter})
            </Button>
          )}
        </div>

        <Card className="overflow-x-auto p-0">
          {/* min-width keeps the six columns readable on small screens — the Card above
           * provides the horizontal scroll instead of squeezing every cell. */}
          <Table className="min-w-[760px]">
            <TableHeader>
              <TableRow>
                <TableHead>Student</TableHead>
                <TableHead>Readiness</TableHead>
                <TableHead>Probability</TableHead>
                <TableHead>Risk</TableHead>
                <TableHead>Primary Reason</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {paginated.map((student) => {
                const risk = riskByStudent.get(student.id);
                return (
                  <TableRow
                    key={student.id}
                    className={cn(
                      "cursor-pointer",
                      risk?.level === "HIGH" && "border-l-2 border-l-risk/50",
                    )}
                    onClick={() => setSelectedId(student.id)}
                  >
                    <TableCell>
                      <div className="flex items-center gap-2.5">
                        <Avatar className="h-7 w-7">
                          <AvatarFallback className="text-[10px]">
                            {student.avatarInitials}
                          </AvatarFallback>
                        </Avatar>
                        <span className="font-medium">{student.name}</span>
                      </div>
                    </TableCell>
                    <TableCell className="tabular-nums">{student.readiness}</TableCell>
                    <TableCell className="tabular-nums">{student.placementProbability}%</TableCell>
                    <TableCell>
                      {risk ? (
                        <Badge variant={riskTone[risk.level]}>{risk.level}</Badge>
                      ) : (
                        <Badge variant="outline">Not run</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {risk?.primaryReason ?? "No prediction yet"}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedId(student.id);
                        }}
                      >
                        {!risk || risk.level === "LOW" ? "View" : "Review"}
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
              {filtered.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-sm text-muted-foreground">
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

      <Sheet open={!!selectedId} onOpenChange={(open) => !open && closeSheet()}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-md">
          {selectedStudent && (
            <>
              <SheetHeader>
                <SheetTitle className="flex items-center gap-2">
                  <ShieldAlert
                    className={cn(
                      "h-4 w-4",
                      selectedRisk?.level === "LOW" ? "text-success" : "text-risk",
                    )}
                  />{" "}
                  {selectedStudent.name}
                </SheetTitle>
                <SheetDescription>
                  {selectedRisk ? (
                    <>
                      <Badge variant={riskTone[selectedRisk.level]} className="mr-2">
                        {selectedRisk.level} RISK
                      </Badge>
                      {selectedRisk.primaryReason}
                    </>
                  ) : (
                    <span>No risk prediction yet. Run one below to assess this student.</span>
                  )}
                </SheetDescription>
              </SheetHeader>

              {selectedRisk && <div className="mt-5">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Risk Factors
                </p>
                <div className="space-y-2">
                  {selectedRisk.factors.map((f) => (
                    <div key={f.label} className="rounded-lg border border-border p-2.5">
                      <p className="text-xs font-medium text-foreground">{f.label}</p>
                      <p className="text-xs text-muted-foreground">{f.detail}</p>
                    </div>
                  ))}
                  {selectedRisk.factors.length === 0 && (
                    <p className="text-xs text-muted-foreground">
                      No driving factors were recorded with this entry — run a prediction with
                      activity signals below to capture them.
                    </p>
                  )}
                </div>
              </div>}

              {selectedRisk && selectedRisk.level !== "LOW" && (
                <div className="mt-5">
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Recommended Interventions
                  </p>
                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {selectedRisk.recommendedActions.map((action) => {
                      const Icon = actionMeta[action] ?? UserPlus;
                      return (
                        <Button
                          key={action}
                          variant="outline"
                          size="sm"
                          // Long recommendation labels must wrap inside the narrow sheet —
                          // Button ships whitespace-nowrap, which overflows the 2-col grid
                          // tracks and made neighbouring buttons overlap.
                          className="h-auto min-w-0 justify-start whitespace-normal py-2 text-left leading-snug"
                          onClick={() => runAction(action)}
                        >
                          <Icon className="h-3.5 w-3.5 shrink-0" /> {action}
                        </Button>
                      );
                    })}
                  </div>

                  {/* Intervention proposal — opened by clicking a recommendation above. The plan
                   * is created server-side (PROPOSED until the mentor activates it) and linked
                   * to this risk entry via riskEntryId. */}
                  {planAction && (
                    <div className="mt-3 rounded-lg border border-border p-3">
                      <p className="text-xs font-semibold text-foreground">
                        Propose intervention plan
                      </p>
                      <p className="mb-3 mt-1 text-[11px] text-muted-foreground">
                        “{planAction}” seeds the checklist — tick what the mentor should track.
                      </p>
                      <div className="flex flex-wrap gap-1.5">
                        {planCandidates.map((label) => (
                          <Button
                            key={label}
                            type="button"
                            variant={planActions.includes(label) ? "default" : "outline"}
                            size="sm"
                            className="h-7 text-[11px]"
                            aria-pressed={planActions.includes(label)}
                            onClick={() => togglePlanAction(label)}
                          >
                            {label}
                          </Button>
                        ))}
                      </div>
                      <div className="mt-3">
                        <Label className="text-[11px] text-muted-foreground">Assign to mentor</Label>
                        <Select value={planMentor} onValueChange={setPlanMentor}>
                          <SelectTrigger className="mt-1" aria-label="Assign plan to mentor">
                            <SelectValue placeholder="Select mentor" />
                          </SelectTrigger>
                          <SelectContent>
                            {mentors.map((m) => (
                              <SelectItem key={m.username} value={m.username}>
                                {m.username} ({m.activeAssignmentsCount} active)
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        {mentors.length === 0 && (
                          <p className="mt-1 text-[11px] text-muted-foreground">
                            No mentor accounts on this campus yet — create one on the Mentors page.
                          </p>
                        )}
                      </div>
                      <div className="mt-3">
                        <Label className="text-[11px] text-muted-foreground">Notes (optional)</Label>
                        <Input
                          className="mt-1"
                          value={planNotes}
                          onChange={(e) => setPlanNotes(e.target.value)}
                          placeholder="Context for the mentor..."
                          aria-label="Plan notes"
                        />
                      </div>
                      <div className="mt-3 flex gap-2">
                        <Button
                          size="sm"
                          className="flex-1"
                          disabled={creatingPlan || !planMentor || planActions.length === 0}
                          onClick={handleProposePlan}
                        >
                          {creatingPlan ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : null}
                          {creatingPlan ? "Proposing..." : "Propose Plan"}
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            setPlanAction(null);
                            setPlanActions([]);
                          }}
                        >
                          Cancel
                        </Button>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Run a fresh prediction for this student. Signals are optional; blanks are sent
               * as missing (the model records gaps rather than assuming high risk). */}
              <div className="mt-5">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Run Risk Prediction
                </p>
                <p className="mb-3 text-[11px] text-muted-foreground">
                  Activity is calculated from saved applications, interview outcomes and completed
                  mock interviews. Enter a value only to override a calculated signal; leave blank
                  to use the saved records. The result is appended to the student&apos;s risk history.
                </p>
                <div className="grid grid-cols-2 gap-2">
                  {SIGNAL_FIELDS.map((f) => (
                    <div key={f.key}>
                      <Label className="text-[11px] text-muted-foreground">{f.label}</Label>
                      <Input
                        type="number"
                        min={0}
                        className="mt-1"
                        value={signalInputs[f.key]}
                        onChange={(e) =>
                          setSignalInputs((prev) => ({ ...prev, [f.key]: e.target.value }))
                        }
                        aria-label={f.label}
                      />
                    </div>
                  ))}
                </div>
                <Button className="mt-3 w-full" onClick={handlePredict} disabled={predicting}>
                  {predicting ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Sparkles className="h-3.5 w-3.5" />
                  )}
                  {predicting ? "Predicting..." : "Predict Risk"}
                </Button>
                {predictError && <p className="mt-2 text-xs text-risk">{predictError}</p>}
                {prediction && (
                  <div className="mt-3 rounded-lg border border-border p-3">
                    <div className="flex items-center justify-between">
                      <Badge
                        variant={
                          ["NEEDS_SUPPORT", "HIGH"].includes(prediction.band.toUpperCase())
                            ? riskTone.HIGH
                            : ["MONITOR", "MEDIUM"].includes(prediction.band.toUpperCase())
                              ? riskTone.MEDIUM
                              : riskTone.LOW
                        }
                      >
                        {prediction.band}
                      </Badge>
                      <span className="text-xs tabular-nums text-muted-foreground">
                        Support probability {Math.round(prediction.supportProbability * 100)}%
                      </span>
                    </div>
                    {prediction.recommendedInterventions.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {prediction.recommendedInterventions.map((i) => (
                          <span
                            key={i}
                            className="rounded-full border border-border px-2 py-0.5 text-[11px] text-muted-foreground"
                          >
                            {i}
                          </span>
                        ))}
                      </div>
                    )}
                    <p className="mt-2 text-[10px] text-muted-foreground">
                      Model {prediction.modelVersion} — saved to risk history.
                    </p>
                  </div>
                )}
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>
    </div>
  );
}