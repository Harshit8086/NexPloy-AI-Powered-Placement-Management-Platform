"use client";

import { useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  ArrowLeft,
  ShieldAlert,
  HeartHandshake,
  CheckCircle2,
  Circle,
  TrendingUp,
  ClipboardList,
  Sparkles,
  CheckCheck,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Progress } from "@/components/ui/progress";
import { PageHeader } from "@/components/layout/PageHeader";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { IntelligencePulse } from "@/components/intelligence/IntelligencePulse";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { TrendAreaChart } from "@/components/charts/TrendAreaChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getStudent } from "@/lib/api/students";
import { getStudentRisk } from "@/lib/api/risk";
import { listApplicationsByStudent } from "@/lib/api/applications";
import { listInterviewsByStudent } from "@/lib/api/interviews";
import {
  activateInterventionPlan,
  completeInterventionPlan,
  listStudentInterventions,
  toggleInterventionAction,
} from "@/lib/api/mentors";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { riskTone } from "@/lib/status";
import type { Application, InterventionPlan, InterviewSlot, RiskEntry, Student } from "@/types";

interface InterventionBundle {
  student?: Student;
  risk?: RiskEntry;
  plan?: InterventionPlan;
  applications: Application[];
  interviews: InterviewSlot[];
}

