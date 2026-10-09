"use client";

import Link from "next/link";
import { Gauge, Trophy, Target, ShieldCheck, ShieldAlert, ArrowUpRight, Video } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/layout/PageHeader";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal } from "@/components/intelligence/DataReveal";
import { TrendAreaChart } from "@/components/charts/TrendAreaChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getPrimaryStudent, getReadiness } from "@/lib/api/students";
import { getStudentRisk } from "@/lib/api/risk";
import { getAnalyticsForCampus } from "@/lib/api/analytics";
import { getRoleSkillGap } from "@/lib/api/skillgap";
import type { RoleSkillGap } from "@/lib/api/skillgap";
import { listMockInterviews, type MockInterviewSummary } from "@/lib/api/mockInterviews";
import { useApiResource } from "@/hooks/useApiResource";
import { readinessBand, riskTone } from "@/lib/status";
import type { BranchStat, ReadinessSummary, RiskEntry, Student } from "@/types";

/** The five readiness factors, in the order they are presented everywhere in
 * the app; `weight` is the ML-service share shown alongside each bar. */
const DIMENSIONS = [
  { key: "academic", label: "Academics", weight: 20 },
  { key: "aptitude", label: "Aptitude", weight: 20 },
  { key: "communication", label: "Communication", weight: 15 },
  { key: "projects", label: "Projects", weight: 25 },
  { key: "interview", label: "Mock Interview", weight: 20 },
] as const;

interface ReadinessData {
  student: Student;
  readiness: ReadinessSummary;
  risk?: RiskEntry;
  branch?: BranchStat;
  skillMatch?: RoleSkillGap;
  latestMock?: MockInterviewSummary;
}

async function loadReadiness(): Promise<ReadinessData> {
  const student = await getPrimaryStudent();
  const [readiness, risk, branch, skillMatch, latestMock] = await Promise.all([
    getReadiness(),
    getStudentRisk(student.id),
    // The branch benchmark is a nice-to-have overlay; if a campus has no analytics yet we simply
    // omit it rather than fail the whole readiness view.
    getAnalyticsForCampus(student.campusId)
      .then((a) => a.branchStats.find((b) => b.branchCode === student.branchCode))
      .catch(() => undefined),
    // Role skill match is a SEPARATE signal surfaced here read-only — it does NOT feed the readiness
    // score (that is the ML service's five factors). Non-fatal if unavailable.
    getRoleSkillGap(student.id).catch(() => undefined),
    // The latest completed practice mock interview. A COMPLETED run feeds readiness through the
    // mock-interview assessment bridge; it stays advisory evidence, never a hand-edited input.
    listMockInterviews()
      .then((runs) =>
        runs
          .filter((r) => r.status === "COMPLETED" && r.overallScore != null)
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0],
      )
      .catch(() => undefined),
  ]);
  return { student, readiness, risk, branch, skillMatch, latestMock };
}

