"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  Users,
  ShieldCheck,
  ShieldX,
  CheckCircle2,
  ArrowUpRight,
  ListChecks,
  Search,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DemoDataBadge } from "@/components/layout/DemoDataBadge";
import { StagedRunner } from "@/components/intelligence/StagedRunner";
import {
  IntelligencePulse,
  IntelligencePulseBadge,
} from "@/components/intelligence/IntelligencePulse";
import { listMatchesForDrive } from "@/lib/api/matches";
import { listDrivesByCompany } from "@/lib/api/drives";
import { listStudents } from "@/lib/api/students";
import { listApplicationsByDrive, updateApplicationStatus } from "@/lib/api/applications";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import { shortlistingSteps } from "@/lib/simulate";
import {
  applicationStatusLabel,
  applicationStatusTone,
  nextApplicationStatuses,
} from "@/lib/status";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";
import type { Application, ApplicationStatus, CandidateMatch, Drive, Recruiter, Student } from "@/types";

type RunState = "idle" | "running";
type Filter = "all" | "eligible" | "shortlisted";

export function CandidatePool() {
  const [driveId, setDriveId] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");
  const [runState, setRunState] = useState<RunState>("idle");

  // Deep-link support: /recruiter/candidates?drive=<id> preselects that drive
  // (linked from the company profile's per-drive "View Candidates Pool" action).
  useEffect(() => {
    const param = new URLSearchParams(window.location.search).get("drive");
    if (param) setDriveId(param);
  }, []);

  const shortlisted = useAppStore((s) => s.shortlisted);
  const shortlistCandidates = useAppStore((s) => s.shortlistCandidates);
  const pushToast = useAppStore((s) => s.pushToast);

  // Recruiter's drives, the ranked pool per drive, and the student directory — all from the
  // API layer (live in live mode, mock otherwise). The pool is the Phase B explainable match.
  // The signed-in recruiter resolves server-side via /recruiters/me — no hardcoded id.
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
  const effectiveDriveId = driveId || companyDrives[0]?.id || "";
  const drive = companyDrives.find((d) => d.id === effectiveDriveId);

  const { data: matches, loading: matchesLoading } = useApiResource<CandidateMatch[]>(
    () => (effectiveDriveId ? listMatchesForDrive(effectiveDriveId) : Promise.resolve([])),
    [effectiveDriveId],
    [],
  );
  const { data: allStudents } = useApiResource<Student[]>(() => listStudents(), [], []);
  const { data: driveApplications, reload: reloadApps } = useApiResource<Application[]>(
    () => (effectiveDriveId ? listApplicationsByDrive(effectiveDriveId) : Promise.resolve([])),
    [effectiveDriveId],
    [],
  );
  const studentById = useMemo(() => {
    const map = new Map<string, Student>();
    allStudents.forEach((s) => map.set(s.id, s));
    return map;
  }, [allStudents]);
  const applicationByStudent = useMemo(() => {
    const map = new Map<string, Application>();
    driveApplications.forEach((a) => map.set(a.studentId, a));
    return map;
  }, [driveApplications]);

  const rows = useMemo(
    () =>
      matches
        .map((m) => {
          const student = studentById.get(m.studentId);
          return {
            match: m,
            student,
            // Prefer the deterministic client check when we have the drive + student;
            // otherwise fall back to the match's eligibility contribution (100 = passed).
            eligible: student && drive
              ? evaluateEligibility(student, drive).eligible
              : m.breakdown.eligibility === 100,
            application: student ? applicationByStudent.get(student.id) : undefined,
          };
        })
        .filter((r) => Boolean(r.student)),
    [matches, studentById, drive, applicationByStudent],
  );

  const visible = rows
    .filter((r) =>
      filter === "all"
        ? true
        : filter === "eligible"
          ? r.eligible
          : shortlisted.includes(r.student!.id),
    )
    .filter((r) => (query ? r.student!.name.toLowerCase().includes(query.toLowerCase()) : true));

  const toggle = (studentId: string) => {
    setSelected((prev) =>
      prev.includes(studentId) ? prev.filter((id) => id !== studentId) : [...prev, studentId],
    );
  };

  const selectableIds = visible.filter((r) => r.eligible).map((r) => r.student!.id);
  const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selected.includes(id));

  const toggleAll = () => {
    setSelected(allSelected ? [] : selectableIds);
  };

  const runShortlist = () => {
    if (selected.length === 0) return;
    setRunState("running");
  };

  const completeShortlist = async () => {
    // Advance every selected candidate that already has an application on this drive to
    // SHORTLISTED via the live PATCH endpoint; keep the local highlight for immediate feedback.
    shortlistCandidates(selected);
    const withApplication = selected
      .map((id) => applicationByStudent.get(id))
      .filter((a): a is Application => Boolean(a));
    const results = await Promise.allSettled(
      withApplication.map((a) => updateApplicationStatus(a.id, "shortlisted")),
    );
    const advanced = results.filter((r) => r.status === "fulfilled").length;
    const failed = results.length - advanced;
    const names = selected
      .map((id) => studentById.get(id)?.name)
      .filter(Boolean)
      .join(", ");
    if (failed > 0) {
      pushToast(
        "Some updates failed",
        `${advanced} advanced; ${failed} could not be shortlisted (check status/permissions).`,
      );
    } else {
      pushToast(
        "Candidates shortlisted",
        withApplication.length > 0
          ? `${advanced} candidates advanced to SHORTLISTED: ${names}.`
          : `${selected.length} highlighted: ${names}.`,
      );
    }
    reloadApps();
    setSelected([]);
    setRunState("idle");
  };

  // Per-candidate funnel control: move one application to a specific legal next stage.
  // The backend PATCH validates the transition (409 on an illegal move); we only ever
  // offer statuses `nextApplicationStatuses` allows, then refetch to reflect the result.
  const changeStage = async (application: Application, target: ApplicationStatus, name: string) => {
    try {
      await updateApplicationStatus(application.id, target);
      pushToast("Stage updated", `${name} moved to ${applicationStatusLabel[target]}.`);
      reloadApps();
    } catch {
      pushToast("Update failed", `Could not move ${name} to ${applicationStatusLabel[target]}.`);
    }
  };

  const eligibleCount = rows.filter((r) => r.eligible).length;
  const shortlistedCount = rows.filter((r) => shortlisted.includes(r.student!.id)).length;
  const avgFit = rows.length
    ? Math.round(rows.reduce((n, r) => n + r.match.overallFit, 0) / rows.length)
    : 0;

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow={recruiter?.companyName ?? "Candidate Pool"}
        title="Candidate Pool"
        subtitle="Ranked candidates with their eligibility verdict, fit score and pipeline status."
        actions={<DemoDataBadge />}
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
        <StatCard label="Candidates" value={rows.length} icon={Users} accent="violet" />
        <StatCard label="Eligible" value={eligibleCount} icon={ShieldCheck} accent="success" />
        <StatCard label="Shortlisted" value={shortlistedCount} icon={CheckCircle2} accent="success" />
        <StatCard label="Average Fit" value={avgFit} suffix="%" />
      </div>

      <IntelligencePulse active={runState === "running"} className="mb-5">
        <Card className="p-4">
          {runState === "running" ? (
            <div className="flex flex-col items-center gap-5 py-4">
              <IntelligencePulseBadge label="Shortlisting candidates" />
              <div className="w-full max-w-xs">
                <StagedRunner steps={shortlistingSteps} onDone={completeShortlist} />
              </div>
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-3">
                <div className="relative min-w-[200px]">
                  <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    placeholder="Search candidate..."
                    className="pl-8"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    aria-label="Search candidates"
                  />
                </div>
                <div className="flex gap-1.5">
                  {(["all", "eligible", "shortlisted"] as Filter[]).map((f) => (
                    <button
                      key={f}
                      onClick={() => setFilter(f)}
                      className={cn(
                        "rounded-full border px-3 py-1.5 text-xs font-medium capitalize transition-colors",
                        filter === f
                          ? "border-violet/50 bg-violet/10 text-violet-bright"
                          : "border-border text-muted-foreground hover:bg-accent",
                      )}
                    >
                      {f}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <AnimatePresence>
                  {selected.length > 0 && (
                    <motion.span
                      initial={{ opacity: 0, x: 8 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 8 }}
                      className="text-xs text-muted-foreground"
                    >
                      {selected.length} selected
                    </motion.span>
                  )}
                </AnimatePresence>
                <Button variant="glow" disabled={selected.length === 0} onClick={runShortlist}>
                  <ListChecks className="h-4 w-4" /> Shortlist Selected
                </Button>
              </div>
            </div>
          )}
        </Card>
      </IntelligencePulse>

      <Card className="overflow-x-auto p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={toggleAll}
                  aria-label="Select all eligible candidates"
                  className="h-4 w-4 cursor-pointer accent-violet-600"
                />
              </TableHead>
              <TableHead>Candidate</TableHead>
              <TableHead>Eligibility</TableHead>
              <TableHead>Fit Score</TableHead>
              <TableHead>Skills</TableHead>
              <TableHead>Projects</TableHead>
              <TableHead>Readiness</TableHead>
              <TableHead>Interview</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {visible.map((r) => {
              const student = r.student!;
              const isShortlisted = shortlisted.includes(student.id);
              return (
                <TableRow
                  key={r.match.id}
                  className={cn(isShortlisted && "bg-success/5", !r.eligible && "opacity-70")}
                >
                  <TableCell>
                    <input
                      type="checkbox"
                      checked={selected.includes(student.id)}
                      onChange={() => toggle(student.id)}
                      disabled={!r.eligible}
                      aria-label={`Select ${student.name}`}
                      className="h-4 w-4 cursor-pointer accent-violet-600 disabled:cursor-not-allowed"
                    />
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2.5">
                      <Avatar className="h-7 w-7">
                        <AvatarFallback className="text-[10px]">
                          {student.avatarInitials}
                        </AvatarFallback>
                      </Avatar>
                      <div className="min-w-0">
                        <span className="block font-medium">{student.name}</span>
                        <span className="block text-[10px] text-muted-foreground">
                          {student.branchCode} · CGPA {student.cgpa.toFixed(2)}
                        </span>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={r.eligible ? "success" : "risk"} className="gap-1">
                      {r.eligible ? (
                        <ShieldCheck className="h-3 w-3" />
                      ) : (
                        <ShieldX className="h-3 w-3" />
                      )}
                      {r.eligible ? "PASS" : "FAIL"}
                    </Badge>
                  </TableCell>
                  <TableCell className="font-semibold tabular-nums text-violet-bright">
                    {r.match.overallFit}%
                  </TableCell>
                  <TableCell className="tabular-nums">{r.match.breakdown.skills}%</TableCell>
                  <TableCell className="tabular-nums">{r.match.breakdown.projects}%</TableCell>
                  <TableCell className="tabular-nums">{student.readiness}</TableCell>
                  <TableCell className="tabular-nums">{r.match.breakdown.interview}%</TableCell>
                  <TableCell>
                    <AnimatePresence mode="wait" initial={false}>
                      <motion.div
                        key={isShortlisted ? "shortlisted" : (r.application?.status ?? "none")}
                        initial={{ opacity: 0, y: -4 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: 4 }}
                        transition={{ duration: 0.2 }}
                      >
                        {isShortlisted ? (
                          <Badge variant="success" className="gap-1">
                            <CheckCircle2 className="h-3 w-3" /> SHORTLISTED
                          </Badge>
                        ) : r.application ? (
                          <Badge variant={applicationStatusTone[r.application.status]}>
                            {applicationStatusLabel[r.application.status]}
                          </Badge>
                        ) : (
                          <span className="text-xs text-muted-foreground">Not applied</span>
                        )}
                      </motion.div>
                    </AnimatePresence>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      {r.application && nextApplicationStatuses(r.application.status).length > 0 && (
                        <Select
                          value=""
                          onValueChange={(target) =>
                            changeStage(r.application!, target as ApplicationStatus, student.name)
                          }
                        >
                          <SelectTrigger
                            className="h-8 w-[9.5rem]"
                            aria-label={`Change stage for ${student.name}`}
                          >
                            <SelectValue placeholder="Move to…" />
                          </SelectTrigger>
                          <SelectContent>
                            {nextApplicationStatuses(r.application.status).map((s) => (
                              <SelectItem key={s} value={s}>
                                {applicationStatusLabel[s]}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                      <Button asChild variant="ghost" size="sm">
                        <Link href={`/recruiter/candidates/${student.id}`}>
                          View <ArrowUpRight className="h-3.5 w-3.5" />
                        </Link>
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
            {visible.length === 0 && (
              <TableRow>
                <TableCell colSpan={10} className="py-10 text-center text-sm text-muted-foreground">
                  {matchesLoading ? "Loading candidate pool…" : "No candidates match this filter."}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Card>

      <Card className="mt-5 p-5">
        <CardTitle className="mb-2">Eligibility is checked before ranking</CardTitle>
        <p className="text-sm text-muted-foreground">
          Candidates who fail the drive&apos;s published criteria stay visible for transparency but
          cannot be selected for shortlisting. Fit score is a prototype estimate and never overrides
          a failing eligibility rule.
        </p>
      </Card>
    </div>
  );
}
