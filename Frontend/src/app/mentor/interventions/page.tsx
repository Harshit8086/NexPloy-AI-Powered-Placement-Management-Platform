"use client";

import Link from "next/link";
import { useState } from "react";
import { motion } from "framer-motion";
import { HeartHandshake, ArrowUpRight, CheckCircle2, Circle, Play } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Progress } from "@/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getCurrentUser } from "@/lib/api/auth";
import {
  activateInterventionPlan,
  listMyAssignments,
  listStudentInterventions,
  toggleInterventionAction,
} from "@/lib/api/mentors";
import { getStudent } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import type { InterventionPlan, InterventionStatus, Student } from "@/types";

const statusTone: Record<InterventionStatus, "default" | "success" | "muted"> = {
  PROPOSED: "muted",
  ACTIVE: "default",
  COMPLETED: "success",
};

interface MentorPlans {
  plans: InterventionPlan[];
  students: Map<string, Student>;
}

async function loadPlans(): Promise<MentorPlans> {
  const [, assignments] = await Promise.all([getCurrentUser(), listMyAssignments()]);
  const studentIds = Array.from(new Set(assignments.map((a) => a.studentId)));
  const [studentList, planLists] = await Promise.all([
    Promise.all(studentIds.map((id) => getStudent(id))),
    Promise.all(studentIds.map((id) => listStudentInterventions(id))),
  ]);
  const students = new Map<string, Student>();
  studentList.forEach((s) => {
    if (s) students.set(s.id, s);
  });
  return { plans: planLists.flat(), students };
}

function PlanCard({
  plan,
  student,
  onChanged,
}: {
  plan: InterventionPlan;
  student?: Student;
  onChanged: () => void;
}) {
  const pushToast = useAppStore((s) => s.pushToast);
  const [busy, setBusy] = useState(false);

  const toggle = async (actionId: string, label: string, done: boolean) => {
    if (busy) return;
    setBusy(true);
    try {
      await toggleInterventionAction(plan.id, actionId, !done);
      pushToast("Progress updated", `"${label}" was updated for ${student?.name ?? "the student"}.`);
      onChanged();
    } catch (err) {
      pushToast("Couldn't update", err instanceof Error ? err.message : "Please try again.");
    } finally {
      setBusy(false);
    }
  };

  const activate = async () => {
    if (busy) return;
    setBusy(true);
    try {
      await activateInterventionPlan(plan.id);
      pushToast("Intervention activated", `${student?.name ?? "The student"}'s plan is now ACTIVE.`);
      onChanged();
    } catch (err) {
      pushToast("Couldn't activate", err instanceof Error ? err.message : "Please try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <DepthCard className="h-full p-5">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 items-center gap-3">
          <Avatar className="h-9 w-9">
            <AvatarFallback className="text-[11px]">{student?.avatarInitials ?? "?"}</AvatarFallback>
          </Avatar>
          <div className="min-w-0">
            <CardTitle className="truncate">{student?.name ?? plan.studentId}</CardTitle>
            <p className="truncate text-xs text-muted-foreground">
              Created {plan.createdOn}
              {student ? ` · ${student.branchCode}` : ""}
            </p>
          </div>
        </div>
        <Badge variant={statusTone[plan.status]}>{plan.status}</Badge>
      </div>

      <div className="mt-3 flex flex-wrap gap-1.5">
        {plan.focusAreas.map((f) => (
          <Badge key={f} variant="warning">
            {f}
          </Badge>
        ))}
      </div>

      <div className="mt-4 flex items-center gap-3">
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

      <div className="mt-3 space-y-1.5">
        {plan.actions.map((a) => (
          <button
            key={a.id}
            onClick={() => toggle(a.id, a.label, a.done)}
            disabled={busy || plan.status !== "ACTIVE"}
            className="flex w-full items-center gap-2 rounded-md px-1.5 py-1.5 text-left text-xs transition-colors hover:bg-accent/40 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {a.done ? (
              <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-success" />
            ) : (
              <Circle className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
            )}
            <span className={a.done ? "text-muted-foreground line-through" : undefined}>
              {a.label}
            </span>
          </button>
        ))}
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        {plan.status === "PROPOSED" && (
          <Button variant="glow" size="sm" onClick={activate} disabled={busy}>
            <Play className="h-3.5 w-3.5" /> Activate
          </Button>
        )}
        <Button asChild variant="outline" size="sm">
          <Link href={`/mentor/students/${plan.studentId}`}>
            Open case <ArrowUpRight className="h-3.5 w-3.5" />
          </Link>
        </Button>
      </div>
    </DepthCard>
  );
}

export default function MentorInterventionsPage() {
  const { data, loading, error, reload } = useApiResource(loadPlans, [], {
    plans: [],
    students: new Map<string, Student>(),
  });

  const mine = data.plans;
  const proposed = mine.filter((p) => p.status === "PROPOSED");
  const active = mine.filter((p) => p.status === "ACTIVE");
  const completed = mine.filter((p) => p.status === "COMPLETED");

  const renderGrid = (list: InterventionPlan[], emptyText: string) =>
    list.length === 0 ? (
      <Card className="p-10 text-center text-sm text-muted-foreground">{emptyText}</Card>
    ) : (
      <div className="grid gap-4 md:grid-cols-2">
        {list.map((p) => (
          <PlanCard key={p.id} plan={p} student={data.students.get(p.studentId)} onChanged={reload} />
        ))}
      </div>
    );

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Intervention"
        title="Intervention Plans"
        subtitle="Plans you own, plus drafts proposed by the placement office awaiting activation."
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={mine.length === 0}
        onRetry={reload}
        emptyTitle="No intervention plans"
        emptyMessage="The placement office proposes intervention plans for your mentees. Nothing has been assigned to you yet."
      >
        <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-3">
          <StatCard label="Proposed" value={proposed.length} accent="warning" />
          <StatCard label="Active" value={active.length} icon={HeartHandshake} accent="violet" />
          <StatCard label="Completed" value={completed.length} accent="success" />
        </div>

        <Tabs defaultValue="active">
          <TabsList>
            <TabsTrigger value="active">Active ({active.length})</TabsTrigger>
            <TabsTrigger value="proposed">Proposed ({proposed.length})</TabsTrigger>
            <TabsTrigger value="completed">Completed ({completed.length})</TabsTrigger>
          </TabsList>
          <TabsContent value="active">
            {renderGrid(active, "No active plans — activate a proposed plan to start tracking it.")}
          </TabsContent>
          <TabsContent value="proposed">
            {renderGrid(proposed, "Nothing awaiting activation right now.")}
          </TabsContent>
          <TabsContent value="completed">
            {renderGrid(completed, "No completed plans yet — tick every action on a plan to complete it.")}
          </TabsContent>
        </Tabs>
      </AsyncBoundary>
    </div>
  );
}
