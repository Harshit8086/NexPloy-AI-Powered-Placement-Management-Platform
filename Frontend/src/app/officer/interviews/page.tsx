"use client";

import { useState, useMemo } from "react";
import {
  CalendarClock,
  ClipboardCheck,
  DoorOpen,
  UserCheck,
  Video,
  MapPin,
  AlertTriangle,
  Plus,
  Search,
  RotateCcw,
  MessageSquareText,
  Calendar,
  X,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { listScheduleEvents, listScheduleConflicts, listVenues, listPanels } from "@/lib/api/schedules";
import {
  listInterviews,
  scheduleInterview,
  rescheduleInterview,
  cancelInterview,
  recordInterviewResult,
} from "@/lib/api/interviews";
import { listStudentsByCampus } from "@/lib/api/students";
import { listDrivesByCampus } from "@/lib/api/drives";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { interviewStatusTone } from "@/lib/status";
import type { InterviewSlot, Student } from "@/types";
import Link from "next/link";

const resultTone = { CLEARED: "success", "NOT CLEARED": "risk", AWAITED: "muted" } as const;

/** Backend InterviewResult values offered in the Record Result dialog. */
const RESULT_OPTIONS = [
  { value: "SELECTED", label: "Selected — cleared this round" },
  { value: "REJECTED", label: "Not selected — ends this candidacy" },
  { value: "ON_HOLD", label: "On hold — no decision yet" },
] as const;

const MONTHS: Record<string, string> = {
  Jan: "01", Feb: "02", Mar: "03", Apr: "04", May: "05", Jun: "06",
  Jul: "07", Aug: "08", Sep: "09", Oct: "10", Nov: "11", Dec: "12",
};

/** "25 Sep 2026" (mapInterview display form) → "2026-09-25" for <input type="date">; "" if unparseable. */
function toDateInputValue(display: string): string {
  const m = display.match(/^(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})$/);
  if (!m) return "";
  const mm = MONTHS[m[2]];
  if (!mm) return "";
  return `${m[3]}-${mm}-${m[1].padStart(2, "0")}`;
}

/** Formats "YYYY-MM-DD" to "DD MMM YYYY" for matching with slot.date */
function toDisplayDateFormat(isoDate: string): string {
  if (!isoDate) return "";
  const parts = isoDate.split("-");
  if (parts.length !== 3) return "";
  const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const mIdx = parseInt(parts[1], 10) - 1;
  const monthStr = monthNames[mIdx] ?? "";
  return `${parts[2].padStart(2, "0")} ${monthStr} ${parts[0]}`;
}

interface InterviewWithCandidate extends InterviewSlot {
  student?: Student;
}

