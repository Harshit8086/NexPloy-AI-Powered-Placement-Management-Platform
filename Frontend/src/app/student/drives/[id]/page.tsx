"use client";

import {
  MapPin,
  IndianRupee,
  Calendar,
  ListChecks,
  Users,
  Building2,
  ShieldCheck,
  ShieldX,
  Sparkles,
  Video,
  ArrowUpRight,
} from "lucide-react";
import Link from "next/link";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/layout/PageHeader";
import { JourneyTimeline } from "@/components/intelligence/JourneyTimeline";
import { EligibilityChecklist } from "@/components/intelligence/EligibilityChecklist";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { VerificationBadge } from "@/components/skills/VerificationBadge";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { ApplyButton } from "./ApplyButton";
import { getDrive } from "@/lib/api/drives";
import { getPrimaryStudent } from "@/lib/api/students";
import { getApplicationForDrive } from "@/lib/api/applications";
import { getDriveSkillFit } from "@/lib/api/skillgap";
import type { RoleSkillGap } from "@/lib/api/skillgap";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import { applicationStageIndex, applicationStatusLabel, driveStatusTone } from "@/lib/status";
import type { Application, Drive, Student } from "@/types";

export default function DriveDetailPage({ params }: { params: { id: string } }) {
  const {
    data: drive,
    loading: driveLoading,
    error: driveError,
    reload: reloadDrive,
  } = useApiResource<Drive | null>(() => getDrive(params.id).then((d) => d ?? null), [params.id], null);

  const {
    data: student,
    loading: studentLoading,
    error: studentError,
    reload: reloadStudent,
  } = useApiResource<Student | null>(() => getPrimaryStudent(), [], null);

  // The signed-in student's application to this drive (if any) drives the journey timeline.
  const { data: app, reload: reloadApp } = useApiResource<Application | null>(
    () =>
      student && drive
        ? getApplicationForDrive(student.id, drive.id).then((a) => a ?? null)
        : Promise.resolve<Application | null>(null),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [student?.id, drive?.id],
    null,
  );

  const loading = driveLoading || studentLoading;
  const error = driveError ?? studentError;
  const reload = () => {
    reloadDrive();
    reloadStudent();
    reloadApp();
  };

  return (
    <div className="mx-auto max-w-5xl">
      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!drive || !student}
        onRetry={reload}
        emptyTitle="Drive unavailable"
        emptyMessage="This drive could not be found, or you need to sign in as a student to view it."
      >
        {drive && student ? (
          <DriveDetail drive={drive} student={student} app={app} />
        ) : null}
      </AsyncBoundary>
    </div>
  );
}

