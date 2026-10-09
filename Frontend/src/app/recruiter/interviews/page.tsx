"use client";

import { useState } from "react";
import { CalendarClock, MapPin, Video, Users, CheckCircle2, Clock, XCircle, Plus } from "lucide-react";
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
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { listDrivesByCompany } from "@/lib/api/drives";
import {
  listInterviewsByDrive,
  scheduleInterview,
  rescheduleInterview,
  cancelInterview,
} from "@/lib/api/interviews";
import { listStudents } from "@/lib/api/students";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { listVenues, listPanels } from "@/lib/api/schedules";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { interviewStatusTone } from "@/lib/status";
import type {
  Application,
  ApplicationStatus,
  Drive,
  InterviewSlot,
  Recruiter,
  ScheduleVenue,
  SchedulePanel,
  Student,
} from "@/types";

/**
 * A candidate is schedulable for an interview once they have been shortlisted or advanced further
 * in the drive's funnel. Excludes `eligible`/`applied` (not yet shortlisted) and terminal
 * `rejected`/`withdrawn`.
 */
const SCHEDULABLE_STATUSES = new Set<ApplicationStatus>([
  "shortlisted",
  "assessment",
  "interview",
  "selected",
  "offer",
  "accepted",
  "docs",
  "joined",
]);

const resultTone = { CLEARED: "success", "NOT CLEARED": "risk", AWAITED: "muted" } as const;

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

