"use client";

import { useEffect, useState } from "react";
import {
  Target,
  Sparkles,
  Building2,
  HelpCircle,
  ArrowRight,
  CheckCircle2,
} from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { GlowLine } from "@/components/intelligence/GlowLine";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { getPrimaryStudent } from "@/lib/api/students";
import { getRoleSkillGap, type RoleSkillGap } from "@/lib/api/skillgap";
import { listDrives } from "@/lib/api/drives";
import type { Drive, SkillGap } from "@/types";

const priorityTone = { HIGH: "risk", MEDIUM: "warning", LOW: "muted" } as const;

function GapRow({ gap, isPresenceOnly }: { gap: SkillGap; isPresenceOnly: boolean }) {
  const isNoEvidence = gap.evidenceStatus === "NO_EVIDENCE" || gap.currentLevel === null || gap.currentLevel === undefined;

  return (
    <div className="rounded-lg border border-border p-4 transition-colors">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold">{gap.skill}</span>
          {isNoEvidence && (
            <Badge variant="outline" className="border-warning/60 bg-warning/10 text-warning text-[10px] font-medium">
              NO EVIDENCE ON PROFILE
            </Badge>
          )}
        </div>
        <Badge
          variant={priorityTone[gap.priority]}
          className={
            gap.priority === "HIGH"
              ? "shadow-[0_0_0_1px_hsl(var(--risk)/0.3),0_0_16px_-4px_hsl(var(--risk)/0.6)]"
              : undefined
          }
        >
          {gap.priority} PRIORITY
        </Badge>
      </div>

      {isNoEvidence ? (
        <div className="mt-3 rounded-md bg-muted/40 p-2.5 text-xs text-muted-foreground">
          <div className="flex items-center gap-1.5 font-medium text-foreground">
            <HelpCircle className="h-3.5 w-3.5 text-warning" />
            <span>Unassessed / Unevidenced Skill</span>
          </div>
          <p className="mt-1">
            Requirement is unmet, but your actual proficiency is not assumed to be zero. No verification, project, or assessment record exists on your profile yet.
          </p>
          {gap.targetLevel && (
            <div className="mt-2 flex items-center gap-2 text-xs">
              <span className="text-muted-foreground">Target Level:</span>
              <span className="font-semibold text-violet-bright">{gap.targetLevel}% ({gap.targetLabel})</span>
            </div>
          )}
        </div>
      ) : isPresenceOnly ? (
        <div className="mt-2 text-xs text-muted-foreground">
          Listed as a required technical skill by the recruiter.
        </div>
      ) : (
        <>
          <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
            <span className="rounded-md bg-muted px-2 py-1">
              <span className="text-muted-foreground">Current: </span>
              <span className="font-medium">{gap.currentLabel}</span>
            </span>
            <ArrowRight className="h-3.5 w-3.5 text-muted-foreground" />
            <span className="rounded-md bg-violet/10 px-2 py-1 text-violet-bright">
              <span className="opacity-80">Required: </span>
              <span className="font-medium">{gap.targetLabel}</span>
            </span>
            {gap.targetLevel !== null && gap.currentLevel !== null && (
              <span className="text-muted-foreground">
                Gap: <span className="font-medium text-foreground">{(gap.targetLevel ?? 0) - (gap.currentLevel ?? 0)}</span> points
              </span>
            )}
          </div>

          <div className="mt-3 grid items-center gap-3 sm:grid-cols-[1fr_auto_1fr]">
            <SkillBar label="Current Level" value={gap.currentLevel ?? 0} colorClass="bg-muted-foreground/60" />
            <GlowLine
              orientation="vertical"
              tone={gap.priority === "HIGH" ? "risk" : "warning"}
              className="hidden h-10 sm:block"
            />
            <SkillBar label="Required Level" value={gap.targetLevel ?? 0} colorClass="bg-violet" />
          </div>
        </>
      )}

      {gap.whyItMatters && (
        <p className="mt-3 text-xs text-muted-foreground">
          <span className="font-medium text-foreground">Why it matters: </span>
          {gap.whyItMatters}
        </p>
      )}

      {gap.recommendedAction && (
        <p className="mt-1.5 text-xs text-violet-bright">
          <span className="font-medium">Recommended action: </span>
          {gap.recommendedAction}
        </p>
      )}
    </div>
  );
}