function DriveDetail({
  drive,
  student,
  app,
}: {
  drive: Drive;
  student: Student;
  app: Application | null;
}) {
  const journeyIndex = app ? applicationStageIndex[app.status] : -1;
  const eligibility = evaluateEligibility(student, drive);

  // Role-fit is a SEPARATE, non-blocking signal from eligibility: it tells the student how their
  // skills compare to what this drive's role expects, but never gates applying.
  const { data: fit } = useApiResource<RoleSkillGap | null>(
    () => getDriveSkillFit(student.id, drive.id),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [student.id, drive.id],
    null,
  );

  return (
    <>
      <PageHeader
        eyebrow={`${drive.companyName} · ${drive.driveType}`}
        title={drive.role}
        subtitle={drive.description}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={driveStatusTone[drive.status]}>{drive.status}</Badge>
            <Badge variant={eligibility.eligible ? "success" : "risk"} className="gap-1">
              {eligibility.eligible ? (
                <ShieldCheck className="h-3 w-3" />
              ) : (
                <ShieldX className="h-3 w-3" />
              )}
              {eligibility.eligible ? "ELIGIBLE" : "NOT ELIGIBLE"}
            </Badge>
          </div>
        }
      />

      <DepthCard className="mb-5 p-5">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <CardTitle>Placement Journey</CardTitle>
          {app && <Badge variant="muted">{applicationStatusLabel[app.status]}</Badge>}
        </div>
        <JourneyTimeline currentIndex={journeyIndex} halted={app?.status === "rejected"} />
        <div className="mt-4 border-t border-border pt-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Link
              href={`/student/mock-interview?role=${encodeURIComponent(drive.role)}`}
              className="inline-flex items-center gap-1.5 text-sm font-medium text-violet-bright transition-colors hover:text-violet"
            >
              <Video className="h-4 w-4" />
              Practice a mock interview for this role
            </Link>
            {!app && (
              <ApplyButton
                driveId={drive.id}
                eligible={eligibility.eligible}
                alreadyApplied={false}
                driveOpen={drive.status === "ACTIVE"}
              />
            )}
          </div>
        </div>
      </DepthCard>

      <div className="grid gap-5 lg:grid-cols-2">
        <div className="space-y-5">
          <DepthCard className="p-5">
            <CardTitle className="mb-3">Drive Details</CardTitle>
            <div className="space-y-2 text-sm text-muted-foreground">
              <div className="flex items-center gap-2">
                <Building2 className="h-3.5 w-3.5" /> {drive.driveType}
                {drive.venue ? ` · ${drive.venue}` : ""}
              </div>
              <div className="flex items-center gap-2">
                <MapPin className="h-3.5 w-3.5" /> {drive.location}
              </div>
              <div className="flex items-center gap-2">
                <IndianRupee className="h-3.5 w-3.5" /> {drive.package}
              </div>
              <div className="flex items-center gap-2">
                <Users className="h-3.5 w-3.5" /> {drive.expectedHiring} expected hires
              </div>
              <div className="flex items-center gap-2">
                <Calendar className="h-3.5 w-3.5" /> Apply by {drive.deadline}
              </div>
            </div>
            <p className="mb-2 mt-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Required Skills
            </p>
            <div className="flex flex-wrap gap-1.5">
              {drive.requiredSkills.map((s) => (
                <Badge key={s} variant="muted">
                  {s}
                </Badge>
              ))}
            </div>
          </DepthCard>

          <DepthCard className="p-5">
            <div className="mb-1 flex items-center justify-between">
              <CardTitle>Eligibility Check</CardTitle>
            </div>
            <p className="mb-3 text-xs text-muted-foreground">
              Minimum CGPA {drive.criteria.minCgpa} · Branches{" "}
              {drive.criteria.allowedBranches.join(", ")} · Max {drive.criteria.maxBacklogs} active
              backlog{drive.criteria.maxBacklogs === 1 ? "" : "s"} · {drive.criteria.graduationYear}{" "}
              batch
            </p>
            <EligibilityChecklist student={student} drive={drive} />
          </DepthCard>

          <DepthCard className="p-5">
            <div className="mb-3 flex items-center gap-2">
              <ListChecks className="h-4 w-4 text-violet-bright" />
              <CardTitle>Selection Rounds</CardTitle>
            </div>
            <div className="space-y-2.5">
              {drive.rounds.map((r, i) => (
                <div key={r.name} className="flex gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-violet/15 text-[11px] font-semibold text-violet-bright">
                    {i + 1}
                  </span>
                  <div>
                    <p className="text-sm font-medium">{r.name}</p>
                    <p className="text-xs text-muted-foreground">{r.description}</p>
                  </div>
                </div>
              ))}
            </div>
          </DepthCard>
        </div>

        <DepthCard className="flex h-fit flex-col p-6">
          <div className="mb-1 flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-violet-bright" />
            <CardTitle>Your Skill Fit</CardTitle>
          </div>
          {fit && (fit.roleRequirementsAvailable || fit.driveSkillsFallback) ? (
            <>
              <p className="mb-4 text-xs text-muted-foreground">
                {fit.driveSkillsFallback
                  ? `No level requirements are published for ${drive.role} yet, so this fit compares your recorded skills to the skills this drive lists. This is guidance only — it does not affect your eligibility to apply.`
                  : `How your recorded skills compare to what ${drive.role} expects. This is guidance only — it does not affect your eligibility to apply.`}
              </p>
              <div className="mb-4 flex items-center gap-4">
                <ScoreRing
                  value={fit.overallMatch}
                  size={96}
                  strokeWidth={8}
                  label="Match"
                  colorClass={
                    fit.overallMatch >= 75
                      ? "stroke-success"
                      : fit.overallMatch >= 50
                        ? "stroke-violet"
                        : "stroke-warning"
                  }
                  animateKey={fit.overallMatch}
                />
                <div className="min-w-0 text-xs text-muted-foreground">
                  {fit.strengths.length > 0 && (
                    <p>
                      <span className="font-medium text-success">{fit.strengths.length}</span> skill
                      {fit.strengths.length === 1 ? "" : "s"}{" "}
                      {fit.driveSkillsFallback ? "you already have" : "at or above target"}
                    </p>
                  )}
                  {fit.gaps.length > 0 && (
                    <p className="mt-0.5">
                      <span className="font-medium text-warning">{fit.gaps.length}</span>{" "}
                      {fit.driveSkillsFallback
                        ? "not recorded yet"
                        : `skill gap${fit.gaps.length === 1 ? "" : "s"}`}
                    </p>
                  )}
                </div>
              </div>
              {fit.strengths.length > 0 && (
                <div className="mb-3 space-y-1.5">
                  {fit.strengths.slice(0, 4).map((s) => (
                    <div
                      key={s.skill}
                      className="flex items-center justify-between gap-2 rounded-lg border border-border/60 bg-elevated/40 px-2.5 py-1.5"
                    >
                      <span className="flex min-w-0 items-center gap-1.5 text-xs font-medium text-foreground">
                        <span className="truncate">{s.skill}</span>
                        <VerificationBadge status={s.verificationStatus} />
                      </span>
                      <span className="shrink-0 text-[11px] text-muted-foreground">
                        {fit.driveSkillsFallback ? `${s.currentLevel}%` : `${s.currentLevel}/${s.requiredLevel}`}
                      </span>
                    </div>
                  ))}
                  {fit.strengths.length > 4 && (
                    <p className="text-[11px] text-muted-foreground">
                      +{fit.strengths.length - 4} more strength{fit.strengths.length - 4 === 1 ? "" : "s"}
                    </p>
                  )}
                </div>
              )}
              {fit.gaps.length > 0 && (
                <div>
                  <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Skill gaps
                  </p>
                  <div className="space-y-2.5">
                    {fit.gaps.slice(0, 4).map((g) => (
                      <div key={g.skill}>
                        <SkillBar
                          label={
                            fit.driveSkillsFallback
                              ? `${g.skill} · Not recorded yet`
                              : g.currentLevel != null
                                ? `${g.skill} · ${g.currentLevel}/${g.targetLevel ?? "—"}`
                                : `${g.skill} · No Evidence (Req: ${g.targetLevel ?? "—"})`
                          }
                          value={g.currentLevel ?? 0}
                          colorClass={g.priority === "HIGH" ? "bg-warning" : "bg-violet"}
                        />
                        {g.recommendedAction && (
                          <p className="mt-0.5 text-[11px] leading-relaxed text-muted-foreground">
                            {g.recommendedAction}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                  {fit.gaps.length > 4 && (
                    <p className="mt-1.5 text-[11px] text-muted-foreground">
                      +{fit.gaps.length - 4} more gap{fit.gaps.length - 4 === 1 ? "" : "s"}
                    </p>
                  )}
                  <Link
                    href={`/student/skill-gap?drive=${encodeURIComponent(drive.id)}`}
                    className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-violet-bright transition-colors hover:text-violet"
                  >
                    View full skill gap analysis
                    <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              )}
            </>
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center py-6 text-center">
              <p className="text-sm font-medium">No skill fit for this role yet</p>
              <p className="mt-1 text-xs text-muted-foreground">
                Add skills in My Skills, or this role has no requirements defined yet.
              </p>
            </div>
          )}
        </DepthCard>
      </div>
    </>
  );
}