export default function RecruiterInterviewsPage() {
  const [driveId, setDriveId] = useState("");
  const shortlisted = useAppStore((s) => s.shortlisted);
  const pushToast = useAppStore((s) => s.pushToast);

  // Signed-in recruiter, their drives, the student directory — all live. The drive picker
  // drives the interview fetch; no hardcoded recruiter id or drive constant.
  const { data: recruiter } = useApiResource<Recruiter | null>(
    () => getActiveRecruiter(),
    [],
    null,
  );
  const { data: companyDrives } = useApiResource<Drive[]>(
    () => (recruiter ? listDrivesByCompany(recruiter.id) : Promise.resolve([])),
    [recruiter?.id],
    [],
  );
  const { data: allStudents } = useApiResource<Student[]>(() => listStudents(), [], []);

  // Officer-registered venues & panels — the recruiter picks from the active ones rather than
  // typing free text. Registry is managed in the officer's "Venues & Panels" dashboard.
  const { data: venues } = useApiResource<ScheduleVenue[]>(() => listVenues(), [], []);
  const { data: panels } = useApiResource<SchedulePanel[]>(() => listPanels(), [], []);
  const activeVenues = venues.filter((v) => v.active);
  const activePanels = panels.filter((p) => p.active);

  const effectiveDriveId = driveId || companyDrives[0]?.id || "";
  const {
    data: interviews,
    loading,
    error,
    reload,
  } = useApiResource(
    () => (effectiveDriveId ? listInterviewsByDrive(effectiveDriveId) : Promise.resolve([])),
    [effectiveDriveId],
    [],
  );

  // Candidates schedulable for this drive: only its shortlisted-and-beyond applicants, not the
  // whole campus directory. Re-fetches when the drive picker changes.
  const { data: driveApplications } = useApiResource<Application[]>(
    () => (effectiveDriveId ? listApplicationsByDrive(effectiveDriveId) : Promise.resolve([])),
    [effectiveDriveId],
    [],
  );

  const studentById = new Map(allStudents.map((s) => [s.id, s]));
  const slots = interviews.map((i) => ({ ...i, student: studentById.get(i.studentId) }));

  const candidateStudents = driveApplications
    .filter((a) => SCHEDULABLE_STATUSES.has(a.status))
    .map((a) => studentById.get(a.studentId))
    .filter((s): s is Student => Boolean(s));

  const scheduled = slots.filter((i) => i.status === "SCHEDULED");
  const completed = slots.filter((i) => i.status === "COMPLETED");
  const cleared = slots.filter((i) => i.result === "CLEARED").length;

  /** Shortlisted candidates from this session who have no slot yet — the
   * natural next step after bulk shortlisting. */
  const awaitingSlot = shortlisted
    .map((id) => studentById.get(id))
    .filter((s): s is NonNullable<typeof s> => Boolean(s))
    .filter((s) => !slots.some((i) => i.studentId === s.id));

  // ---- Schedule / reschedule / cancel dialog state -------------------
  const [scheduling, setScheduling] = useState(false);
  const [rescheduling, setRescheduling] = useState<InterviewSlot | null>(null);
  const [cancelling, setCancelling] = useState<InterviewSlot | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [fStudentId, setFStudentId] = useState("");
  const [fRound, setFRound] = useState("");
  const [fMode, setFMode] = useState<"ON_CAMPUS" | "VIRTUAL">("ON_CAMPUS");
  const [fDate, setFDate] = useState("");
  const [fTime, setFTime] = useState("");
  const [fVenue, setFVenue] = useState("");
  const [fPanel, setFPanel] = useState("");

  const openSchedule = (studentId = "") => {
    setFStudentId(studentId);
    setFRound("");
    setFMode("ON_CAMPUS");
    setFDate("");
    setFTime("");
    setFVenue("");
    setFPanel("");
    setScheduling(true);
  };

  const openReschedule = (slot: InterviewSlot) => {
    setFStudentId(slot.studentId);
    setFRound(slot.round);
    setFMode(slot.mode === "Virtual" ? "VIRTUAL" : "ON_CAMPUS");
    setFDate(toDateInputValue(slot.date));
    setFTime(slot.time);
    setFVenue(slot.venue);
    setFPanel(slot.panel);
    setRescheduling(slot);
  };

  const submitSchedule = async () => {
    const local = new Date(`${fDate}T${fTime}:00`);
    const scheduledAt =
      fDate && fTime && !Number.isNaN(local.getTime()) ? local.toISOString() : "";
    if (!fStudentId || !effectiveDriveId || !scheduledAt) {
      pushToast("Missing details", "Candidate, drive and date + time are required.");
      return;
    }
    setSubmitting(true);
    try {
      await scheduleInterview({
        studentId: fStudentId,
        driveId: effectiveDriveId,
        mode: fMode,
        scheduledAt,
        round: fRound.trim() || null,
        venue: fVenue.trim() || null,
        panel: fPanel.trim() || null,
        companyName: recruiter?.companyName ?? null,
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
    <div className="space-y-4">
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
            className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
          >
            <option value="ON_CAMPUS">On Campus</option>
            <option value="VIRTUAL">Virtual</option>
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
            className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
          >
            <option value="">No venue</option>
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
            className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
          >
            <option value="">No panel</option>
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
      </div>
    </div>
  );

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!recruiter}
      onRetry={reload}
      emptyTitle="No company profile"
      emptyMessage="Your recruiter account isn't linked to a company profile yet."
      className="mx-auto max-w-6xl"
    >
      <div className="mx-auto max-w-6xl">
        <PageHeader
          eyebrow={recruiter?.companyName ?? "Interviews"}
          title="Interview Scheduling"
          subtitle="Rounds booked with the placement office, plus shortlisted candidates still awaiting a slot."
          actions={
            <Button variant="glow" onClick={() => openSchedule()} disabled={!effectiveDriveId}>
              <Plus className="h-4 w-4" /> Schedule Interview
            </Button>
          }
        />

        <div className="mb-5 flex flex-wrap items-center gap-3">
          <Select value={effectiveDriveId} onValueChange={setDriveId}>
            <SelectTrigger className="w-full sm:w-72" aria-label="Select drive">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {companyDrives.map((d) => (
                <SelectItem key={d.id} value={d.id}>
                  {d.companyName} — {d.role}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Scheduled" value={scheduled.length} icon={CalendarClock} accent="violet" />
          <StatCard label="Completed" value={completed.length} icon={CheckCircle2} />
          <StatCard label="Rounds Cleared" value={cleared} accent="success" />
          <StatCard label="Awaiting Slot" value={awaitingSlot.length} icon={Clock} accent="warning" />
        </div>

        {awaitingSlot.length > 0 && (
          <DepthCard className="mb-5 border-violet/30 p-4">
            <p className="text-sm font-medium">
              {awaitingSlot.length} shortlisted candidate
              {awaitingSlot.length === 1 ? "" : "s"} awaiting a slot
            </p>
            <div className="mt-2.5 flex flex-wrap gap-2">
              {awaitingSlot.map((s) => (
                <div
                  key={s.id}
                  className="flex items-center gap-2 rounded-lg border border-border py-1 pl-2.5 pr-1"
                >
                  <span className="text-xs font-medium">{s.name}</span>
                  <Button variant="glow" size="sm" onClick={() => openSchedule(s.id)}>
                    <CalendarClock className="h-3.5 w-3.5" /> Schedule
                  </Button>
                </div>
              ))}
            </div>
          </DepthCard>
        )}

        <DataReveal stagger className="mb-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {scheduled.map((i) => (
            <DataRevealItem key={i.id}>
              <DepthCard className="h-full p-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex min-w-0 items-center gap-2.5">
                    <Avatar className="h-8 w-8 shrink-0">
                      <AvatarFallback className="text-[10px]">
                        {i.student?.avatarInitials}
                      </AvatarFallback>
                    </Avatar>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{i.student?.name}</p>
                      <p className="truncate text-xs text-muted-foreground">{i.round}</p>
                    </div>
                  </div>
                  <Badge variant="default" className="shrink-0">
                    {i.date}
                  </Badge>
                </div>
                <div className="mt-3 space-y-1.5 text-xs text-muted-foreground">
                  <p className="flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5" /> {i.time}
                  </p>
                  <p className="flex items-center gap-1.5">
                    {i.mode === "Virtual" ? (
                      <Video className="h-3.5 w-3.5" />
                    ) : (
                      <MapPin className="h-3.5 w-3.5" />
                    )}
                    {i.venue}
                  </p>
                  <p className="flex items-center gap-1.5">
                    <Users className="h-3.5 w-3.5" /> {i.panel}
                  </p>
                </div>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  <Button variant="outline" size="sm" onClick={() => openReschedule(i)}>
                    Reschedule
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-risk"
                    onClick={() => setCancelling(i)}
                  >
                    Cancel
                  </Button>
                </div>
              </DepthCard>
            </DataRevealItem>
          ))}
          {scheduled.length === 0 && (
            <Card className="p-8 text-center text-sm text-muted-foreground sm:col-span-2 lg:col-span-3">
              No upcoming rounds scheduled for this drive.
            </Card>
          )}
        </DataReveal>

        <Card className="overflow-x-auto p-5">
          <CardTitle className="mb-3">All Rounds</CardTitle>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Candidate</TableHead>
                <TableHead>Round</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Time</TableHead>
                <TableHead>Mode / Venue</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Result</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {slots.map((i) => (
                <TableRow key={i.id}>
                  <TableCell>
                    <div className="flex items-center gap-2.5">
                      <Avatar className="h-7 w-7">
                        <AvatarFallback className="text-[10px]">
                          {i.student?.avatarInitials}
                        </AvatarFallback>
                      </Avatar>
                      <span className="font-medium">{i.student?.name}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{i.round}</TableCell>
                  <TableCell className="text-muted-foreground">{i.date}</TableCell>
                  <TableCell className="tabular-nums">{i.time}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {i.mode} · {i.venue}
                  </TableCell>
                  <TableCell>
                    <Badge variant={interviewStatusTone[i.status]}>{i.status}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={resultTone[i.result]} className="gap-1">
                      {i.result === "CLEARED" && <CheckCircle2 className="h-3 w-3" />}
                      {i.result === "NOT CLEARED" && <XCircle className="h-3 w-3" />}
                      {i.result}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {i.status === "SCHEDULED" ? (
                      <div className="flex gap-1.5">
                        <Button variant="outline" size="sm" onClick={() => openReschedule(i)}>
                          Reschedule
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="text-risk"
                          onClick={() => setCancelling(i)}
                        >
                          Cancel
                        </Button>
                      </div>
                    ) : (
                      <span className="text-xs text-muted-foreground">—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {slots.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="py-10 text-center text-sm text-muted-foreground">
                    No rounds recorded for this drive.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Card>
      </div>

      {/* Schedule interview */}
      <Dialog open={scheduling} onOpenChange={(open) => !open && setScheduling(false)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Schedule Interview</DialogTitle>
            <DialogDescription>
              Book a new round for a candidate on the selected drive.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="int-student">
                Candidate <span className="text-risk">*</span>
              </Label>
              <select
                id="int-student"
                value={fStudentId}
                onChange={(e) => setFStudentId(e.target.value)}
                className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
              >
                <option value="">Select a candidate…</option>
                {candidateStudents.length === 0 ? (
                  <option value="" disabled>
                    No shortlisted candidates for this drive yet
                  </option>
                ) : (
                  candidateStudents.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} — {s.rollNumber}
                    </option>
                  ))
                )}
              </select>
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

      {/* Reschedule interview */}
      <Dialog open={!!rescheduling} onOpenChange={(open) => !open && setRescheduling(null)}>
        <DialogContent className="max-w-lg">
          {rescheduling && (
            <>
              <DialogHeader>
                <DialogTitle>Reschedule Interview</DialogTitle>
                <DialogDescription>
                  Update the slot for{" "}
                  <span className="font-medium text-foreground">
                    {studentById.get(rescheduling.studentId)?.name ?? "this candidate"}
                  </span>
                  . Leave date and time set to submit a new time.
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

      {/* Cancel confirmation */}
      <Dialog open={!!cancelling} onOpenChange={(open) => !open && setCancelling(null)}>
        <DialogContent className="max-w-md">
          {cancelling && (
            <>
              <DialogHeader>
                <DialogTitle>Cancel interview?</DialogTitle>
                <DialogDescription>
                  Cancel the {cancelling.round || "interview"} slot for{" "}
                  <span className="font-medium text-foreground">
                    {studentById.get(cancelling.studentId)?.name ?? "this candidate"}
                  </span>
                  ? The record is preserved but the round is marked CANCELLED.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setCancelling(null)} disabled={submitting}>
                  Keep Slot
                </Button>
                <Button
                  variant="outline"
                  className="text-risk"
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
    </AsyncBoundary>
  );
}