function matchColorClass(match: number): string {
  if (match >= 75) return "stroke-success";
  if (match >= 50) return "stroke-violet";
  return "stroke-warning";
}

export default function SkillGapPage() {
  const [drives, setDrives] = useState<Drive[]>([]);
  const [selectedDriveId, setSelectedDriveId] = useState<string>("");
  const [studentId, setStudentId] = useState<string>("");

  const [data, setData] = useState<RoleSkillGap | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Initial load: student & drives
  useEffect(() => {
    let active = true;
    async function init() {
      try {
        setLoading(true);
        setError(null);
        const stu = await getPrimaryStudent();
        if (!active) return;
        setStudentId(stu.id);

        const allDrives = await listDrives();
        if (!active) return;
        // Only ACTIVE drives are relevant for skill-gap analysis — unless one was explicitly
        // requested (e.g. "View full skill gap analysis" from a drive's detail page), which
        // always wins and is listed first even if it is no longer active.
        const requestedId = new URLSearchParams(window.location.search).get("drive");
        const requested = requestedId ? allDrives.find((d) => d.id === requestedId) : undefined;
        const activeDrives = allDrives.filter((d) => d.status === "ACTIVE");
        const visibleDrives = requested
          ? [requested, ...activeDrives.filter((d) => d.id !== requested.id)]
          : activeDrives;
        setDrives(visibleDrives);

        const driveIdToSelect = visibleDrives[0]?.id || "";
        setSelectedDriveId(driveIdToSelect);

        const res = await getRoleSkillGap(stu.id, driveIdToSelect || undefined);
        if (!active) return;
        setData(res);
      } catch (err) {
        if (!active) return;
        setError(err instanceof Error ? err.message : "Failed to load skill gap");
      } finally {
        if (active) setLoading(false);
      }
    }
    init();
    return () => {
      active = false;
    };
  }, []);

  // When drive selection changes
  const handleDriveChange = async (newDriveId: string) => {
    setSelectedDriveId(newDriveId);
    if (!studentId) return;
    try {
      setLoading(true);
      setError(null);
      const res = await getRoleSkillGap(studentId, newDriveId || undefined);
      setData(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load skill gap");
    } finally {
      setLoading(false);
    }
  };

  const isPresenceOnly = data?.analysisType === "PRESENCE_ONLY" || data?.driveSkillsFallback === true;
  const highPriority = (data?.gaps ?? []).filter((g) => g.priority === "HIGH");
  const rest = (data?.gaps ?? []).filter((g) => g.priority !== "HIGH");
  const strengths = data?.strengths ?? [];
  const selectedDrive = drives.find((d) => d.id === selectedDriveId);

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Intelligence"
        title="Job & Drive Skill Gap Analysis"
        subtitle="Evaluated strictly against real company requirements extracted from job descriptions."
      />

      {/* Drive Selector Bar */}
      <DepthCard className="mb-6 p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <Building2 className="h-5 w-5 text-violet-bright" />
            <div>
              <p className="text-sm font-semibold text-foreground">Select Target Campus Drive</p>
              <p className="text-xs text-muted-foreground">
                Skill Gaps are specific to each company and role. Compare against different hiring drives.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <select
              value={selectedDriveId}
              onChange={(e) => handleDriveChange(e.target.value)}
              className="flex h-9 w-full min-w-0 rounded-md border border-border bg-background px-3 py-1 text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-violet sm:w-auto"
            >
              {drives.length === 0 ? (
                <option value="">No drives available</option>
              ) : (
                drives.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.companyName} — {d.role}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>
      </DepthCard>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={false}
        onRetry={() => handleDriveChange(selectedDriveId)}
      >
        {/* State: No drives exist */}
        {drives.length === 0 && (
          <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border p-10 text-center">
            <Target className="mb-3 h-8 w-8 text-muted-foreground" />
            <p className="text-sm font-semibold">No Campus Drives Open</p>
            <p className="mt-1 max-w-md text-xs text-muted-foreground">
              When placement officers and recruiters announce new hiring drives, your skill gap against each specific job description will appear here.
            </p>
          </div>
        )}

        {/* State: Drive selected, analysis available */}
        {data && drives.length > 0 && (
          <>
            {/* Analysis Header Card */}
            <DepthCard className="mb-5 p-5">
              <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-center">
                <ScoreRing
                  value={data.overallMatch}
                  size={128}
                  strokeWidth={10}
                  label={isPresenceOnly ? "Coverage" : "Fit Score"}
                  sublabel={selectedDrive ? selectedDrive.companyName : "Match"}
                  colorClass={matchColorClass(data.overallMatch)}
                  animateKey={data.overallMatch}
                />
                <div className="min-w-0 flex-1">
                  <div className="mb-1 flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-violet-bright" />
                    <CardTitle>
                      {selectedDrive?.companyName} — {selectedDrive?.role || data.targetRole}
                    </CardTitle>
                    {isPresenceOnly ? (
                      <Badge variant="outline" className="text-xs border-muted-foreground/30 text-muted-foreground">
                        Presence-Only Matching
                      </Badge>
                    ) : (
                      <Badge variant="outline" className="text-xs border-violet/40 text-violet-bright">
                        Level-Based Analysis
                      </Badge>
                    )}
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {isPresenceOnly
                      ? "This drive lists required skills without numeric target levels. Matching reflects presence of skill evidence across your profile."
                      : `A weighted comparison of your verified evidence against the approved requirements for ${selectedDrive?.companyName || "this drive"}. Unassessed skills are not assumed to be zero.`}
                  </p>

                  <div className="mt-3 flex flex-wrap gap-2 text-xs">
                    <span className="rounded bg-muted px-2 py-0.5 text-muted-foreground">
                      Package: <span className="font-semibold text-foreground">{selectedDrive?.package || "Not specified"}</span>
                    </span>
                    <span className="rounded bg-muted px-2 py-0.5 text-muted-foreground">
                      Type: <span className="font-semibold text-foreground">{selectedDrive?.driveType || "On-Campus"}</span>
                    </span>
                    <span className="rounded bg-muted px-2 py-0.5 text-muted-foreground">
                      Covered: <span className="font-semibold text-foreground">{strengths.length} of {strengths.length + data.gaps.length} skills</span>
                    </span>
                  </div>
                </div>
              </div>
            </DepthCard>

            {/* Strengths Card */}
            <DepthCard className="mb-5 p-5">
              <div className="mb-2 flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-success" />
                <CardTitle>Demonstrated Strengths</CardTitle>
              </div>
              {strengths.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {strengths.map((s) => (
                    <Badge key={s.skill} variant="success">
                      {s.skill}
                      {s.requiredLevel > 0 && ` · ${s.currentLevel}% / ${s.requiredLevel}%`}
                      {s.verificationStatus && ` (${s.verificationStatus})`}
                    </Badge>
                  ))}
                </div>
              ) : (
                <span className="text-sm text-muted-foreground">
                  No verified skills currently meet this drive&apos;s bar — closing the requirements below will increase your fit score.
                </span>
              )}
            </DepthCard>

            {/* Gaps List Card */}
            <DepthCard className="p-5">
              <div className="mb-1 flex items-center gap-2">
                <Target className="h-4 w-4 text-violet-bright" />
                <CardTitle>Skill Requirements & Gap Roadmap</CardTitle>
              </div>
              <p className="mb-4 text-xs text-muted-foreground">
                Ranked by importance and deficit. Skills marked <span className="font-semibold text-warning">NO EVIDENCE</span> indicate missing portfolio evidence or unassessed areas.
              </p>

              {data.gaps.length === 0 ? (
                <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border p-8 text-center">
                  <CheckCircle2 className="mb-2 h-7 w-7 text-success" />
                  <p className="text-sm font-semibold">You meet every requirement for this drive</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Your profile is strongly aligned with {selectedDrive?.companyName || "the hiring requirements"}.
                  </p>
                </div>
              ) : (
                <DataReveal stagger className="space-y-4">
                  {highPriority.map((g) => (
                    <DataRevealItem key={g.skill}>
                      <GapRow gap={g} isPresenceOnly={isPresenceOnly} />
                    </DataRevealItem>
                  ))}
                  {rest.map((g) => (
                    <DataRevealItem key={g.skill}>
                      <GapRow gap={g} isPresenceOnly={isPresenceOnly} />
                    </DataRevealItem>
                  ))}
                </DataReveal>
              )}
            </DepthCard>
          </>
        )}
      </AsyncBoundary>
    </div>
  );
}
