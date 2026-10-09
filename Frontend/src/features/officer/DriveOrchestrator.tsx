"use client";

import { useMemo, useState, useCallback } from "react";
import { motion } from "framer-motion";
import {
  CalendarClock,
  AlertTriangle,
  CheckCircle2,
  Zap,
  DoorOpen,
  MapPin,
  ChevronLeft,
  ChevronRight,
  Plus,
  Clock,
  Sparkles,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { StagedRunner } from "@/components/intelligence/StagedRunner";
import {
  IntelligencePulse,
  IntelligencePulseBadge,
} from "@/components/intelligence/IntelligencePulse";
import {
  listScheduleEvents,
  listScheduleConflicts,
  listVenues,
  listPanels,
  optimizeSchedule,
  approveOptimizationRun,
  rejectOptimizationRun,
  groupEventsByDay,
  formatEventTime,
  formatEventTimeRange,
  scheduleOptimizationSteps,
} from "@/lib/api/schedules";
import { listDrives } from "@/lib/api/drives";
import { listStudentsByCampus } from "@/lib/api/students";
import { scheduleInterview } from "@/lib/api/interviews";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";
import type { OptimizationRun, ScheduleConflict, ScheduleEvent, Student } from "@/types";

type RunState = "idle" | "running" | "proposed" | "rejecting";
type ViewMode = "month" | "week" | "agenda";

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const MONTH_NAMES = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

const ROUND_PRESETS = [
  "Technical Interview - Round 1",
  "Technical Interview - Round 2",
  "Online Assessment / Coding Test",
  "Case Study & Group Discussion",
  "Managerial Round",
  "HR Interview",
  "Company Presentation / Orientation",
];

export function DriveOrchestrator() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  // Active view state
  const [viewMode, setViewMode] = useState<ViewMode>("month");
  const [currentDate, setCurrentDate] = useState<Date>(new Date());
  const [companyFilter, setCompanyFilter] = useState<string>("all");
  const [conflictOnlyFilter, setConflictOnlyFilter] = useState<boolean>(false);
  const [activityFilter, setActivityFilter] = useState<string>("all");

  // Selection & Scheduling state
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
  const [scheduleModalOpen, setScheduleModalOpen] = useState(false);
  const [runState, setRunState] = useState<RunState>("idle");
  const [proposal, setProposal] = useState<OptimizationRun | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [isSubmittingSchedule, setIsSubmittingSchedule] = useState(false);

  // Quick schedule form state
  const [formDriveId, setFormDriveId] = useState<string>("");
  const [formStudentId, setFormStudentId] = useState<string>("");
  const [formRound, setFormRound] = useState<string>(ROUND_PRESETS[0]);
  const [formDate, setFormDate] = useState<string>(() => new Date().toISOString().slice(0, 10));
  const [formTime, setFormTime] = useState<string>("10:00");
  const [formVenue, setFormVenue] = useState<string>("");
  const [formPanel, setFormPanel] = useState<string>("");
  const [formMode, setFormMode] = useState<"ON_CAMPUS" | "VIRTUAL">("ON_CAMPUS");

  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [events, conflicts, venues, panels, drives, students] = await Promise.all([
        listScheduleEvents(),
        listScheduleConflicts(campusId),
        listVenues(),
        listPanels(),
        listDrives(),
        listStudentsByCampus(campusId).catch(() => [] as Student[]),
      ]);
      return {
        events,
        conflicts,
        driveNames: Object.fromEntries(drives.map((d) => [d.id, d.companyName])) as Record<string, string>,
        venueNames: Object.fromEntries(venues.map((v) => [v.id, v.name])) as Record<string, string>,
        panelNames: Object.fromEntries(panels.map((p) => [p.id, p.name])) as Record<string, string>,
        venues,
        panels,
        drives,
        students,
        venueCount: venues.length,
      };
    },
    [campusId],
    {
      events: [],
      conflicts: [],
      driveNames: {},
      venueNames: {},
      panelNames: {},
      venues: [],
      panels: [],
      drives: [],
      students: [],
      venueCount: 0,
    },
  );

  const {
    events,
    conflicts,
    driveNames,
    venueNames,
    panelNames,
    venues,
    panels,
    drives,
    students,
    venueCount,
  } = data;

  const conflictsByEvent = useMemo(() => {
    const map = new Map<string, ScheduleConflict[]>();
    for (const c of conflicts) {
      const list = map.get(c.eventId) ?? [];
      list.push(c);
      map.set(c.eventId, list);
    }
    return map;
  }, [conflicts]);

  const company = useCallback(
    (e: ScheduleEvent) => driveNames[e.driveId] ?? e.driveId,
    [driveNames],
  );

  // Filtered Events
  const filteredEvents = useMemo(() => {
    return events.filter((e) => {
      if (companyFilter !== "all" && e.driveId !== companyFilter && company(e) !== companyFilter) {
        return false;
      }
      if (conflictOnlyFilter && !e.conflict && (conflictsByEvent.get(e.id)?.length ?? 0) === 0) {
        return false;
      }
      if (activityFilter !== "all") {
        const act = e.activityType?.toUpperCase() || "";
        if (activityFilter === "INTERVIEW" && !act.includes("INTERVIEW")) return false;
        if (activityFilter === "ASSESSMENT" && !act.includes("ASSESSMENT") && !act.includes("TEST")) return false;
        if (activityFilter === "GROUP" && !act.includes("GROUP") && !act.includes("CASE")) return false;
      }
      return true;
    });
  }, [events, companyFilter, conflictOnlyFilter, activityFilter, conflictsByEvent, company]);

  // Calendar Month Cells Calculation
  const monthGrid = useMemo(() => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const totalDays = lastDay.getDate();

    // Monday is index 0
    const startDayIndex = (firstDay.getDay() + 6) % 7;

    const prevMonthLastDay = new Date(year, month, 0).getDate();
    const cells: {
      date: Date;
      dateString: string;
      dayNumber: number;
      isCurrentMonth: boolean;
      isToday: boolean;
      events: ScheduleEvent[];
    }[] = [];

    const todayStr = new Date().toISOString().slice(0, 10);

    // Leading days from previous month
    for (let i = startDayIndex - 1; i >= 0; i--) {
      const d = new Date(year, month - 1, prevMonthLastDay - i);
      const dStr = d.toISOString().slice(0, 10);
      cells.push({
        date: d,
        dateString: dStr,
        dayNumber: d.getDate(),
        isCurrentMonth: false,
        isToday: dStr === todayStr,
        events: filteredEvents.filter((e) => e.startsAt?.slice(0, 10) === dStr),
      });
    }

    // Current month days
    for (let dNum = 1; dNum <= totalDays; dNum++) {
      const d = new Date(year, month, dNum);
      const dStr = d.toISOString().slice(0, 10);
      cells.push({
        date: d,
        dateString: dStr,
        dayNumber: dNum,
        isCurrentMonth: true,
        isToday: dStr === todayStr,
        events: filteredEvents.filter((e) => e.startsAt?.slice(0, 10) === dStr),
      });
    }

    // Trailing days from next month to complete row of 7
    const remaining = (7 - (cells.length % 7)) % 7;
    for (let i = 1; i <= remaining; i++) {
      const d = new Date(year, month + 1, i);
      const dStr = d.toISOString().slice(0, 10);
      cells.push({
        date: d,
        dateString: dStr,
        dayNumber: i,
        isCurrentMonth: false,
        isToday: dStr === todayStr,
        events: filteredEvents.filter((e) => e.startsAt?.slice(0, 10) === dStr),
      });
    }

    return cells;
  }, [currentDate, filteredEvents]);

  // Week Grid Calculation
  const weekDays = useMemo(() => {
    const curr = new Date(currentDate);
    const day = curr.getDay();
    const diff = (day === 0 ? -6 : 1) - day;
    curr.setDate(curr.getDate() + diff);

    const days: {
      date: Date;
      dateString: string;
      dayName: string;
      dayNumber: number;
      isToday: boolean;
      events: ScheduleEvent[];
    }[] = [];

    const todayStr = new Date().toISOString().slice(0, 10);

    for (let i = 0; i < 7; i++) {
      const d = new Date(curr);
      d.setDate(curr.getDate() + i);
      const dStr = d.toISOString().slice(0, 10);
      days.push({
        date: d,
        dateString: dStr,
        dayName: WEEKDAYS[i],
        dayNumber: d.getDate(),
        isToday: dStr === todayStr,
        events: filteredEvents.filter((e) => e.startsAt?.slice(0, 10) === dStr),
      });
    }
    return days;
  }, [currentDate, filteredEvents]);

  const columns = useMemo(() => groupEventsByDay(filteredEvents), [filteredEvents]);
  const selectedEvent = events.find((e) => e.id === selectedEventId) ?? null;
  const bookedVenues = new Set(events.map((e) => e.venueId).filter(Boolean)).size;

  // Month navigation helpers
  const handlePrevMonth = () => {
    if (viewMode === "week") {
      const d = new Date(currentDate);
      d.setDate(d.getDate() - 7);
      setCurrentDate(d);
    } else {
      const d = new Date(currentDate);
      d.setMonth(d.getMonth() - 1);
      setCurrentDate(d);
    }
  };

  const handleNextMonth = () => {
    if (viewMode === "week") {
      const d = new Date(currentDate);
      d.setDate(d.getDate() + 7);
      setCurrentDate(d);
    } else {
      const d = new Date(currentDate);
      d.setMonth(d.getMonth() + 1);
      setCurrentDate(d);
    }
  };

  const handleToday = () => {
    setCurrentDate(new Date());
  };

  const openScheduleDialogOnDate = (dateStr?: string) => {
    if (dateStr) {
      setFormDate(dateStr);
    }
    if (drives.length > 0 && !formDriveId) {
      setFormDriveId(drives[0].id);
    }
    if (venues.length > 0 && !formVenue) {
      setFormVenue(venues[0].name);
    }
    if (panels.length > 0 && !formPanel) {
      setFormPanel(panels[0].name);
    }
    setScheduleModalOpen(true);
  };

  const handleScheduleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formDriveId || !formDate || !formTime) {
      pushToast("Missing information", "Please select a drive, date, and time slot.");
      return;
    }

    const drive = drives.find((d) => d.id === formDriveId);
    const chosenStudentId = formStudentId || students[0]?.id || "stu_batch_all";
    const scheduledAt = `${formDate}T${formTime}:00+05:30`;

    setIsSubmittingSchedule(true);
    try {
      await scheduleInterview({
        driveId: formDriveId,
        studentId: chosenStudentId,
        round: formRound,
        mode: formMode,
        scheduledAt,
        venue: formVenue || null,
        panel: formPanel || null,
        companyName: drive?.companyName ?? null,
      });

      pushToast("Round Scheduled", `${drive?.companyName ?? "Drive"} round booked for ${formDate} at ${formTime}.`);
      setScheduleModalOpen(false);
      reload();
    } catch (err) {
      pushToast(
        "Scheduling Failed",
        err instanceof Error ? err.message : "Could not book the round. Check for conflicts.",
      );
    } finally {
      setIsSubmittingSchedule(false);
    }
  };

  const runOptimizer = async () => {
    setRunState("running");
    setProposal(null);
    try {
      const run = await optimizeSchedule(campusId);
      setProposal(run);
      setRunState("proposed");
    } catch (err) {
      setRunState("idle");
      pushToast(
        "Optimization failed",
        err instanceof Error ? err.message : "The optimizer could not return a schedule.",
      );
    }
  };

  const approve = async () => {
    if (!proposal) return;
    setBusy(true);
    try {
      await approveOptimizationRun(proposal.runId);
      pushToast("Schedule updated", "The optimized assignments were applied.");
      setProposal(null);
      setRunState("idle");
      reload();
    } catch (err) {
      pushToast("Approve failed", err instanceof Error ? err.message : "Could not apply the schedule.");
    } finally {
      setBusy(false);
    }
  };

  const reject = async () => {
    if (!proposal || !rejectReason.trim()) return;
    setBusy(true);
    try {
      await rejectOptimizationRun(proposal.runId, rejectReason.trim());
      pushToast("Proposal rejected", "The active schedule was left unchanged.");
      setProposal(null);
      setRejectReason("");
      setRunState("idle");
    } catch (err) {
      pushToast("Reject failed", err instanceof Error ? err.message : "Could not reject the proposal.");
    } finally {
      setBusy(false);
    }
  };

  const getEventBadgeClass = (e: ScheduleEvent) => {
    if (e.conflict) {
      return "border-risk/60 bg-risk/20 text-risk font-semibold ring-1 ring-risk/40";
    }
    const act = e.activityType?.toUpperCase() || "";
    if (act.includes("ASSESSMENT") || act.includes("TEST") || act.includes("CODING")) {
      return "border-cyan-500/30 bg-cyan-500/15 text-cyan-300 hover:bg-cyan-500/25";
    }
    if (act.includes("GROUP") || act.includes("CASE")) {
      return "border-amber-500/30 bg-amber-500/15 text-amber-300 hover:bg-amber-500/25";
    }
    return "border-violet-500/30 bg-violet-500/15 text-violet-300 hover:bg-violet-500/25";
  };

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <PageHeader
        eyebrow="Placement Command Center"
        title="Drive Orchestrator & Calendar"
        subtitle="Full scheduling intelligence, conflict detection, and interactive placement drive management."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={runOptimizer}
              disabled={runState === "running" || conflicts.length === 0}
              className={cn(conflicts.length > 0 && "border-risk/50 text-risk hover:bg-risk/10")}
            >
              <Zap className="mr-1.5 h-4 w-4" />
              {conflicts.length > 0 ? `Optimize (${conflicts.length} Conflicts)` : "Check & Optimize"}
            </Button>
            <Button
              variant="glow"
              size="sm"
              onClick={() => openScheduleDialogOnDate()}
            >
              <Plus className="mr-1.5 h-4 w-4" /> Schedule Round
            </Button>
          </div>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={events.length === 0}
        onRetry={reload}
        emptyTitle="No scheduled events"
        emptyMessage="No drives are scheduled yet. Click '+ Schedule Round' to create the first placement activity."
      >
        {/* Metric Stat Cards */}
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Total Scheduled Events" value={events.length} icon={CalendarClock} accent="violet" />
          <StatCard
            label="Open Conflicts"
            value={conflicts.length}
            icon={AlertTriangle}
            accent={conflicts.length > 0 ? "risk" : "success"}
          />
          <StatCard label="Venues In Use" value={bookedVenues} icon={DoorOpen} />
          <StatCard label="Venues Available" value={Math.max(0, venueCount - bookedVenues)} accent="success" />
        </div>

        {/* Conflict / Optimizer Panel */}
        {conflicts.length > 0 && (
          <Card className="border-risk/30 p-5 shadow-lg">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2 text-sm font-semibold text-risk">
                <AlertTriangle className="h-4 w-4" />
                {conflicts.length} scheduling conflict{conflicts.length === 1 ? "" : "s"} detected
              </div>
              <Button variant="glow" size="sm" onClick={runOptimizer} disabled={runState === "running"}>
                <Zap className="h-4 w-4" /> Optimize Schedule with OR-Tools
              </Button>
            </div>

            <IntelligencePulse active={runState === "running"}>
              {runState === "running" ? (
                <div className="flex flex-col items-center gap-5 py-6">
                  <IntelligencePulseBadge label="Running OR-Tools Constraint Optimizer" />
                  <div className="w-full max-w-xs">
                    <StagedRunner steps={scheduleOptimizationSteps} onDone={() => {}} />
                  </div>
                </div>
              ) : runState === "proposed" && proposal ? (
                <ProposalPanel
                  proposal={proposal}
                  venueNames={venueNames}
                  panelNames={panelNames}
                  busy={busy}
                  onApprove={approve}
                  onStartReject={() => setRunState("rejecting")}
                />
              ) : runState === "rejecting" && proposal ? (
                <div className="rounded-lg border border-border p-4">
                  <p className="mb-2 text-sm font-medium">Reason for rejecting</p>
                  <textarea
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    maxLength={500}
                    rows={3}
                    placeholder="Recorded for audit; the active schedule is left unchanged."
                    className="w-full rounded-lg border border-border bg-background p-2.5 text-sm outline-none focus:border-violet/50"
                  />
                  <div className="mt-3 flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => setRunState("proposed")} disabled={busy}>
                      Back
                    </Button>
                    <Button variant="destructive" size="sm" onClick={reject} disabled={busy || !rejectReason.trim()}>
                      Confirm Reject
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="grid gap-2 sm:grid-cols-2">
                  {conflicts.map((c, i) => (
                    <div
                      key={`${c.eventId}-${c.conflictingEventId ?? c.resourceId ?? i}`}
                      className="rounded-lg border border-risk/30 bg-risk/5 p-3 text-left"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-sm font-medium">
                          {company(events.find((e) => e.id === c.eventId) ?? ({} as ScheduleEvent)) || c.eventId}
                        </p>
                        <Badge variant="risk">{c.type.replace(/_/g, " ")}</Badge>
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">{c.message}</p>
                      {c.overlapStart && c.overlapEnd && (
                        <p className="mt-0.5 text-[11px] text-muted-foreground/70">
                          Overlap {formatEventTimeRange(c.overlapStart, c.overlapEnd)}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </IntelligencePulse>
          </Card>
        )}

        {/* Main Big Calendar Container */}
        <Card className="overflow-hidden border border-border/80 shadow-2xl">
          {/* Calendar Toolbar */}
          <div className="border-b border-border/60 bg-card/60 p-4">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              {/* Navigation & Month Title */}
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex items-center gap-1.5">
                  <Button variant="outline" size="icon" className="h-8 w-8" onClick={handlePrevMonth}>
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="sm" className="h-8 px-3 font-medium" onClick={handleToday}>
                    Today
                  </Button>
                  <Button variant="outline" size="icon" className="h-8 w-8" onClick={handleNextMonth}>
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>

                <h2 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
                  {MONTH_NAMES[currentDate.getMonth()]} {currentDate.getFullYear()}
                </h2>

                <Badge variant="secondary" className="hidden sm:inline-flex">
                  {filteredEvents.length} Event{filteredEvents.length === 1 ? "" : "s"}
                </Badge>
              </div>

              {/* View Switcher & Filter Controls */}
              <div className="flex flex-wrap items-center gap-2.5">
                {/* View Switcher */}
                <div className="flex items-center rounded-lg border border-border bg-muted/30 p-0.5">
                  <button
                    onClick={() => setViewMode("month")}
                    className={cn(
                      "rounded-md px-3 py-1.5 text-xs font-semibold transition-all",
                      viewMode === "month"
                        ? "bg-violet-600 text-white shadow-sm"
                        : "text-muted-foreground hover:text-foreground",
                    )}
                  >
                    Month
                  </button>
                  <button
                    onClick={() => setViewMode("week")}
                    className={cn(
                      "rounded-md px-3 py-1.5 text-xs font-semibold transition-all",
                      viewMode === "week"
                        ? "bg-violet-600 text-white shadow-sm"
                        : "text-muted-foreground hover:text-foreground",
                    )}
                  >
                    Week
                  </button>
                  <button
                    onClick={() => setViewMode("agenda")}
                    className={cn(
                      "rounded-md px-3 py-1.5 text-xs font-semibold transition-all",
                      viewMode === "agenda"
                        ? "bg-violet-600 text-white shadow-sm"
                        : "text-muted-foreground hover:text-foreground",
                    )}
                  >
                    Agenda
                  </button>
                </div>

                {/* Company Filter Dropdown */}
                <Select value={companyFilter} onValueChange={setCompanyFilter}>
                  <SelectTrigger className="h-8 w-[140px] text-xs">
                    <SelectValue placeholder="All Companies" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Companies</SelectItem>
                    {drives.map((d) => (
                      <SelectItem key={d.id} value={d.id}>
                        {d.companyName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                {/* Activity Filter Dropdown */}
                <Select value={activityFilter} onValueChange={setActivityFilter}>
                  <SelectTrigger className="h-8 w-[130px] text-xs">
                    <SelectValue placeholder="All Rounds" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Rounds</SelectItem>
                    <SelectItem value="INTERVIEW">Interviews</SelectItem>
                    <SelectItem value="ASSESSMENT">Assessments</SelectItem>
                    <SelectItem value="GROUP">Group / Case</SelectItem>
                  </SelectContent>
                </Select>

                {/* Conflict Filter Toggle */}
                <Button
                  variant={conflictOnlyFilter ? "destructive" : "outline"}
                  size="sm"
                  className="h-8 text-xs"
                  onClick={() => setConflictOnlyFilter(!conflictOnlyFilter)}
                >
                  <AlertTriangle className="mr-1 h-3 w-3" />
                  {conflictOnlyFilter ? "Conflicts Only" : "Show All"}
                </Button>
              </div>
            </div>

            {/* Quick legend */}
            <div className="mt-3 flex flex-wrap items-center gap-4 text-[11px] text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-violet-400" /> Technical / HR Interview
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-cyan-400" /> Online Assessment / Coding
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-amber-400" /> Case Study / GD
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-risk animate-pulse" /> Conflict Clash
              </span>
            </div>
          </div>

          {/* VIEW: Month View */}
          {viewMode === "month" && (
            <div className="overflow-x-auto">
              <div className="min-w-[800px]">
                {/* Weekday Header */}
                <div className="grid grid-cols-7 border-b border-border bg-muted/20 text-center text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {WEEKDAYS.map((day) => (
                    <div key={day} className="py-2.5">
                      {day}
                    </div>
                  ))}
                </div>

                {/* Days Grid */}
                <div className="grid grid-cols-7 border-collapse">
                  {monthGrid.map((cell, idx) => (
                    <div
                      key={cell.dateString + idx}
                      className={cn(
                        "group relative flex min-h-[130px] flex-col justify-between border-b border-r border-border/60 p-2 transition-colors",
                        !cell.isCurrentMonth && "bg-muted/10 opacity-40",
                        cell.isToday && "bg-violet-500/5 ring-1 ring-inset ring-violet-500/40",
                        "hover:bg-muted/15",
                      )}
                    >
                      {/* Top Header of Day Cell */}
                      <div className="flex items-center justify-between">
                        <span
                          className={cn(
                            "flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold",
                            cell.isToday
                              ? "bg-violet-600 font-bold text-white shadow-sm"
                              : "text-foreground/80",
                          )}
                        >
                          {cell.dayNumber}
                        </span>

                        <button
                          onClick={() => openScheduleDialogOnDate(cell.dateString)}
                          className="opacity-0 transition-opacity hover:text-violet-400 group-hover:opacity-100"
                          title="Schedule round on this day"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      </div>

                      {/* Event Chips List */}
                      <div className="mt-1 flex-1 space-y-1.5 overflow-hidden">
                        {cell.events.slice(0, 3).map((e) => {
                          const conflictCount = (conflictsByEvent.get(e.id) ?? []).length;
                          return (
                            <motion.button
                              key={e.id}
                              whileHover={{ scale: 1.02 }}
                              whileTap={{ scale: 0.98 }}
                              onClick={() => setSelectedEventId(e.id)}
                              className={cn(
                                "flex w-full flex-col rounded-md border p-1.5 text-left text-[11px] transition-all",
                                getEventBadgeClass(e),
                              )}
                            >
                              <div className="flex items-center justify-between gap-1">
                                <span className="truncate font-bold tracking-tight">
                                  {company(e)}
                                </span>
                                {(e.conflict || conflictCount > 0) && (
                                  <AlertTriangle className="h-3 w-3 shrink-0 text-risk" />
                                )}
                              </div>
                              <div className="flex items-center justify-between text-[10px] opacity-90">
                                <span>{formatEventTime(e.startsAt)}</span>
                                <span className="truncate">{e.venueName ?? "Venue TBD"}</span>
                              </div>
                            </motion.button>
                          );
                        })}

                        {cell.events.length > 3 && (
                          <button
                            onClick={() => {
                              setSelectedEventId(cell.events[3].id);
                            }}
                            className="w-full text-center text-[10px] font-semibold text-violet-400 hover:underline"
                          >
                            +{cell.events.length - 3} more rounds
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* VIEW: Week View */}
          {viewMode === "week" && (
            <div className="overflow-x-auto p-4">
              <div className="grid min-w-[900px] grid-cols-7 gap-3">
                {weekDays.map((day) => (
                  <div key={day.dateString} className="flex flex-col space-y-2">
                    <div
                      className={cn(
                        "rounded-lg border p-2 text-center",
                        day.isToday ? "border-violet-500/50 bg-violet-500/10" : "border-border bg-card",
                      )}
                    >
                      <p className="text-xs font-semibold uppercase text-muted-foreground">{day.dayName}</p>
                      <p className="text-base font-bold text-foreground">{day.dayNumber}</p>
                      <span className="text-[10px] text-muted-foreground">
                        {day.events.length} round{day.events.length === 1 ? "" : "s"}
                      </span>
                    </div>

                    <div className="min-h-[350px] space-y-2 rounded-lg border border-dashed border-border/60 bg-muted/5 p-2">
                      {day.events.map((e) => (
                        <div
                          key={e.id}
                          onClick={() => setSelectedEventId(e.id)}
                          className={cn(
                            "cursor-pointer rounded-lg border p-3 transition-all hover:shadow-md",
                            getEventBadgeClass(e),
                          )}
                        >
                          <div className="flex items-center justify-between gap-1">
                            <span className="font-bold">{company(e)}</span>
                            {e.conflict && (
                              <Badge variant="risk" className="h-4 text-[9px] px-1">
                                Clash
                              </Badge>
                            )}
                          </div>
                          <p className="mt-1 flex items-center gap-1 text-[11px] font-medium">
                            <Clock className="h-3 w-3" />
                            {formatEventTimeRange(e.startsAt, e.endsAt)}
                          </p>
                          <p className="mt-1 flex items-center gap-1 text-[11px] text-muted-foreground">
                            <MapPin className="h-3 w-3" />
                            {e.venueName ?? "Venue TBD"}
                          </p>
                          {e.panelName && (
                            <p className="mt-0.5 text-[10px] text-muted-foreground">
                              Panel: {e.panelName}
                            </p>
                          )}
                        </div>
                      ))}

                      {day.events.length === 0 && (
                        <div className="flex h-32 flex-col items-center justify-center text-center text-xs text-muted-foreground/60">
                          <p>No activity</p>
                          <button
                            onClick={() => openScheduleDialogOnDate(day.dateString)}
                            className="mt-1 text-[10px] text-violet-400 hover:underline"
                          >
                            + Add round
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* VIEW: Agenda Columns View */}
          {viewMode === "agenda" && (
            <div className="overflow-x-auto p-5">
              <div
                className="grid min-w-[720px] gap-3"
                style={{ gridTemplateColumns: `repeat(${Math.max(1, columns.length)}, minmax(0, 1fr))` }}
              >
                {columns.map((col) => (
                  <div key={col.key}>
                    <p className="mb-0.5 text-center text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                      {col.day}
                    </p>
                    <p className="mb-2 text-center text-[10px] text-muted-foreground/70">{col.date}</p>
                    <div className="min-h-[160px] space-y-2 rounded-lg border border-dashed border-border/70 p-2">
                      {col.events.map((e) => (
                        <motion.button
                          key={e.id}
                          layout
                          onClick={() => setSelectedEventId(e.id)}
                          className={cn(
                            "relative w-full rounded-lg border p-2.5 text-left transition-colors",
                            e.conflict ? "border-risk/50 bg-risk/10" : "border-border bg-card",
                          )}
                        >
                          <p className="truncate text-xs font-semibold">{company(e)}</p>
                          <p className="mt-1 text-[10px] font-medium text-violet-bright">
                            {formatEventTimeRange(e.startsAt, e.endsAt)}
                          </p>
                          <p className="flex items-center gap-1 text-[10px] text-muted-foreground">
                            <MapPin className="h-2.5 w-2.5" /> {e.venueName ?? "Venue TBD"}
                          </p>
                          {e.conflict && (
                            <Badge variant="risk" className="mt-1.5">
                              <AlertTriangle className="mr-1 h-2.5 w-2.5" /> Conflict
                            </Badge>
                          )}
                        </motion.button>
                      ))}
                      {col.events.length === 0 && (
                        <p className="py-6 text-center text-[10px] text-muted-foreground/60">No events</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </Card>
      </AsyncBoundary>

      {/* MODAL 1: Event Details & Conflict Inspector */}
      <Dialog open={!!selectedEvent} onOpenChange={(open) => !open && setSelectedEventId(null)}>
        <DialogContent className="max-w-lg">
          {selectedEvent && (
            <>
              <DialogHeader>
                <div className="flex items-center justify-between gap-2">
                  <DialogTitle className="flex items-center gap-2 text-lg">
                    <CalendarClock className="h-5 w-5 text-violet-bright" />
                    {company(selectedEvent)}
                  </DialogTitle>
                  <Badge variant={selectedEvent.conflict ? "risk" : "success"}>
                    {selectedEvent.conflict ? "Conflict Detected" : "Confirmed Slot"}
                  </Badge>
                </div>
                <DialogDescription>
                  Placement Round Information & Conflict Analysis
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-4 pt-2">
                <div className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-muted/10 p-3.5 text-xs">
                  <div>
                    <span className="text-muted-foreground">Date & Time</span>
                    <p className="font-semibold text-foreground">
                      {formatEventTimeRange(selectedEvent.startsAt, selectedEvent.endsAt)}
                    </p>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Venue</span>
                    <p className="font-semibold text-foreground">
                      {selectedEvent.venueName ?? "Venue TBD"}
                    </p>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Panel / Interviewers</span>
                    <p className="font-semibold text-foreground">
                      {selectedEvent.panelName ?? "Panel Not Assigned"}
                    </p>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Expected Participants</span>
                    <p className="font-semibold text-foreground">
                      {selectedEvent.expectedParticipants ?? 1} candidate(s)
                      {selectedEvent.venueCapacity ? ` (Capacity: ${selectedEvent.venueCapacity})` : ""}
                    </p>
                  </div>
                </div>

                {/* Conflict Details */}
                {(conflictsByEvent.get(selectedEvent.id) ?? []).length > 0 ? (
                  <div className="space-y-2 rounded-lg border border-risk/30 bg-risk/5 p-4">
                    <div className="flex items-center gap-2 text-sm font-semibold text-risk">
                      <AlertTriangle className="h-4 w-4" /> Conflicts Detected For This Slot
                    </div>
                    {(conflictsByEvent.get(selectedEvent.id) ?? []).map((c, i) => (
                      <div key={i} className="rounded-lg bg-card p-2.5 text-xs">
                        <div className="flex items-center justify-between gap-2">
                          <Badge variant="risk">{c.type.replace(/_/g, " ")}</Badge>
                          {c.overlapStart && c.overlapEnd && (
                            <span className="text-[11px] text-muted-foreground">
                              {formatEventTimeRange(c.overlapStart, c.overlapEnd)}
                            </span>
                          )}
                        </div>
                        <p className="mt-1.5 text-xs text-foreground/90">{c.message}</p>
                      </div>
                    ))}
                    <Button
                      variant="glow"
                      size="sm"
                      className="mt-2 w-full"
                      onClick={() => {
                        setSelectedEventId(null);
                        runOptimizer();
                      }}
                    >
                      <Zap className="mr-1.5 h-4 w-4" /> Auto-Resolve with OR-Tools
                    </Button>
                  </div>
                ) : (
                  <div className="rounded-lg border border-border p-3.5 text-xs text-success">
                    <p className="flex items-center gap-2 font-medium">
                      <CheckCircle2 className="h-4 w-4" /> No scheduling conflicts detected for this drive.
                    </p>
                  </div>
                )}
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* MODAL 2: Add / Schedule New Round */}
      <Dialog open={scheduleModalOpen} onOpenChange={setScheduleModalOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-violet-400" />
              Schedule Placement Drive Round
            </DialogTitle>
            <DialogDescription>
              Book an assessment, interview, or case-study slot directly into the orchestrator calendar.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleScheduleSubmit} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label className="text-xs">Company / Placement Drive</Label>
              <Select value={formDriveId} onValueChange={setFormDriveId}>
                <SelectTrigger>
                  <SelectValue placeholder="Select Drive" />
                </SelectTrigger>
                <SelectContent>
                  {drives.map((d) => (
                    <SelectItem key={d.id} value={d.id}>
                      {d.companyName} — {d.role}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs">Round Name / Preset</Label>
              <Select value={formRound} onValueChange={setFormRound}>
                <SelectTrigger>
                  <SelectValue placeholder="Select Round Type" />
                </SelectTrigger>
                <SelectContent>
                  {ROUND_PRESETS.map((p) => (
                    <SelectItem key={p} value={p}>
                      {p}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Date</Label>
                <Input
                  type="date"
                  value={formDate}
                  onChange={(e) => setFormDate(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs">Start Time</Label>
                <Input
                  type="time"
                  value={formTime}
                  onChange={(e) => setFormTime(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Venue</Label>
                <Select value={formVenue} onValueChange={setFormVenue}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select Venue" />
                  </SelectTrigger>
                  <SelectContent>
                    {venues.map((v) => (
                      <SelectItem key={v.id} value={v.name}>
                        {v.name} {v.capacity ? `(Cap: ${v.capacity})` : ""}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs">Panel / Interviewer</Label>
                <Select value={formPanel} onValueChange={setFormPanel}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select Panel" />
                  </SelectTrigger>
                  <SelectContent>
                    {panels.map((p) => (
                      <SelectItem key={p.id} value={p.name}>
                        {p.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Candidate (Optional or Batch)</Label>
                <Select value={formStudentId} onValueChange={setFormStudentId}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select student (optional)" />
                  </SelectTrigger>
                  <SelectContent>
                    {students.slice(0, 20).map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.name} ({s.rollNumber ?? s.branch})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs">Interview Mode</Label>
                <Select
                  value={formMode}
                  onValueChange={(val: "ON_CAMPUS" | "VIRTUAL") => setFormMode(val)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Mode" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ON_CAMPUS">On Campus</SelectItem>
                    <SelectItem value="VIRTUAL">Virtual</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <DialogFooter className="pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setScheduleModalOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="glow"
                size="sm"
                disabled={isSubmittingSchedule || !formDriveId}
              >
                {isSubmittingSchedule ? "Scheduling..." : "Confirm & Schedule"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ProposalPanel({
  proposal,
  venueNames,
  panelNames,
  busy,
  onApprove,
  onStartReject,
}: {
  proposal: OptimizationRun;
  venueNames: Record<string, string>;
  panelNames: Record<string, string>;
  busy: boolean;
  onApprove: () => void;
  onStartReject: () => void;
}) {
  const moves = proposal.selectedAssignments.length > 0 ? proposal.selectedAssignments : proposal.alternatives;
  return (
    <motion.div initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} className="rounded-lg border border-violet/30 bg-violet/5 p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-violet-bright">
          Proposed schedule · {proposal.status}
        </p>
        <span className="text-xs text-muted-foreground">
          {proposal.hardViolations} hard violations · soft score {proposal.softScore}
        </span>
      </div>

      {moves.length === 0 ? (
        <p className="text-xs text-muted-foreground">
          The optimizer returned no reassignments — the current schedule is already optimal.
        </p>
      ) : (
        <div className="space-y-2">
          {moves.map((a) => (
            <div key={a.eventId} className="rounded-lg bg-elevated p-2.5">
              <div className="flex items-center justify-between gap-2">
                <p className="text-xs font-medium">{a.eventId}</p>
                <span className="text-[11px] text-violet-bright">
                  {formatEventTimeRange(a.startsAt, a.endsAt)}
                </span>
              </div>
              <p className="mt-0.5 text-[11px] text-muted-foreground">
                {venueNames[a.venueId] ?? a.venueId} · {panelNames[a.panelId] ?? a.panelId}
              </p>
              {a.reasonCodes.length > 0 && (
                <p className="mt-0.5 text-[10px] text-muted-foreground/70">{a.reasonCodes.join(", ")}</p>
              )}
            </div>
          ))}
        </div>
      )}

      <div className="mt-4 flex gap-2">
        <Button variant="glow" size="sm" className="flex-1" onClick={onApprove} disabled={busy}>
          <CheckCircle2 className="h-4 w-4" /> Approve & Apply
        </Button>
        <Button variant="outline" size="sm" className="flex-1" onClick={onStartReject} disabled={busy}>
          Reject
        </Button>
      </div>
    </motion.div>
  );
}