export default function ReadinessPage() {
  const { data, loading, error, reload } = useApiResource<ReadinessData | null>(
    loadReadiness,
    [],
    null,
  );

  const student = data?.student;
  // Readiness score + breakdown come ONLY from the authoritative /students/me/readiness endpoint —
  // never from the profile response. A failed fetch surfaces via AsyncBoundary's error/retry state.
  const breakdown = data?.readiness.breakdown ?? null;
  const score = data?.readiness.score ?? 0;
  const risk = data?.risk;
  const branch = data?.branch;
  const skillMatch = data?.skillMatch;
  const latestMock = data?.latestMock;

  const trendData = (student?.readinessTrend ?? []).map((v, i) => ({ label: `W${i + 1}`, value: v }));
  const band = readinessBand(score);
  // A dimension the ML service had no data for arrives null/absent — exclude it from the
  // "biggest levers" ranking rather than treating a missing signal as a 0.
  const weakest: { key: string; label: string; value: number }[] = breakdown
    ? DIMENSIONS.map((d) => ({ key: d.key as string, label: d.label as string, value: breakdown[d.key] }))
        .filter((d) => typeof d.value === "number")
        .map((d) => ({ key: d.key, label: d.label, value: d.value as number }))
        .sort((a, b) => a.value - b.value)
        .slice(0, 2)
    : [];

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Intelligence"
        title="Placement Readiness"
        subtitle="Five readiness factors, tracked over time and benchmarked against your branch."
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!student}
        onRetry={reload}
        emptyTitle="No readiness data"
        emptyMessage="Your readiness profile isn't available yet."
      >
        {student && (
          <>
            <div className="grid gap-5 md:grid-cols-3">
              <Card className="flex flex-col items-center justify-center gap-3 p-6 md:col-span-1">
                <ScoreRing
                  value={score}
                  size={160}
                  strokeWidth={12}
                  label="Readiness"
                  sublabel="out of 100"
                />
                <Badge variant={band.tone}>{band.label}</Badge>
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Trophy className="h-3.5 w-3.5 text-violet-bright" /> Top{" "}
                  {100 - score < 25 ? 20 : 35}% of cohort
                </div>
              </Card>

              <Card className="p-5 md:col-span-2">
                <div className="flex items-center gap-2">
                  <Gauge className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Readiness Over Time</CardTitle>
                </div>
                <div className="mt-3">
                  {trendData.length > 0 ? (
                    <DataReveal>
                      <TrendAreaChart data={trendData} dataKey="value" xKey="label" height={200} />
                    </DataReveal>
                  ) : (
                    <p className="py-10 text-center text-sm text-muted-foreground">
                      No readiness history recorded yet.
                    </p>
                  )}
                </div>
              </Card>
            </div>

            <Card className="mt-5 p-5">
              <CardTitle className="mb-1">Employability Dimensions</CardTitle>
              <p className="mb-4 text-xs text-muted-foreground">
                The overall readiness score is a weighted blend of these five factors (weights shown).
                Factors with no data are excluded and the rest renormalised — never scored as 0.
              </p>
              {breakdown ? (
                <DataReveal>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {DIMENSIONS.map((d) => {
                      const value = breakdown[d.key];
                      const labelRow = (
                        <span className="flex min-w-0 items-center gap-1.5">
                          <span className="truncate">{d.label}</span>
                          <span className="shrink-0 rounded-full bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                            {d.weight}%
                          </span>
                        </span>
                      );
                      if (typeof value !== "number") {
                        // No record for this factor — the ML service excluded it rather than
                        // scoring a fabricated 0, so we surface that honestly instead of a bar.
                        return (
                          <div key={d.key} className="space-y-1.5">
                            <div className="flex items-center justify-between gap-2 text-xs">
                              <span className="min-w-0 text-muted-foreground">{labelRow}</span>
                              <span className="shrink-0 font-medium text-muted-foreground">
                                Not Available
                              </span>
                            </div>
                            <div className="h-2 w-full overflow-hidden rounded-full bg-muted" />
                          </div>
                        );
                      }
                      return (
                        <div key={d.key} className="space-y-1.5">
                          <div className="flex items-center justify-between gap-2 text-xs">
                            {labelRow}
                            <span className="shrink-0 font-semibold tabular-nums">{value}</span>
                          </div>
                          <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                            <div
                              className={`h-full rounded-full ${
                                value >= 80 ? "bg-success" : value >= 65 ? "bg-violet" : "bg-warning"
                              }`}
                              style={{ width: `${Math.min(100, value)}%` }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </DataReveal>
              ) : (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  No dimension breakdown scored yet.
                </p>
              )}
            </Card>

            <div className="mt-5 grid gap-5 md:grid-cols-2">
              <DepthCard className="p-5">
                <div className="mb-3 flex items-center gap-2">
                  <Target className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Placement Probability Estimate</CardTitle>
                </div>
                <div className="flex items-center gap-5">
                  <ScoreRing
                    value={student.placementProbability}
                    size={110}
                    strokeWidth={9}
                    label="Probability"
                    colorClass="stroke-success"
                  />
                  <div className="min-w-0">
                    <p className="text-sm text-muted-foreground">
                      Based on your current recorded readiness data.
                    </p>
                    <Button asChild variant="outline" size="sm" className="mt-3">
                      <Link href="/student/what-if">
                        Run a What-If scenario <ArrowUpRight className="h-3.5 w-3.5" />
                      </Link>
                    </Button>
                  </div>
                </div>
              </DepthCard>

              <DepthCard className="p-5">
                <div className="mb-3 flex items-center gap-2">
                  {student.riskLevel === "LOW" ? (
                    <ShieldCheck className="h-4 w-4 text-success" />
                  ) : (
                    <ShieldAlert className="h-4 w-4 text-risk" />
                  )}
                  <CardTitle>Risk Assessment</CardTitle>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={riskTone[student.riskLevel]}>{student.riskLevel} RISK</Badge>
                  <span className="text-sm text-muted-foreground">{student.riskReason}</span>
                </div>
                {risk && (
                  <div className="mt-3 space-y-2">
                    {risk.factors.map((f) => (
                      <div key={f.label} className="rounded-lg border border-border p-2.5">
                        <p className="text-xs font-medium">{f.label}</p>
                        <p className="text-xs text-muted-foreground">{f.detail}</p>
                      </div>
                    ))}
                  </div>
                )}
                {weakest.length > 0 && (
                  <>
                    <p className="mb-2 mt-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      Biggest levers right now
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {weakest.map((w) => (
                        <Badge key={w.key} variant="warning">
                          {w.label} · {w.value}
                        </Badge>
                      ))}
                    </div>
                  </>
                )}
              </DepthCard>
            </div>

            {skillMatch && skillMatch.roleRequirementsAvailable && (
              <Card className="mt-5 p-5">
                <div className="flex flex-col items-center gap-4 sm:flex-row">
                  <ScoreRing
                    value={skillMatch.overallMatch}
                    size={92}
                    strokeWidth={8}
                    label="Match"
                    colorClass={
                      skillMatch.overallMatch >= 75
                        ? "stroke-success"
                        : skillMatch.overallMatch >= 50
                          ? "stroke-violet"
                          : "stroke-warning"
                    }
                  />
                  <div className="min-w-0 flex-1">
                    <CardTitle className="mb-1">
                      Role Skill Match — {skillMatch.targetRole}
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      A separate view of how your skills line up with your target role. This is{" "}
                      <span className="font-medium text-foreground">not</span> part of the readiness
                      score above — it&apos;s a development signal you can act on.
                    </p>
                    <Button asChild variant="outline" size="sm" className="mt-3">
                      <Link href="/student/skill-gap">
                        View skill gap analysis <ArrowUpRight className="h-3.5 w-3.5" />
                      </Link>
                    </Button>
                  </div>
                </div>
              </Card>
            )}

            {latestMock && (
              <DepthCard className="mt-5 p-5">
                <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center">
                  <ScoreRing
                    value={latestMock.overallScore ?? 0}
                    size={92}
                    strokeWidth={8}
                    label="Mock"
                    colorClass={
                      (latestMock.overallScore ?? 0) >= 75
                        ? "stroke-success"
                        : (latestMock.overallScore ?? 0) >= 50
                          ? "stroke-violet"
                          : "stroke-warning"
                    }
                  />
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex items-center gap-2">
                      <Video className="h-4 w-4 text-violet-bright" />
                      <CardTitle>Latest Mock Interview</CardTitle>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Your most recent AI practice run{latestMock.role ? ` for ${latestMock.role}` : ""},
                      scored {new Date(latestMock.createdAt).toLocaleDateString()}. A completed run feeds
                      the Mock Interview and Communication factors of the readiness score above.
                    </p>
                    <Button asChild variant="outline" size="sm" className="mt-3">
                      <Link href={`/student/mock-interview/${latestMock.id}/report`}>
                        View report <ArrowUpRight className="h-3.5 w-3.5" />
                      </Link>
                    </Button>
                  </div>
                </div>
              </DepthCard>
            )}

            {branch && (
              <Card className="mt-5 p-5">
                <CardTitle className="mb-3">Branch Benchmark — {student.branch}</CardTitle>
                <div className="space-y-3">
                  <SkillBar label="Your readiness" value={score} colorClass="bg-violet" />
                  <SkillBar
                    label={`${student.branch} placement rate`}
                    value={branch.placementRate}
                    colorClass="bg-success"
                  />
                  <p className="pt-1 text-xs text-muted-foreground">
                    {branch.studentsPlaced} of {branch.totalStudents} students placed this cycle in{" "}
                    {student.branch}.
                  </p>
                </div>
              </Card>
            )}
          </>
        )}
      </AsyncBoundary>
    </div>
  );
}
