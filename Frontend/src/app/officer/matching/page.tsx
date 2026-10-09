"use client";

import { useMemo, useState } from "react";
import { Target, ShieldCheck, ShieldX, CheckCircle2, XCircle, ThumbsUp } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { MatchBreakdownBars } from "@/components/intelligence/MatchBreakdown";
import { EligibilityChecklist } from "@/components/intelligence/EligibilityChecklist";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { listMatchesForDrive } from "@/lib/api/matches";
import { listStudentsByCampus } from "@/lib/api/students";
import { listDrivesByCampus } from "@/lib/api/drives";
import { listApplicationsByDrive, updateApplicationStatus } from "@/lib/api/applications";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import {
  applicationStatusLabel,
  applicationStatusTone,
  nextApplicationStatuses,
} from "@/lib/status";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";
import type { ApplicationStatus } from "@/types";

export default function OfficerMatchingPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const shortlisted = useAppStore((s) => s.shortlisted);
  const pushToast = useAppStore((s) => s.pushToast);

  const [selectedMatchId, setSelectedMatchId] = useState<string | null>(null);
  const [approved, setApproved] = useState<string[]>([]);
  const [driveId, setDriveId] = useState<string>("");

  // Live: campus drives + roster. Matches are fetched per selected drive below.
  const { data: base, loading, error, reload } = useApiResource(
    async () => {
      const [campusDrives, students] = await Promise.all([
        listDrivesByCampus(campusId),
        listStudentsByCampus(campusId),
      ]);
      return { drives: campusDrives, students };
    },
    [campusId],
    { drives: [], students: [] },
  );

  const studentById = useMemo(
    () => new Map(base.students.map((s) => [s.id, s])),
    [base.students],
  );
  const activeDriveId = driveId || base.drives[0]?.id || "";
  const drive = base.drives.find((d) => d.id === activeDriveId);

  // Live: the explainable ranked candidate pool for the selected drive, joined
  // with the roster for candidate identity and with the per-drive applications.
  const { data: matchData, loading: matchLoading, error: matchError, reload: reloadMatches } =
    useApiResource(
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

  const appByStudent = useMemo(
    () => new Map(matchData.applications.map((a) => [a.studentId, a])),
    [matchData.applications],
  );
  const rows = useMemo(
    () =>
      matchData.matches.map((m) => {
        const student = studentById.get(m.studentId);
        return {
          match: m,
          student,
          eligible: student && drive ? evaluateEligibility(student, drive).eligible : false,
          application: appByStudent.get(m.studentId),
        };
      }),
    [matchData.matches, studentById, appByStudent, drive],
  );

  const selected = rows.find((r) => r.match.id === selectedMatchId);

  const eligibleCount = rows.filter((r) => r.eligible).length;
  const avgFit = rows.length
    ? Math.round(rows.reduce((n, r) => n + r.match.overallFit, 0) / rows.length)
    : 0;
  const recommendedCount = rows.filter(
    (r) => r.eligible && r.match.overallFit >= 83,
  ).length;

  const approveShortlist = async () => {
    // Persist the shortlist for real: every recommended, eligible candidate that has an
    // application still at APPLIED is advanced to SHORTLISTED via the live PATCH endpoint
    // (the backend validates the transition). Candidates already past APPLIED are left as-is.
    const recommended = rows.filter(
      (r) => r.eligible && r.match.overallFit >= 83 && r.application?.status === "applied",
    );
    setApproved(rows.filter((r) => r.eligible && r.match.overallFit >= 83).map((r) => r.match.id));
    if (recommended.length === 0) {
      pushToast(
        "Nothing to shortlist",
        "No recommended candidate has an application waiting at APPLIED on this drive.",
      );
      return;
    }
    const results = await Promise.allSettled(
      recommended.map((r) => updateApplicationStatus(r.application!.id, "shortlisted")),
    );
    const advanced = results.filter((res) => res.status === "fulfilled").length;
    const failed = results.length - advanced;
    pushToast(
      failed > 0 ? "Some updates failed" : "Shortlist approved",
      failed > 0
        ? `${advanced} advanced to SHORTLISTED; ${failed} failed (check status/permissions).`
        : `${advanced} candidates advanced to SHORTLISTED for ${drive?.role ?? "this drive"}.`,
    );
    reloadMatches();
  };

  // Per-candidate funnel control for the officer: move one application to a specific legal
  // next stage. The backend PATCH validates the transition (409 on an illegal move); the UI
  // only offers statuses `nextApplicationStatuses` allows, then refetches.
  const changeStage = async (applicationId: string, target: ApplicationStatus, name: string) => {
    try {
      await updateApplicationStatus(applicationId, target);
      pushToast("Stage updated", `${name} moved to ${applicationStatusLabel[target]}.`);
      reloadMatches();
    } catch {
      pushToast("Update failed", `Could not move ${name} to ${applicationStatusLabel[target]}.`);
    }
  };

  /** Candidates the recruiter shortlisted this session, surfaced here so
   * the officer sees the recruiter's action without a backend. */
  const recruiterShortlisted = base.students.filter((s) => shortlisted.includes(s.id));

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Placement"
        title="AI Match Review"
        subtitle="Review every candidate-drive pair — eligibility verdict alongside the explainable match score."
        actions={
          <Button variant="glow" onClick={approveShortlist}>
            <ThumbsUp className="h-4 w-4" /> Approve Recommended Shortlist
          </Button>
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
      </div>

      <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard label="Candidates Matched" value={rows.length} icon={Target} accent="violet" />
        <StatCard label="Eligible" value={eligibleCount} icon={ShieldCheck} accent="success" />
        <StatCard label="Average Fit" value={avgFit} suffix="%" />
        <StatCard label="Recommended" value={recommendedCount} icon={ThumbsUp} accent="success" />
      </div>

      {recruiterShortlisted.length > 0 && (
        <DepthCard className="mb-5 border-violet/30 p-4">
          <p className="text-xs font-semibold uppercase tracking-wider text-violet-bright">
            Shortlisted by the recruiter this session
          </p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {recruiterShortlisted.map((s) => (
              <Badge key={s.id} variant="default">
                {s.name}
              </Badge>
            ))}
          </div>
        </DepthCard>
      )}

      <AsyncBoundary
        loading={loading || matchLoading}
        error={error ?? matchError}
        empty={rows.length === 0}
        onRetry={() => {
          reload();
          reloadMatches();
        }}
        emptyTitle="No candidate matches"
        emptyMessage="No ranked candidates are available for the selected drive yet."
      >
      <Card className="overflow-x-auto p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Rank</TableHead>
              <TableHead>Candidate</TableHead>
              <TableHead>Eligibility</TableHead>
              <TableHead>Fit Score</TableHead>
              <TableHead>Readiness</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Recommendation</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((r, i) => {
              if (!r.student) return null;
              const isApproved = approved.includes(r.match.id);
              return (
                <TableRow
                  key={r.match.id}
                  className={cn("cursor-pointer", isApproved && "bg-success/5")}
                  onClick={() => setSelectedMatchId(r.match.id)}
                >
                  <TableCell className="tabular-nums text-muted-foreground">{i + 1}</TableCell>
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
                  <TableCell className="tabular-nums">{r.student.readiness}</TableCell>
                  <TableCell>
                    {r.application ? (
                      <Badge variant={applicationStatusTone[r.application.status]}>
                        {applicationStatusLabel[r.application.status]}
                      </Badge>
                    ) : (
                      <span className="text-xs text-muted-foreground">Not applied</span>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {isApproved ? (
                      <span className="flex items-center gap-1 text-success">
                        <CheckCircle2 className="h-3.5 w-3.5" /> Shortlist approved
                      </span>
                    ) : (
                      r.match.recommendation
                    )}
                  </TableCell>
                  <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                    <div className="flex items-center justify-end gap-2">
                      {r.application && nextApplicationStatuses(r.application.status).length > 0 && (
                        <Select
                          value=""
                          onValueChange={(target) =>
                            changeStage(r.application!.id, target as ApplicationStatus, r.student!.name)
                          }
                        >
                          <SelectTrigger
                            className="h-8 w-[9.5rem]"
                            aria-label={`Change stage for ${r.student.name}`}
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
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedMatchId(r.match.id);
                        }}
                      >
                        <Target className="h-3.5 w-3.5" /> Breakdown
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Card>
      </AsyncBoundary>

      <Dialog open={!!selected} onOpenChange={(open) => !open && setSelectedMatchId(null)}>
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          {selected?.student && drive && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {selected.student.name} → {drive.companyName}
                </DialogTitle>
                <DialogDescription>
                  {drive.role} · Overall Fit{" "}
                  <span className="font-semibold text-violet-bright">
                    {selected.match.overallFit}%
                  </span>
                </DialogDescription>
              </DialogHeader>

              <div>
                <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Eligibility (rule check)
                </p>
                <EligibilityChecklist student={selected.student} drive={drive} compact />
              </div>

              <div>
                <p className="mb-2 mt-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Match breakdown (server-computed)
                </p>
                <MatchBreakdownBars breakdown={selected.match.breakdown} />
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-success">
                    <CheckCircle2 className="h-3.5 w-3.5" /> Strengths
                  </p>
                  <ul className="space-y-1">
                    {selected.match.whySelected.map((w) => (
                      <li key={w} className="text-xs text-muted-foreground">
                        • {w}
                      </li>
                    ))}
                  </ul>
                </div>
                <div>
                  <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-risk">
                    <XCircle className="h-3.5 w-3.5" /> Gaps
                  </p>
                  <ul className="space-y-1">
                    {selected.match.weakOrMissing.map((w) => (
                      <li key={w} className="text-xs text-muted-foreground">
                        • {w}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="rounded-lg border border-violet/30 bg-violet/5 p-3">
                <p className="text-xs font-semibold text-violet-bright">Recommendation</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {selected.match.recommendation}
                </p>
              </div>
              <CardTitle className="sr-only">Ranking note</CardTitle>
              <Badge variant="muted" className="w-fit">
                Server-ranked · explainable match
              </Badge>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