export function InterventionDetail({ studentId }: { studentId: string }) {
  const pushToast = useAppStore((s) => s.pushToast);
  const [pulsing, setPulsing] = useState(false);
  const [busy, setBusy] = useState(false);

  const { data, loading, error, reload } = useApiResource<InterventionBundle>(
    async () => {
      const student = await getStudent(studentId);
      if (!student) {
        return { student: undefined, applications: [], interviews: [] };
      }
      const [risk, plans, applications, interviews] = await Promise.all([
        getStudentRisk(studentId),
        listStudentInterventions(studentId),
        listApplicationsByStudent(studentId),
        listInterviewsByStudent(studentId),
      ]);
      return {
        student,
        risk,
        // The mentor owns at most the most recent plan for this student; the backend returns
        // plans newest-first, so index 0 is the one to act on.
        plan: plans[0],
        applications: applications.filter((a) => a.status !== "eligible"),
        interviews,
      };
    },
    [studentId],
    { student: undefined, applications: [], interviews: [] },
  );

  const { student, risk, plan, applications, interviews } = data;

  const pulse = (ms: number) => {
    setPulsing(true);
    window.setTimeout(() => setPulsing(false), ms);
  };

  const activate = async () => {
    if (!plan || busy) return;
    setBusy(true);
    try {
      await activateInterventionPlan(plan.id);
      pulse(900);
      pushToast("Intervention activated", `${student?.name ?? "The student"}'s plan is now ACTIVE.`);
      reload();
    } catch (err) {
      pushToast("Couldn't activate", err instanceof Error ? err.message : "Please try again.");
    } finally {
      setBusy(false);
    }
  };

  const complete = async () => {
    if (!plan || busy) return;
    setBusy(true);
    try {
      await completeInterventionPlan(plan.id);
      pulse(900);
      pushToast("Intervention completed", `${student?.name ?? "The student"}'s plan is closed out.`);
      reload();
    } catch (err) {
      pushToast("Couldn't complete", err instanceof Error ? err.message : "Please try again.");
    } finally {
      setBusy(false);
    }
  };

  const markProgress = async (actionId: string, label: string, currentDone: boolean) => {
    if (!plan || busy) return;
    setBusy(true);
    try {
      await toggleInterventionAction(plan.id, actionId, !currentDone);
      pulse(700);
      pushToast("Progress updated", `"${label}" was updated for ${student?.name ?? "the student"}.`);
      reload();
    } catch (err) {
      pushToast("Couldn't update", err instanceof Error ? err.message : "Please try again.");
    } finally {
      setBusy(false);
    }
  };

  const trendData = (student?.readinessTrend ?? []).map((v, i) => ({ label: `W${i + 1}`, value: v }));

  return (
    <div className="mx-auto max-w-5xl">
      <Button asChild variant="ghost" size="sm" className="mb-3 -ml-2">
        <Link href="/mentor/students">
          <ArrowLeft className="h-3.5 w-3.5" /> Back to assigned students
        </Link>
      </Button>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!student}
        onRetry={reload}
        emptyTitle="Student not found"
        emptyMessage="This student isn't available, or isn't in your caseload."
      >
        {student && (
          <>
            <PageHeader
              eyebrow="Intervention"
              title={student.name}
              subtitle={`${student.rollNumber} · ${student.branch} · CGPA ${student.cgpa.toFixed(2)} · ${student.placementStatus}`}
              actions={
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant={riskTone[student.riskLevel]}>{student.riskLevel} RISK</Badge>
                  {plan?.status === "PROPOSED" && (
                    <Button variant="glow" onClick={activate} disabled={busy}>
                      <HeartHandshake className="h-4 w-4" /> Activate Plan
                    </Button>
                  )}
                  {plan?.status === "ACTIVE" && (
                    <Button variant="outline" onClick={complete} disabled={busy}>
                      <CheckCheck className="h-4 w-4" /> Mark Complete
                    </Button>
                  )}
                </div>
              }
            />

            <div className="grid gap-5 md:grid-cols-[auto_1fr]">
              <Card className="flex flex-col items-center justify-center gap-3 p-6">
                <Avatar className="h-12 w-12">
                  <AvatarFallback>{student.avatarInitials}</AvatarFallback>
                </Avatar>
                <ScoreRing value={student.readiness} size={140} strokeWidth={11} label="Readiness" />
                <div className="text-center text-xs text-muted-foreground">
                  Placement probability{" "}
                  <span className="font-semibold text-foreground">{student.placementProbability}%</span>
                </div>
              </Card>

              <Card className="p-5">
                <div className="mb-3 flex items-center gap-2">
                  <ShieldAlert className="h-4 w-4 text-risk" />
                  <CardTitle>Issues Identified</CardTitle>
                </div>
                {risk ? (
                  <div className="space-y-2">
                    {risk.factors.map((f) => (
                      <div key={f.label} className="rounded-lg border border-border p-2.5">
                        <p className="text-xs font-medium">{f.label}</p>
                        <p className="text-xs text-muted-foreground">{f.detail}</p>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">
                    No risk factors flagged for this student.
                  </p>
                )}
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <SkillBar label="Projects" value={student.breakdown.projects} />
                  <SkillBar label="Communication" value={student.breakdown.communication} />
                  <SkillBar label="Mock Interview" value={student.breakdown.interview} />
                </div>
              </Card>
            </div>

            <IntelligencePulse active={pulsing} className="mt-5">
              <Card className="p-5">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <HeartHandshake className="h-4 w-4 text-violet-bright" />
                    <CardTitle>Intervention Plan</CardTitle>
                  </div>
                  {plan && (
                    <Badge
                      variant={
                        plan.status === "ACTIVE"
                          ? "default"
                          : plan.status === "COMPLETED"
                            ? "success"
                            : "muted"
                      }
                    >
                      {plan.status}
                    </Badge>
                  )}
                </div>

                {!plan ? (
                  <div className="py-8 text-center">
                    <p className="mb-1 text-sm font-medium">No plan yet</p>
                    <p className="mx-auto max-w-sm text-xs text-muted-foreground">
                      The placement office proposes intervention plans and assigns them to you.
                      Once a plan is proposed for {student.name}, you can activate it and track
                      progress here.
                    </p>
                  </div>
                ) : (
                  <>
                    <div className="mb-4 flex flex-wrap gap-1.5">
                      {plan.focusAreas.map((f) => (
                        <Badge key={f} variant="warning">
                          {f}
                        </Badge>
                      ))}
                    </div>

                    <div className="mb-4 flex items-center gap-3">
                      <Progress value={plan.progress} className="flex-1" />
                      <motion.span
                        key={plan.progress}
                        initial={{ opacity: 0, y: -4 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="shrink-0 text-sm font-semibold tabular-nums text-violet-bright"
                      >
                        {plan.progress}%
                      </motion.span>
                    </div>

                    <div className="space-y-2">
                      {plan.actions.map((a) => (
                        <button
                          key={a.id}
                          onClick={() => markProgress(a.id, a.label, a.done)}
                          disabled={busy || plan.status !== "ACTIVE"}
                          className="flex w-full items-center gap-2.5 rounded-lg border border-border p-3 text-left transition-colors hover:bg-accent/40 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <AnimatePresence mode="wait" initial={false}>
                            <motion.span
                              key={a.done ? "done" : "todo"}
                              initial={{ opacity: 0, scale: 0.7 }}
                              animate={{ opacity: 1, scale: 1 }}
                              exit={{ opacity: 0, scale: 0.7 }}
                              transition={{ duration: 0.18 }}
                              className="shrink-0"
                            >
                              {a.done ? (
                                <CheckCircle2 className="h-4 w-4 text-success" />
                              ) : (
                                <Circle className="h-4 w-4 text-muted-foreground" />
                              )}
                            </motion.span>
                          </AnimatePresence>
                          <span className={a.done ? "text-sm text-muted-foreground line-through" : "text-sm"}>
                            {a.label}
                          </span>
                          <span className="ml-auto shrink-0 text-[10px] uppercase tracking-wider text-muted-foreground">
                            {plan.status === "ACTIVE" ? (a.done ? "Done" : "Mark progress") : a.done ? "Done" : "—"}
                          </span>
                        </button>
                      ))}
                    </div>

                    {plan.notes && (
                      <p className="mt-4 rounded-lg border border-border bg-elevated p-3 text-xs text-muted-foreground">
                        <span className="font-medium text-foreground">Notes: </span>
                        {plan.notes}
                      </p>
                    )}

                    {plan.progress === 100 && (
                      <motion.p
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="mt-3 flex items-center gap-1.5 text-sm font-medium text-success"
                      >
                        <Sparkles className="h-4 w-4" /> All actions complete.
                      </motion.p>
                    )}
                  </>
                )}
              </Card>
            </IntelligencePulse>

            <DataReveal stagger className="mt-5 grid gap-5 md:grid-cols-2">
              <DataRevealItem>
                <DepthCard className="h-full p-5">
                  <div className="mb-3 flex items-center gap-2">
                    <TrendingUp className="h-4 w-4 text-violet-bright" />
                    <CardTitle>Readiness Trend</CardTitle>
                  </div>
                  <TrendAreaChart data={trendData} dataKey="value" xKey="label" height={200} />
                </DepthCard>
              </DataRevealItem>
              <DataRevealItem>
                <DepthCard className="h-full p-5">
                  <div className="mb-3 flex items-center gap-2">
                    <ClipboardList className="h-4 w-4 text-violet-bright" />
                    <CardTitle>Placement Activity</CardTitle>
                  </div>
                  <div className="space-y-2 text-sm">
                    <p className="text-muted-foreground">
                      Applications:{" "}
                      <span className="font-medium text-foreground">{applications.length}</span>
                    </p>
                    <p className="text-muted-foreground">
                      Interview rounds:{" "}
                      <span className="font-medium text-foreground">{interviews.length}</span>
                    </p>
                    <p className="text-muted-foreground">
                      Rounds cleared:{" "}
                      <span className="font-medium text-foreground">
                        {interviews.filter((i) => i.result === "CLEARED").length}
                      </span>
                    </p>
                    <p className="text-muted-foreground">
                      Active backlogs:{" "}
                      <span
                        className={
                          student.backlogs > 0 ? "font-medium text-risk" : "font-medium text-success"
                        }
                      >
                        {student.backlogs}
                      </span>
                    </p>
                    <p className="text-muted-foreground">
                      Resume score:{" "}
                      <span className="font-medium text-foreground">{student.resumeScore}/100</span>
                    </p>
                  </div>
                  <p className="mb-2 mt-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Skill gaps
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {student.criticalGaps.map((g) => (
                      <Badge key={g} variant="risk">
                        {g}
                      </Badge>
                    ))}
                  </div>
                </DepthCard>
              </DataRevealItem>
            </DataReveal>
          </>
        )}
      </AsyncBoundary>
    </div>
  );
}
