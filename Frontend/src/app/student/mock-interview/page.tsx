"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Loader2, Play, ChevronRight, Trophy, CheckCircle2, Sparkles, Mic, Code2, Volume2 } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { getPrimaryStudent } from "@/lib/api/students";
import {
  listMockInterviews,
  startMockInterview,
  type MockInterviewSummary,
  type MockInterviewType,
} from "@/lib/api/mockInterviews";

const TYPE_LABELS: Record<MockInterviewType, string> = {
  MIXED: "Mixed (behavioural + technical + coding)",
  TECHNICAL: "Technical",
  BEHAVIOURAL: "Behavioural",
  CODING: "Coding",
};

/** Short labels for the segmented type toggle. */
const TYPE_SHORT: Record<MockInterviewType, string> = {
  MIXED: "Mixed",
  TECHNICAL: "Technical",
  BEHAVIOURAL: "Behavioural",
  CODING: "Coding",
};

export default function MockInterviewPage() {
  const router = useRouter();
  const pushToast = useAppStore((st) => st.pushToast);

  const { data: history, loading, error, reload } = useApiResource<MockInterviewSummary[]>(
    () => listMockInterviews(),
    [],
    [],
  );

  const [type, setType] = useState<MockInterviewType>("MIXED");
  const [role, setRole] = useState("");
  const [useResume, setUseResume] = useState(true);
  const [starting, setStarting] = useState(false);
  const [firstName, setFirstName] = useState("");

  // Seed the target role: a ?role= query param (e.g. from a Drive's "practice" link) wins;
  // otherwise fall back to the student's stored target role. The student can still edit it.
  useEffect(() => {
    let cancelled = false;
    getPrimaryStudent()
      .then((student) => {
        if (cancelled) return;
        if (student?.name) setFirstName(student.name.split(/\s+/)[0] ?? "");
        if (student?.targetRole) setRole((prev) => prev || student.targetRole);
      })
      .catch(() => {
        // no profile / not signed in as student — leave the fields as-is
      });
    const fromQuery =
      typeof window !== "undefined" ? new URLSearchParams(window.location.search).get("role") : null;
    if (fromQuery) setRole(fromQuery);
    return () => {
      cancelled = true;
    };
  }, []);

  const onStart = useCallback(async () => {
    setStarting(true);
    try {
      const interview = await startMockInterview({
        type,
        role: role.trim() || null,
        useResume,
      });
      router.push(`/student/mock-interview/${interview.id}`);
    } catch (e) {
      pushToast("Couldn't start interview", e instanceof Error ? e.message : "Please try again.");
      setStarting(false);
    }
  }, [type, role, useResume, router, pushToast]);

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Growth"
        title="AI Mock Interview"
        subtitle="Practise with AI-generated questions tailored to your target role, then get scored, actionable feedback. Practice only — this never affects your placement record."
      />

      <DepthCard className="mb-6 overflow-hidden p-0">
        <div className="grid md:grid-cols-2">
          {/* Left: welcome + what to expect */}
          <div className="border-b border-border bg-gradient-to-br from-violet/10 to-transparent p-6 md:border-b-0 md:border-r">
            <div className="mb-2 flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-violet-bright" />
              <span className="text-xs font-semibold uppercase tracking-wide text-violet-bright">AI Mock Interview</span>
            </div>
            <h2 className="text-xl font-semibold text-foreground">Welcome back{firstName ? `, ${firstName}` : ""}</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              A spoken AI interviewer asks role-specific questions, then scores every answer with
              actionable feedback. Practice only — it never affects your placement record.
            </p>
            <ul className="mt-5 space-y-3">
              {[
                { icon: <Volume2 className="h-4 w-4" />, text: "A voice interviewer that speaks each question aloud" },
                { icon: <Mic className="h-4 w-4" />, text: "Answer by voice or typing — your choice" },
                { icon: <Code2 className="h-4 w-4" />, text: "Built-in code editor for coding questions" },
                { icon: <CheckCircle2 className="h-4 w-4" />, text: "Scored feedback and a full report at the end" },
              ].map((f, i) => (
                <li key={i} className="flex items-center gap-3 text-sm text-foreground/90">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-violet/15 text-violet-bright">
                    {f.icon}
                  </span>
                  {f.text}
                </li>
              ))}
            </ul>
          </div>

          {/* Right: session configuration */}
          <div className="p-6">
            <div className="space-y-1.5">
              <Label htmlFor="mi-role">Target role</Label>
              <Input
                id="mi-role"
                placeholder="e.g. Backend Developer"
                value={role}
                maxLength={200}
                onChange={(e) => setRole(e.target.value)}
              />
            </div>

            <div className="mt-4 space-y-1.5">
              <Label>Interview type</Label>
              <div className="grid grid-cols-2 gap-2">
                {(Object.keys(TYPE_SHORT) as MockInterviewType[]).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setType(t)}
                    className={cn(
                      "rounded-lg border px-3 py-2 text-sm font-medium transition-colors",
                      type === t
                        ? "border-violet/50 bg-violet/15 text-violet-bright shadow-glow"
                        : "border-border text-muted-foreground hover:text-foreground",
                    )}
                  >
                    {TYPE_SHORT[t]}
                  </button>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">{TYPE_LABELS[type]}</p>
            </div>

            <div className="mt-4 flex items-center justify-between rounded-lg border border-border p-3">
              <div>
                <p className="text-sm font-medium text-foreground">Use my profile</p>
                <p className="text-xs text-muted-foreground">
                  Grounds questions on your target role, branch and strengths.
                </p>
              </div>
              <Switch checked={useResume} onCheckedChange={setUseResume} aria-label="Use my profile" />
            </div>

            <Button className="mt-5 w-full" size="lg" onClick={onStart} disabled={starting}>
              {starting ? (
                <>
                  <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> Preparing questions…
                </>
              ) : (
                <>
                  <Play className="mr-1.5 h-4 w-4" /> Start Interview
                </>
              )}
            </Button>
          </div>
        </div>
      </DepthCard>

      <h2 className="mb-3 text-sm font-semibold text-foreground">Your practice history</h2>
      <AsyncBoundary
        loading={loading}
        error={error}
        empty={history.length === 0}
        onRetry={reload}
        emptyTitle="No practice interviews yet"
        emptyMessage="Start a session above — completed runs and their reports will appear here."
      >
        <div className="space-y-2">
          {history.map((run) => (
            <Link
              key={run.id}
              href={
                run.status === "COMPLETED"
                  ? `/student/mock-interview/${run.id}/report`
                  : `/student/mock-interview/${run.id}`
              }
              className="group flex items-center justify-between gap-3 rounded-xl border border-border p-4 transition-colors hover:border-violet/40"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium text-foreground">
                    {run.role || TYPE_LABELS[run.type].split(" ")[0]}
                  </span>
                  <Badge variant="muted">{run.type}</Badge>
                  <Badge variant={run.status === "COMPLETED" ? "success" : "default"}>
                    {run.status === "COMPLETED" ? "Completed" : "In progress"}
                  </Badge>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  {new Date(run.createdAt).toLocaleString()}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-3">
                {run.status === "COMPLETED" && run.overallScore != null && (
                  <span className="flex items-center gap-1 text-sm font-semibold text-foreground">
                    <Trophy className="h-3.5 w-3.5 text-violet-bright" />
                    {run.overallScore}
                  </span>
                )}
                <ChevronRight className="h-4 w-4 text-muted-foreground group-hover:text-violet-bright" />
              </div>
            </Link>
          ))}
        </div>
      </AsyncBoundary>
    </div>
  );
}