export default function OfficerInterviewsPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  // Filter States
  const [searchQuery, setSearchQuery] = useState("");
  const [company, setCompany] = useState("all");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "SCHEDULED" | "COMPLETED" | "CANCELLED">("ALL");
  const [resultFilter, setResultFilter] = useState<"ALL" | "CLEARED" | "NOT CLEARED" | "AWAITED">("ALL");
  const [dateFilter, setDateFilter] = useState<"ALL" | "TODAY" | "UPCOMING" | "PAST">("ALL");

  // Live data fetch
  const { data: resources, loading, error, reload } = useApiResource(
    async () => {
      const [events, conflicts, venues, panels, interviews, students, drives] = await Promise.all([
        listScheduleEvents(),
        listScheduleConflicts(campusId),
        listVenues(),
        listPanels(),
        listInterviews(campusId),
        listStudentsByCampus(campusId),
        listDrivesByCampus(campusId),
      ]);
      const byId = new Map(students.map((s) => [s.id, s]));
      // Preserving all interviews even if candidate is not in the campus roster slice
      const interviewRows: InterviewWithCandidate[] = interviews.map((i) => ({
        ...i,
        student: byId.get(i.studentId) ?? ({
          id: i.studentId,
          name: `Candidate (${i.studentId.slice(0, 8)})`,
          rollNumber: "—",
          avatarInitials: "CD",
          department: "",
        } as unknown as Student),
      }));
      return { events, conflicts, venues, panels, interviews: interviewRows, students, drives };
    },
    [campusId],
    { events: [], conflicts: [], venues: [], panels: [], interviews: [] as InterviewWithCandidate[], students: [], drives: [] },
  );

  const { events, conflicts, venues, panels, interviews, students, drives } = resources;
  const conflictCount = conflicts.length;
  const studentById = new Map(students.map((s) => [s.id, s]));

  const activeVenues = venues.filter((v) => v.active);
  const activePanels = panels.filter((p) => p.active);

  const companies = Array.from(new Set(interviews.map((i) => i.companyName).filter(Boolean)));

  // Date helpers for timeline filtering
  const todayStr = useMemo(() => {
    const now = new Date();
    return `${String(now.getDate()).padStart(2, "0")} ${Object.keys(MONTHS)[now.getMonth()]} ${now.getFullYear()}`;
  }, []);

  // Filtered rows
  const filteredRows = useMemo(() => {
    return interviews.filter((i) => {
      // 1. Company filter
      if (company !== "all" && i.companyName !== company) return false;

      // 2. Status filter
      if (statusFilter !== "ALL" && i.status !== statusFilter) return false;

      // 3. Result filter
      if (resultFilter !== "ALL" && i.result !== resultFilter) return false;

      // 4. Date filter
      if (dateFilter !== "ALL") {
        const inputVal = toDateInputValue(i.date);
        const slotTime = inputVal ? new Date(`${inputVal}T${i.time || "00:00"}:00`).getTime() : 0;
        const nowTime = new Date().setHours(0, 0, 0, 0);

        if (dateFilter === "TODAY" && i.date !== todayStr) return false;
        if (dateFilter === "UPCOMING" && slotTime < nowTime) return false;
        if (dateFilter === "PAST" && slotTime >= nowTime) return false;
      }

      // 5. Search query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const candidateName = i.student?.name?.toLowerCase() ?? "";
        const rollNumber = i.student?.rollNumber?.toLowerCase() ?? "";
        const companyName = i.companyName?.toLowerCase() ?? "";
        const round = i.round?.toLowerCase() ?? "";
        const venue = i.venue?.toLowerCase() ?? "";
        const panel = i.panel?.toLowerCase() ?? "";

        const match =
          candidateName.includes(q) ||
          rollNumber.includes(q) ||
          companyName.includes(q) ||
          round.includes(q) ||
          venue.includes(q) ||
          panel.includes(q);

        if (!match) return false;
      }

      return true;
    });
  }, [interviews, company, statusFilter, resultFilter, dateFilter, searchQuery, todayStr]);

  // Overall counts
  const totalScheduled = interviews.filter((i) => i.status === "SCHEDULED").length;
  const totalCompleted = interviews.filter((i) => i.status === "COMPLETED").length;
  const totalCleared = interviews.filter((i) => i.result === "CLEARED").length;
  const todayInterviewsCount = interviews.filter((i) => i.date === todayStr).length;

  const hasActiveFilters =
    searchQuery.trim() !== "" ||
    company !== "all" ||
    statusFilter !== "ALL" ||
    resultFilter !== "ALL" ||
    dateFilter !== "ALL";

  const clearAllFilters = () => {
    setSearchQuery("");
    setCompany("all");
    setStatusFilter("ALL");
    setResultFilter("ALL");
    setDateFilter("ALL");
  };

  // ---- Dialog States ------------------------------------------------
  const [scheduling, setScheduling] = useState(false);
  const [rescheduling, setRescheduling] = useState<InterviewWithCandidate | null>(null);
  const [cancelling, setCancelling] = useState<InterviewWithCandidate | null>(null);
  const [recording, setRecording] = useState<InterviewWithCandidate | null>(null);
  const [viewingFeedback, setViewingFeedback] = useState<InterviewWithCandidate | null>(null);

  const [fResult, setFResult] = useState<"SELECTED" | "REJECTED" | "ON_HOLD">("SELECTED");
  const [fFeedback, setFFeedback] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const [fStudentId, setFStudentId] = useState("");
  const [fDriveId, setFDriveId] = useState("");
  const [fRound, setFRound] = useState("");
  const [fMode, setFMode] = useState<"ON_CAMPUS" | "VIRTUAL">("ON_CAMPUS");
  const [fDate, setFDate] = useState("");
  const [fTime, setFTime] = useState("");
  const [fVenue, setFVenue] = useState("");
  const [fPanel, setFPanel] = useState("");

  const openSchedule = () => {
    setFStudentId("");
    setFDriveId(drives[0]?.id ?? "");
    setFRound("");
    setFMode("ON_CAMPUS");
    setFDate("");
    setFTime("");
    setFVenue("");
    setFPanel("");
    setScheduling(true);
  };

  const openReschedule = (slot: InterviewWithCandidate) => {
    setFStudentId(slot.studentId);
    setFDriveId(slot.driveId);
    setFRound(slot.round);
    setFMode(slot.mode === "Virtual" ? "VIRTUAL" : "ON_CAMPUS");
    setFDate(toDateInputValue(slot.date));
    setFTime(slot.time);
    setFVenue(slot.venue);
    setFPanel(slot.panel);
    setRescheduling(slot);
  };

  // Real-time conflict warning detector during scheduling/rescheduling
  const scheduleConflictWarning = useMemo(() => {
    if (!fDate || !fTime) return null;
    const displayDate = toDisplayDateFormat(fDate);
    const conflictsFound: string[] = [];

    // Check candidate booking
    if (fStudentId) {
      const busyStudent = interviews.find(
        (i) =>
          i.status === "SCHEDULED" &&
          i.studentId === fStudentId &&
          i.date === displayDate &&
          i.time === fTime &&
          (!rescheduling || i.id !== rescheduling.id),
      );
      if (busyStudent) {
        conflictsFound.push(`Candidate is already booked with ${busyStudent.companyName} at ${fTime}`);
      }
    }

    // Check venue booking
    if (fVenue) {
      const busyVenue = interviews.find(
        (i) =>
          i.status === "SCHEDULED" &&
          i.venue === fVenue &&
          i.date === displayDate &&
          i.time === fTime &&
          (!rescheduling || i.id !== rescheduling.id),
      );
      if (busyVenue) {
        conflictsFound.push(`Venue "${fVenue}" is already booked for ${busyVenue.companyName}`);
      }
    }

    // Check panel booking
    if (fPanel) {
      const busyPanel = interviews.find(
        (i) =>
          i.status === "SCHEDULED" &&
          i.panel === fPanel &&
          i.date === displayDate &&
          i.time === fTime &&
          (!rescheduling || i.id !== rescheduling.id),
      );
      if (busyPanel) {
        conflictsFound.push(`Panel "${fPanel}" is assigned to another interview at this time`);
      }
    }

    return conflictsFound.length > 0 ? conflictsFound : null;
  }, [fDate, fTime, fStudentId, fVenue, fPanel, interviews, rescheduling]);

  const submitSchedule = async () => {
    const local = new Date(`${fDate}T${fTime}:00`);
    const scheduledAt =
      fDate && fTime && !Number.isNaN(local.getTime()) ? local.toISOString() : "";
    if (!fStudentId || !fDriveId || !scheduledAt) {
      pushToast("Missing details", "Candidate, drive and date + time are required.");
      return;
    }
    setSubmitting(true);
    try {
      const drive = drives.find((d) => d.id === fDriveId);
      await scheduleInterview({
        studentId: fStudentId,
        driveId: fDriveId,
        mode: fMode,
        scheduledAt,
        round: fRound.trim() || null,
        venue: fVenue.trim() || null,
        panel: fPanel.trim() || null,
        companyName: drive?.companyName ?? null,
      });
      pushToast(
        "Interview scheduled",
        `${studentById.get(fStudentId)?.name ?? "Candidate"} booked for ${fDate} ${fTime}.`,
      );
      reload();
      setScheduling(false);
    } catch (err) {
      pushToast(
        "Schedule failed",
        err instanceof Error ? err.message : "Could not schedule the interview.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const submitReschedule = async () => {
    if (!rescheduling) return;
    const local = new Date(`${fDate}T${fTime}:00`);
    const scheduledAt =
      fDate && fTime && !Number.isNaN(local.getTime()) ? local.toISOString() : undefined;
    setSubmitting(true);
    try {
      await rescheduleInterview(rescheduling.id, {
        scheduledAt,
        mode: fMode,
        round: fRound.trim() || null,
        venue: fVenue.trim() || null,
        panel: fPanel.trim() || null,
      });
      pushToast("Interview rescheduled", "The slot has been updated.");
      reload();
      setRescheduling(null);
    } catch (err) {
      pushToast(
        "Reschedule failed",
        err instanceof Error ? err.message : "Could not reschedule the interview.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const openRecord = (slot: InterviewWithCandidate) => {
    setFResult("SELECTED");
    setFFeedback(slot.feedback ?? "");
    setRecording(slot);
  };

  const submitRecord = async () => {
    if (!recording) return;
    setSubmitting(true);
    try {
      await recordInterviewResult(recording.id, {
        result: fResult,
        feedback: fFeedback.trim() || null,
      });
      pushToast(
        "Result recorded",
        `${recording.student?.name ?? "Candidate"} marked as ${fResult}.`,
      );
      reload();
      setRecording(null);
    } catch (err) {
      pushToast(
        "Record failed",
        err instanceof Error ? err.message : "Could not record the result.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const confirmCancel = async () => {
    if (!cancelling) return;
    setSubmitting(true);
    try {
      await cancelInterview(cancelling.id);
      pushToast("Interview cancelled", "The slot has been cancelled.");
      reload();
      setCancelling(null);
    } catch (err) {
      pushToast(
        "Cancel failed",
        err instanceof Error ? err.message : "Could not cancel the interview.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const scheduleFields = (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-1.5">
        <Label htmlFor="int-round">Round</Label>
        <Input
          id="int-round"
          value={fRound}
          onChange={(e) => setFRound(e.target.value)}
          placeholder="e.g. Technical Round 1"
        />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="int-mode">Mode</Label>
        <select
          id="int-mode"
          value={fMode}
          onChange={(e) => setFMode(e.target.value as "ON_CAMPUS" | "VIRTUAL")}
          className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option value="ON_CAMPUS">On Campus (In-Person)</option>
          <option value="VIRTUAL">Virtual (Online Link)</option>
        </select>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="int-date">
          Date <span className="text-risk">*</span>
        </Label>
        <Input id="int-date" type="date" value={fDate} onChange={(e) => setFDate(e.target.value)} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="int-time">
          Time <span className="text-risk">*</span>
        </Label>
        <Input id="int-time" type="time" value={fTime} onChange={(e) => setFTime(e.target.value)} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="int-venue">Venue</Label>
        <select
          id="int-venue"
          value={fVenue}
          onChange={(e) => setFVenue(e.target.value)}
          className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option value="">No venue assigned</option>
          {activeVenues.map((v) => (
            <option key={v.id} value={v.name}>
              {v.name}
              {v.capacity ? ` (cap ${v.capacity})` : ""}
            </option>
          ))}
          {fVenue && !activeVenues.some((v) => v.name === fVenue) && (
            <option value={fVenue}>{fVenue} (inactive)</option>
          )}
        </select>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="int-panel">Panel</Label>
        <select
          id="int-panel"
          value={fPanel}
          onChange={(e) => setFPanel(e.target.value)}
          className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option value="">No panel assigned</option>
          {activePanels.map((p) => (
            <option key={p.id} value={p.name}>
              {p.name}
            </option>
          ))}
          {fPanel && !activePanels.some((p) => p.name === fPanel) && (
            <option value={fPanel}>{fPanel} (inactive)</option>
          )}
        </select>
      </div>

      {scheduleConflictWarning && (
        <div className="sm:col-span-2 rounded-lg border border-risk/40 bg-risk/10 p-3 text-xs text-risk space-y-1">
          <div className="flex items-center gap-1.5 font-medium">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>Scheduling Conflict Warning</span>
          </div>
          <ul className="list-disc list-inside space-y-0.5 text-foreground/80 pl-1">
            {scheduleConflictWarning.map((warning, idx) => (
              <li key={idx}>{warning}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );

  return (
    <div className="mx-auto max-w-6xl space-y-6 pb-12">
      <PageHeader
        eyebrow="Operations"
        title="Interview Scheduling"
        subtitle="Manage live rounds, monitor venue/panel utilisation, and record student outcomes."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => reload()} disabled={loading}>
              <RotateCcw className={`h-3.5 w-3.5 mr-1.5 ${loading ? "animate-spin" : ""}`} /> Refresh
            </Button>
            <Button variant="glow" onClick={openSchedule} disabled={drives.length === 0}>
              <Plus className="h-4 w-4 mr-1.5" /> Schedule Interview
            </Button>
          </div>
        }
      />

      {/* KPI Stat Cards */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard
          label="Scheduled Slots"
          value={totalScheduled}
          icon={CalendarClock}
          accent="violet"
          trend={todayInterviewsCount > 0 ? `${todayInterviewsCount} scheduled today` : undefined}
          trendPositive={true}
        />
        <StatCard
          label="Completed Rounds"
          value={totalCompleted}
          icon={ClipboardCheck}
        />
        <StatCard
          label="Rounds Cleared"
          value={totalCleared}
          accent="success"
          trend={totalCompleted > 0 ? `${Math.round((totalCleared / totalCompleted) * 100)}% clearance rate` : undefined}
          trendPositive={true}
        />
        <StatCard
          label="Open Conflicts"
          value={conflictCount}
          icon={AlertTriangle}
          accent={conflictCount > 0 ? "risk" : "success"}
          trend={conflictCount > 0 ? "Requires resolution" : "Schedule clean"}
          trendPositive={conflictCount === 0}
        />
      </div>

      {/* Conflicts Alert Banner */}
      {conflictCount > 0 && (
        <DepthCard className="border-risk/30 bg-risk/5 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <AlertTriangle className="h-4 w-4 shrink-0 text-risk" />
              <p className="text-sm font-medium text-foreground">
                {conflictCount} scheduling conflict{conflictCount === 1 ? "" : "s"} detected across
                the active timetable.
              </p>
            </div>
            <Link
              href="/officer/orchestrator"
              className="text-xs font-semibold text-violet-bright hover:underline inline-flex items-center gap-1"
            >
              Resolve in Drive Orchestrator →
            </Link>
          </div>
        </DepthCard>
      )}

      {/* Venues & Panels Utilisation */}
      <DataReveal stagger className="grid gap-5 lg:grid-cols-2">
        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <DoorOpen className="h-4 w-4 text-violet-bright" />
                <CardTitle className="text-base">Venue Utilisation</CardTitle>
              </div>
              <Badge variant="muted" className="text-xs">
                {activeVenues.length} active
              </Badge>
            </div>
            <div className="space-y-3.5">
              {venues.map((v) => {
                // Count schedule events + direct interview slots booked in this venue
                const eventCount = events.filter((e) => e.venueId === v.id).length;
                const slotCount = interviews.filter((i) => i.status === "SCHEDULED" && i.venue === v.name).length;
                const totalBooked = Math.max(eventCount, slotCount);
                const capacity = v.capacity ?? 10;
                const pct = Math.min(100, Math.round((totalBooked / Math.max(1, capacity)) * 100));

                return (
                  <div key={v.id} className="space-y-1">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-medium text-foreground">{v.name}</span>
                      <span className="text-muted-foreground">
                        {totalBooked} booked · Cap {v.capacity ?? "—"} ({pct}%)
                      </span>
                    </div>
                    <Progress value={pct} className="h-1.5" />
                  </div>
                );
              })}
              {venues.length === 0 && (
                <p className="py-6 text-center text-xs text-muted-foreground">
                  No venues configured for this campus.
                </p>
              )}
            </div>
          </DepthCard>
        </DataRevealItem>

        <DataRevealItem>
          <DepthCard className="h-full p-5">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <UserCheck className="h-4 w-4 text-violet-bright" />
                <CardTitle className="text-base">Panel Allocation</CardTitle>
              </div>
              <Badge variant="muted" className="text-xs">
                {activePanels.length} active
              </Badge>
            </div>
            <div className="space-y-2.5">
              {panels.map((p) => {
                const assignedDrives = new Set(
                  events.filter((e) => e.panelId === p.id).map((e) => e.driveId),
                ).size;
                const liveSlotCount = interviews.filter(
                  (i) => i.status === "SCHEDULED" && i.panel === p.name,
                ).length;

                return (
                  <div
                    key={p.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-border/70 bg-card/40 p-2.5 transition hover:border-border"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{p.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {p.members.map((m) => m.name).join(", ") || "No members listed"}
                      </p>
                    </div>
                    <div className="flex items-center gap-1.5 shrink-0">
                      {liveSlotCount > 0 && (
                        <Badge variant="outline" className="text-[11px]">
                          {liveSlotCount} slot{liveSlotCount === 1 ? "" : "s"}
                        </Badge>
                      )}
                      <Badge variant={assignedDrives > 1 ? "warning" : "muted"}>
                        {assignedDrives} drive{assignedDrives === 1 ? "" : "s"}
                      </Badge>
                    </div>
                  </div>
                );
              })}
              {panels.length === 0 && (
                <p className="py-6 text-center text-xs text-muted-foreground">
                  No interview panels configured for this campus.
                </p>
              )}
            </div>
          </DepthCard>
        </DataRevealItem>
      </DataReveal>

      {/* Main Interviews Table & Filter Toolbar */}
      <div className="space-y-3">
        {/* Status Filter Tabs */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-border bg-card p-1">
            <button
              type="button"
              onClick={() => setStatusFilter("ALL")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusFilter === "ALL"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              All Slots ({interviews.length})
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter("SCHEDULED")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusFilter === "SCHEDULED"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Scheduled ({totalScheduled})
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter("COMPLETED")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusFilter === "COMPLETED"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Completed ({totalCompleted})
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter("CANCELLED")}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition ${
                statusFilter === "CANCELLED"
                  ? "bg-violet-bright/15 text-violet-bright font-semibold shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Cancelled ({interviews.filter((i) => i.status === "CANCELLED").length})
            </button>
          </div>

          {hasActiveFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={clearAllFilters}
              className="text-xs text-muted-foreground hover:text-foreground"
            >
              <RotateCcw className="h-3.5 w-3.5 mr-1" /> Reset Filters
            </Button>
          )}
        </div>

        {/* Filter Controls Bar */}
        <div className="grid gap-2.5 sm:grid-cols-2 md:grid-cols-4">
          {/* Search Box */}
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search candidate, roll no, round..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-8 text-xs h-9"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="absolute right-2.5 top-2.5 text-muted-foreground hover:text-foreground"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>

          {/* Company Filter */}
          <Select value={company} onValueChange={setCompany}>
            <SelectTrigger className="h-9 text-xs" aria-label="Filter by company">
              <SelectValue placeholder="All Companies" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Companies</SelectItem>
              {companies.map((c) => (
                <SelectItem key={c} value={c}>
                  {c}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* Result Filter */}
          <Select value={resultFilter} onValueChange={(v) => setResultFilter(v as typeof resultFilter)}>
            <SelectTrigger className="h-9 text-xs" aria-label="Filter by result">
              <SelectValue placeholder="All Results" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Outcomes</SelectItem>
              <SelectItem value="CLEARED">Cleared / Selected</SelectItem>
              <SelectItem value="NOT CLEARED">Not Cleared / Rejected</SelectItem>
              <SelectItem value="AWAITED">Awaited / Pending</SelectItem>
            </SelectContent>
          </Select>

          {/* Date Timeline Filter */}
          <Select value={dateFilter} onValueChange={(v) => setDateFilter(v as typeof dateFilter)}>
            <SelectTrigger className="h-9 text-xs" aria-label="Filter by timeline">
              <SelectValue placeholder="Timeline" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Dates</SelectItem>
              <SelectItem value="TODAY">Today&apos;s Rounds</SelectItem>
              <SelectItem value="UPCOMING">Upcoming Slots</SelectItem>
              <SelectItem value="PAST">Past Rounds</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Interviews Data Table */}
        <Card className="overflow-x-auto p-0 border border-border">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/40 hover:bg-muted/40">
                <TableHead className="w-[180px]">Candidate</TableHead>
                <TableHead>Company</TableHead>
                <TableHead>Round</TableHead>
                <TableHead>Date & Time</TableHead>
                <TableHead>Mode / Venue</TableHead>
                <TableHead>Panel</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Outcome</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                // Shimmer Skeleton Rows during initial or campus reload
                Array.from({ length: 5 }).map((_, idx) => (
                  <TableRow key={idx} className="animate-pulse">
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div className="h-7 w-7 rounded-full bg-muted" />
                        <div className="h-4 w-24 rounded bg-muted" />
                      </div>
                    </TableCell>
                    <TableCell><div className="h-4 w-20 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-16 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-20 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-24 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-4 w-16 rounded bg-muted" /></TableCell>
                    <TableCell><div className="h-5 w-16 rounded-full bg-muted" /></TableCell>
                    <TableCell><div className="h-5 w-16 rounded-full bg-muted" /></TableCell>
                    <TableCell className="text-right"><div className="h-7 w-16 rounded bg-muted ml-auto" /></TableCell>
                  </TableRow>
                ))
              ) : error ? (
                <TableRow>
                  <TableCell colSpan={9} className="py-12 text-center">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <AlertTriangle className="h-6 w-6 text-risk" />
                      <p className="text-sm font-medium text-foreground">Failed to load interview data</p>
                      <p className="text-xs text-muted-foreground">{error}</p>
                      <Button variant="outline" size="sm" onClick={() => reload()} className="mt-2">
                        Try Again
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ) : filteredRows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} className="py-12 text-center">
                    <div className="flex flex-col items-center justify-center gap-2">
                      <Calendar className="h-8 w-8 text-muted-foreground/60" />
                      <p className="text-sm font-medium text-foreground">
                        {hasActiveFilters ? "No matching interviews found" : "No interviews scheduled yet"}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {hasActiveFilters
                          ? "Try clearing or broadening your search filters."
                          : "Schedule candidate interview rounds to start tracking outcomes."}
                      </p>
                      {hasActiveFilters ? (
                        <Button variant="outline" size="sm" onClick={clearAllFilters} className="mt-2 text-xs">
                          Clear Filters
                        </Button>
                      ) : (
                        <Button variant="glow" size="sm" onClick={openSchedule} className="mt-2 text-xs">
                          <Plus className="h-3.5 w-3.5 mr-1" /> Schedule Interview
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ) : (
                filteredRows.map((i) => (
                  <TableRow key={i.id} className="transition hover:bg-muted/30">
                    <TableCell>
                      <div className="flex items-center gap-2.5">
                        <Avatar className="h-7 w-7">
                          <AvatarFallback className="text-[10px] font-semibold">
                            {i.student?.avatarInitials ?? "CD"}
                          </AvatarFallback>
                        </Avatar>
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-foreground truncate">{i.student?.name}</p>
                          <p className="text-[10px] text-muted-foreground">{i.student?.rollNumber || "ID: " + i.studentId.slice(0, 6)}</p>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-xs font-medium text-foreground">{i.companyName || "—"}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">{i.round || "Round 1"}</TableCell>
                    <TableCell className="text-xs">
                      <div className="space-y-0.5">
                        <span className="font-medium text-foreground block">{i.date || "—"}</span>
                        <span className="text-[11px] text-muted-foreground block tabular-nums">{i.time || "—"}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        {i.mode === "Virtual" ? (
                          <Video className="h-3.5 w-3.5 text-violet-bright shrink-0" />
                        ) : (
                          <MapPin className="h-3.5 w-3.5 text-foreground/70 shrink-0" />
                        )}
                        <span className="truncate">{i.venue || (i.mode === "Virtual" ? "Online Link" : "TBD")}</span>
                      </span>
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{i.panel || "—"}</TableCell>
                    <TableCell>
                      <Badge variant={interviewStatusTone[i.status]}>{i.status}</Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1.5">
                        <Badge variant={resultTone[i.result]}>{i.result}</Badge>
                        {i.feedback && (
                          <button
                            type="button"
                            onClick={() => setViewingFeedback(i)}
                            title="View panel feedback"
                            className="text-muted-foreground hover:text-violet-bright transition"
                          >
                            <MessageSquareText className="h-3.5 w-3.5" />
                          </button>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      {i.status === "SCHEDULED" ? (
                        <div className="flex items-center justify-end gap-1.5">
                          <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => openRecord(i)}>
                            <ClipboardCheck className="h-3 w-3 mr-1" /> Result
                          </Button>
                          <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => openReschedule(i)}>
                            Reschedule
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 text-xs text-risk hover:bg-risk/10"
                            onClick={() => setCancelling(i)}
                          >
                            Cancel
                          </Button>
                        </div>
                      ) : (
                        <div className="flex items-center justify-end">
                          {i.feedback ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-7 text-xs text-violet-bright"
                              onClick={() => setViewingFeedback(i)}
                            >
                              <MessageSquareText className="h-3 w-3 mr-1" /> Notes
                            </Button>
                          ) : (
                            <span className="text-xs text-muted-foreground">—</span>
                          )}
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </Card>
      </div>

      {/* Schedule Interview Dialog */}
      <Dialog open={scheduling} onOpenChange={(open) => !open && setScheduling(false)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Schedule Interview</DialogTitle>
            <DialogDescription>
              Book a new round for a campus candidate on an active recruitment drive.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="int-student">
                  Candidate <span className="text-risk">*</span>
                </Label>
                <select
                  id="int-student"
                  value={fStudentId}
                  onChange={(e) => setFStudentId(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="">Select a candidate…</option>
                  {students.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.rollNumber || s.department || "Student"})
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="int-drive">
                  Drive <span className="text-risk">*</span>
                </Label>
                <select
                  id="int-drive"
                  value={fDriveId}
                  onChange={(e) => setFDriveId(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="">Select a drive…</option>
                  {drives.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.companyName} — {d.role}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            {scheduleFields}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setScheduling(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button variant="glow" onClick={submitSchedule} disabled={submitting}>
              {submitting ? "Scheduling…" : "Schedule Interview"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reschedule Interview Dialog */}
      <Dialog open={!!rescheduling} onOpenChange={(open) => !open && setRescheduling(null)}>
        <DialogContent className="max-w-lg">
          {rescheduling && (
            <>
              <DialogHeader>
                <DialogTitle>Reschedule Interview</DialogTitle>
                <DialogDescription>
                  Update the slot for{" "}
                  <span className="font-semibold text-foreground">
                    {rescheduling.student?.name ?? "this candidate"}
                  </span>
                  . Modify date, time, or assigned venue/panel.
                </DialogDescription>
              </DialogHeader>
              {scheduleFields}
              <DialogFooter>
                <Button variant="outline" onClick={() => setRescheduling(null)} disabled={submitting}>
                  Cancel
                </Button>
                <Button variant="glow" onClick={submitReschedule} disabled={submitting}>
                  {submitting ? "Saving…" : "Save Changes"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* Cancel Confirmation Dialog */}
      <Dialog open={!!cancelling} onOpenChange={(open) => !open && setCancelling(null)}>
        <DialogContent className="max-w-md">
          {cancelling && (
            <>
              <DialogHeader>
                <DialogTitle>Cancel interview slot?</DialogTitle>
                <DialogDescription>
                  Are you sure you want to cancel the {cancelling.round || "interview"} slot for{" "}
                  <span className="font-semibold text-foreground">
                    {cancelling.student?.name ?? "this candidate"}
                  </span>
                  ? The slot will be preserved with status CANCELLED.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setCancelling(null)} disabled={submitting}>
                  Keep Slot
                </Button>
                <Button
                  variant="outline"
                  className="text-risk border-risk/40 hover:bg-risk/10"
                  onClick={confirmCancel}
                  disabled={submitting}
                >
                  {submitting ? "Cancelling…" : "Cancel Interview"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* Record Result Dialog */}
      <Dialog open={!!recording} onOpenChange={(open) => !open && setRecording(null)}>
        <DialogContent className="max-w-md">
          {recording && (
            <>
              <DialogHeader>
                <DialogTitle>Record Interview Outcome</DialogTitle>
                <DialogDescription>
                  Record panel assessment for{" "}
                  <span className="font-semibold text-foreground">
                    {recording.student?.name ?? "this candidate"}
                  </span>{" "}
                  ({recording.companyName} — {recording.round || "Round"}). Selected/Rejected automatically advances their placement funnel.
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="rec-result">Outcome Decision</Label>
                  <select
                    id="rec-result"
                    value={fResult}
                    onChange={(e) => setFResult(e.target.value as typeof fResult)}
                    className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  >
                    {RESULT_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="rec-feedback">Panel Feedback & Evaluation Notes</Label>
                  <textarea
                    id="rec-feedback"
                    value={fFeedback}
                    onChange={(e) => setFFeedback(e.target.value)}
                    placeholder="Candidate performance, technical competency notes, behavioral assessment..."
                    rows={4}
                    className="flex w-full rounded-md border border-border bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setRecording(null)} disabled={submitting}>
                  Cancel
                </Button>
                <Button variant="glow" onClick={submitRecord} disabled={submitting}>
                  {submitting ? "Recording…" : "Save Result"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* View Feedback Dialog */}
      <Dialog open={!!viewingFeedback} onOpenChange={(open) => !open && setViewingFeedback(null)}>
        <DialogContent className="max-w-md">
          {viewingFeedback && (
            <>
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                  <MessageSquareText className="h-5 w-5 text-violet-bright" />
                  Interview Evaluation & Notes
                </DialogTitle>
                <DialogDescription>
                  Recorded result for {viewingFeedback.student?.name} ({viewingFeedback.companyName})
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-3.5">
                <div className="flex items-center justify-between rounded-lg border border-border/80 bg-muted/20 p-3">
                  <div>
                    <p className="text-xs text-muted-foreground">{viewingFeedback.round || "Interview Round"}</p>
                    <p className="text-sm font-semibold">{viewingFeedback.date} at {viewingFeedback.time}</p>
                  </div>
                  <Badge variant={resultTone[viewingFeedback.result]}>{viewingFeedback.result}</Badge>
                </div>

                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Panel Evaluator Feedback</Label>
                  <div className="rounded-lg border border-border bg-card p-3.5 text-xs text-foreground leading-relaxed whitespace-pre-wrap">
                    {viewingFeedback.feedback ? viewingFeedback.feedback : "No written notes recorded for this round."}
                  </div>
                </div>

                <div className="flex items-center justify-between text-[11px] text-muted-foreground pt-1">
                  <span>Panel: {viewingFeedback.panel || "Not specified"}</span>
                  <span>Venue: {viewingFeedback.venue || viewingFeedback.mode}</span>
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" size="sm" onClick={() => setViewingFeedback(null)}>
                  Close
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
