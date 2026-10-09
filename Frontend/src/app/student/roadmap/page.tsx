"use client";

import { useCallback, useMemo, useState } from "react";
import { motion } from "framer-motion";
import { Map, Check, Plus, Loader2, Sparkles, RefreshCw } from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
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
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { GlowLine } from "@/components/intelligence/GlowLine";
import { TutorialVideoLinks } from "@/components/intelligence/TutorialVideoLinks";
import { staggerContainer, staggerItem } from "@/lib/motion-variants";
import { cn } from "@/lib/utils";
import { useAppStore } from "@/hooks/useAppStore";
import Link from "next/link";
import {
  getRoadmap,
  toggleRoadmapTask,
  addRoadmapProject,
  regenerateRoadmap,
  upsertSkill,
  getLearningRecommendations,
  getMyGoal,
  type Roadmap,
  type RoadmapPhase,
  type RoadmapTask,
  type RoadmapRecommendedProject,
  type StudentGoal,
  type TutorialVideo,
} from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";

const phaseTone = { DONE: "success", CURRENT: "default", UPCOMING: "muted" } as const;

/** Proficiency recorded for a skill the student demonstrated by completing a roadmap project. */
const ROADMAP_SKILL_PROFICIENCY = 60;

export default function RoadmapPage() {
  const pushToast = useAppStore((st) => st.pushToast);
  const { data, loading, error, reload } = useApiResource<Roadmap | null>(
    () => getRoadmap(),
    [],
    null,
  );

  const { data: goal } = useApiResource<StudentGoal | null>(
    () => getMyGoal(),
    [],
    null,
  );

  // YouTube enrichment for the roadmap: the Learning Recommendations endpoint already returns real
  // tutorial videos per still-pending gap. Surface them here so the roadmap points directly at
  // material to learn the weak areas. Non-fatal — no gaps / no YouTube key simply hides the section.
  const { data: recs } = useApiResource(
    () => getLearningRecommendations().catch(() => []),
    [],
    [],
  );
  const tutorialVideos = useMemo<TutorialVideo[]>(() => {
    const seen = new Set<string>();
    const out: TutorialVideo[] = [];
    for (const rec of recs ?? []) {
      for (const v of rec.tutorialVideos ?? []) {
        if (!seen.has(v.url)) {
          seen.add(v.url);
          out.push(v);
        }
      }
    }
    return out.slice(0, 8);
  }, [recs]);

  // Ids of tasks/projects with an in-flight mutation, so we can disable just those controls.
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const setBusyFor = useCallback((id: string, on: boolean) => {
    setBusy((prev) => ({ ...prev, [id]: on }));
  }, []);

  // The roadmap loop: after a PROJECT task with a demonstrated skill is completed, offer to record
  // that skill in My Skills (student-confirmed, source ROADMAP) so it feeds skill-gap analysis.
  const [skillOffer, setSkillOffer] = useState<{ skill: string; projectTitle: string } | null>(null);
  const [savingSkill, setSavingSkill] = useState(false);
  const [regenerating, setRegenerating] = useState(false);

  // Rebuild from the current catalog + latest demonstrated skills, preserving completed tasks and
  // projects — so a newly-recorded skill flips its pending gaps to done.
  const onRegenerate = useCallback(async () => {
    setRegenerating(true);
    try {
      await regenerateRoadmap();
      await reload();
      pushToast("Roadmap synced", "Rebuilt from your latest skills — completed work and projects were kept.");
    } catch (e) {
      pushToast("Couldn't sync roadmap", e instanceof Error ? e.message : "Please try again.");
    } finally {
      setRegenerating(false);
    }
  }, [pushToast, reload]);

  const onToggleTask = useCallback(
    async (task: RoadmapTask, next: "PLANNED" | "DONE") => {
      setBusyFor(task.id, true);
      try {
        await toggleRoadmapTask(task.id, next);
        await reload();
        // Completing a portfolio project you built demonstrates its skill — offer to record it.
        if (next === "DONE" && task.kind === "PROJECT" && task.skill) {
          setSkillOffer({ skill: task.skill, projectTitle: task.title });
        }
      } catch (e) {
        pushToast("Couldn't update task", e instanceof Error ? e.message : "Please try again.");
      } finally {
        setBusyFor(task.id, false);
      }
    },
    [pushToast, reload, setBusyFor],
  );

  const onConfirmAddSkill = useCallback(async () => {
    if (!skillOffer) return;
    setSavingSkill(true);
    try {
      await upsertSkill({ skill: skillOffer.skill, proficiency: ROADMAP_SKILL_PROFICIENCY, source: "ROADMAP" });
      // Rebuild so the newly-demonstrated skill flips any matching catalog gaps to done.
      await regenerateRoadmap();
      await reload();
      pushToast(
        "Recorded in My Skills",
        `${skillOffer.skill} added at ${ROADMAP_SKILL_PROFICIENCY}% — it now counts toward your skill-gap analysis and your roadmap has been updated. Adjust it any time in My Skills.`,
      );
      setSkillOffer(null);
    } catch (e) {
      pushToast("Couldn't record skill", e instanceof Error ? e.message : "Please try again.");
    } finally {
      setSavingSkill(false);
    }
  }, [skillOffer, pushToast, reload]);

  const onAddProject = useCallback(
    async (rec: RoadmapRecommendedProject) => {
      setBusyFor(rec.id, true);
      try {
        await addRoadmapProject({
          name: rec.name,
          description: rec.description,
          addressesGap: rec.addressesGap,
          skillsDemonstrated: rec.skillsDemonstrated,
          difficulty: rec.difficulty,
        });
        pushToast("Added to roadmap", `"${rec.name}" is now in your Portfolio Projects.`);
        await reload();
      } catch (e) {
        pushToast("Couldn't add project", e instanceof Error ? e.message : "Please try again.");
      } finally {
        setBusyFor(rec.id, false);
      }
    },
    [pushToast, reload, setBusyFor],
  );

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Growth"
        title="Career Roadmap"
        subtitle="A sequenced plan to close your skill gaps — structure from a curated catalog, progress from what you've actually completed."
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!data || data.phases.length === 0}
        onRetry={reload}
        emptyTitle="No roadmap yet"
        emptyMessage="Set a target role on your profile and your gap-driven roadmap will appear here."
      >
        {data && (
          <>
            <div className="mb-3 flex justify-end">
              <Button variant="outline" size="sm" onClick={onRegenerate} disabled={regenerating}>
                {regenerating ? (
                  <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                ) : (
                  <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                )}
                Sync with my latest skills
              </Button>
            </div>

            {goal && goal.factors && goal.factors.length > 0 && (
              <div className="mb-4 rounded-xl border border-violet/40 bg-violet/5 p-4 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-violet-bright" />
                    <span className="text-sm font-semibold text-foreground">
                      Active Sprint Focus · Committed via What-If Simulator
                    </span>
                    {goal.projectedProbability !== null && (
                      <Badge variant="outline" className="border-violet/40 bg-violet/10 text-violet-bright text-[10px] font-semibold">
                        Target: {goal.projectedProbability}% Placement Probability
                      </Badge>
                    )}
                  </div>
                  <Link
                    href="/student/what-if"
                    className="text-xs font-medium text-violet-bright hover:underline flex items-center gap-1"
                  >
                    Adjust in Simulator →
                  </Link>
                </div>
                <div className="mt-2.5 flex flex-wrap gap-1.5">
                  {goal.factors.map((f) => (
                    <Badge key={f} variant="outline" className="border-border bg-card text-xs font-normal">
                      {f}
                    </Badge>
                  ))}
                </div>
              </div>
            )}

            <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <StatCard label="Completed" value={data.completedTaskCount} accent="success" />
              <StatCard label="Total Tasks" value={data.taskCount} accent="violet" />
              <StatCard label="Overall Progress" value={data.overallProgress} suffix="%" accent="violet" />
              <StatCard label="Projects" value={data.projectCount} />
            </div>

            <DepthCard className="mb-6 p-5">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <CardTitle>Current Focus</CardTitle>
                <Badge variant="default">{data.targetRole ?? "General"}</Badge>
              </div>
              {data.currentFocus ? (
                <>
                  <p className="text-sm font-medium">{data.currentFocus.title}</p>
                  {data.currentFocus.rationale && (
                    <p className="mt-1 text-xs text-muted-foreground">{data.currentFocus.rationale}</p>
                  )}
                  <Progress value={data.overallProgress} className="mt-4" />
                  <p className="mt-2 text-xs text-muted-foreground">
                    {data.currentFocus.doneTasks} of {data.currentFocus.totalTasks} tasks done in this phase ·{" "}
                    {data.completedTaskCount} of {data.taskCount} overall.
                  </p>
                </>
              ) : (
                <p className="flex items-center gap-2 text-sm text-success">
                  <Check className="h-4 w-4" /> Every phase complete — nice work.
                </p>
              )}
            </DepthCard>

            <motion.div initial="hidden" animate="visible" variants={staggerContainer} className="relative pl-8">
              <div className="absolute bottom-4 left-[15px] top-2 w-px overflow-hidden rounded-full">
                <GlowLine orientation="vertical" className="h-full" />
              </div>
              {data.phases.map((phase, i) => (
                <PhaseCard
                  key={phase.key}
                  phase={phase}
                  index={i}
                  busy={busy}
                  onToggleTask={onToggleTask}
                />
              ))}
            </motion.div>

            {data.recommendedProjects.length > 0 && (
              <DepthCard className="mt-6 p-5">
                <div className="mb-3 flex items-center gap-2">
                  <Sparkles className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Recommended Projects</CardTitle>
                </div>
                <p className="mb-3 text-xs text-muted-foreground">
                  Each project targets a skill still pending on your roadmap. Adding one creates a tracked
                  Portfolio Project task.
                </p>
                <div className="space-y-3">
                  {data.recommendedProjects.map((rec) => (
                    <div key={rec.id} className="rounded-lg border border-border p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="text-sm font-medium text-foreground">{rec.name}</p>
                          <p className="mt-0.5 text-sm text-muted-foreground">{rec.description}</p>
                        </div>
                        <div className="flex shrink-0 flex-col items-end gap-1.5">
                          <Badge variant="muted">{rec.difficulty}</Badge>
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={rec.alreadyAdded || busy[rec.id]}
                            onClick={() => onAddProject(rec)}
                          >
                            {busy[rec.id] ? (
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            ) : rec.alreadyAdded ? (
                              <>
                                <Check className="mr-1 h-3.5 w-3.5" /> Added
                              </>
                            ) : (
                              <>
                                <Plus className="mr-1 h-3.5 w-3.5" /> Add to Roadmap
                              </>
                            )}
                          </Button>
                        </div>
                      </div>
                      <p className="mt-2 text-xs text-muted-foreground">
                        <span className="font-medium text-foreground/80">Addresses:</span> {rec.addressesGap}
                      </p>
                    </div>
                  ))}
                </div>
              </DepthCard>
            )}

            {tutorialVideos.length > 0 && (
              <DepthCard className="mt-6 p-5">
                <div className="mb-3 flex items-center gap-2">
                  <Sparkles className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Tutorials for your gaps</CardTitle>
                </div>
                <p className="mb-3 text-xs text-muted-foreground">
                  Real tutorials for the skills still pending on your roadmap — curated from your
                  Learning Recommendations. Watch, then mark the related tasks done above.
                </p>
                <TutorialVideoLinks videos={tutorialVideos} />
              </DepthCard>
            )}
          </>
        )}
      </AsyncBoundary>

      <Dialog open={skillOffer !== null} onOpenChange={(open) => !open && !savingSkill && setSkillOffer(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Add {skillOffer?.skill} to My Skills?</DialogTitle>
            <DialogDescription>
              You completed the project &ldquo;{skillOffer?.projectTitle}&rdquo;, which demonstrates{" "}
              {skillOffer?.skill}. Recording it at {ROADMAP_SKILL_PROFICIENCY}% (source: Roadmap) lets it
              count toward your skill-gap analysis and future roadmaps. You can adjust or remove it any time
              in My Skills.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setSkillOffer(null)} disabled={savingSkill}>
              Not now
            </Button>
            <Button onClick={onConfirmAddSkill} disabled={savingSkill}>
              {savingSkill ? <Loader2 className="mr-1 h-3.5 w-3.5 animate-spin" /> : null}
              Add to My Skills
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function PhaseCard({
  phase,
  index,
  busy,
  onToggleTask,
}: {
  phase: RoadmapPhase;
  index: number;
  busy: Record<string, boolean>;
  onToggleTask: (task: RoadmapTask, next: "PLANNED" | "DONE") => void;
}) {
  const isCurrent = phase.status === "CURRENT";
  const isDone = phase.status === "DONE";
  return (
    <motion.div variants={staggerItem} className="relative mb-6 last:mb-0">
      <div
        className={cn(
          "absolute -left-8 top-1 flex h-7 w-7 items-center justify-center rounded-full border-2 text-xs font-semibold",
          isDone && "border-success bg-success/15 text-success",
          isCurrent && "border-violet-bright bg-violet/15 text-violet-bright shadow-glow-strong animate-pulse-glow",
          !isDone && !isCurrent && "border-border bg-muted text-muted-foreground",
        )}
      >
        {isDone ? <Check className="h-3.5 w-3.5" /> : index + 1}
      </div>
      <DepthCard className={cn("p-4", isCurrent && "border-violet/40 shadow-glow", isDone && "opacity-80")}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Map className="h-3.5 w-3.5 text-violet-bright" />
            <Badge variant={phaseTone[phase.status]}>{phase.status}</Badge>
          </div>
          <span className="text-xs font-medium text-muted-foreground">{phase.progress}%</span>
        </div>
        <CardTitle className="mt-1.5">{phase.title}</CardTitle>
        {phase.rationale && <p className="mt-1 text-sm text-muted-foreground">{phase.rationale}</p>}

        <ul className="mt-3 space-y-2">
          {phase.tasks.map((task) => {
            const done = task.status === "DONE";
            const isBusy = busy[task.id];
            return (
              <li key={task.id} className="flex items-start gap-2.5">
                <button
                  type="button"
                  disabled={isBusy}
                  onClick={() => onToggleTask(task, done ? "PLANNED" : "DONE")}
                  aria-label={done ? `Mark "${task.title}" not done` : `Mark "${task.title}" done`}
                  className={cn(
                    "mt-0.5 flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded border transition-colors",
                    done
                      ? "border-success bg-success/20 text-success"
                      : "border-border text-transparent",
                    isBusy && "opacity-50",
                  )}
                >
                  {isBusy ? (
                    <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
                  ) : (
                    <Check className="h-3 w-3" />
                  )}
                </button>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={cn(
                        "text-sm",
                        done ? "text-muted-foreground line-through" : "text-foreground",
                      )}
                    >
                      {task.title}
                    </span>
                    {task.kind === "PROJECT" && <Badge variant="default">Project</Badge>}
                    {task.skill && <Badge variant="muted">{task.skill}</Badge>}
                  </div>
                  {task.detail && <p className="mt-0.5 text-xs text-muted-foreground">{task.detail}</p>}
                </div>
              </li>
            );
          })}
        </ul>
      </DepthCard>
    </motion.div>
  );
}
