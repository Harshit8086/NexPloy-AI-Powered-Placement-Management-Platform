"use client";

import { useMemo } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Sparkles,
  TrendingUp,
  AlertTriangle,
  Lightbulb,
  Code2,
  MessageSquare,
  Map,
  ArrowUpRight,
} from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { TutorialVideoLinks } from "@/components/intelligence/TutorialVideoLinks";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useApiResource } from "@/hooks/useApiResource";
import { getLearningRecommendations, type TutorialVideo } from "@/lib/api/students";
import {
  getMockInterview,
  completeMockInterview,
  type MockInterview,
  type AnswerFeedback,
} from "@/lib/api/mockInterviews";

// The nine per-answer metrics AnswerFeedback carries, labelled for display.
const METRICS: { key: keyof AnswerFeedback; label: string }[] = [
  { key: "correctness", label: "Correctness" },
  { key: "clarity", label: "Clarity" },
  { key: "relevance", label: "Relevance" },
  { key: "detail", label: "Detail" },
  { key: "efficiency", label: "Efficiency" },
  { key: "communication", label: "Communication" },
  { key: "problemSolving", label: "Problem solving" },
  { key: "creativity", label: "Creativity" },
];

// Average the numeric metrics across every scored answer so the report can show
// aggregate strengths without the backend needing to pre-compute them.
function averageMetrics(data: MockInterview): { key: keyof AnswerFeedback; label: string; value: number }[] {
  const scored = data.questions.map((q) => q.feedback).filter((f): f is AnswerFeedback => f != null);
  if (scored.length === 0) return METRICS.map((m) => ({ ...m, value: 0 }));
  return METRICS.map((m) => {
    const sum = scored.reduce((acc, f) => acc + (typeof f[m.key] === "number" ? (f[m.key] as number) : 0), 0);
    return { ...m, value: Math.round(sum / scored.length) };
  });
}

export default function MockInterviewReportPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;

  // A run reaches the report already COMPLETED (the run page finalises on the last
  // answer). If a student lands here on a run that is still IN_PROGRESS, complete it.
  const { data, loading, error, reload } = useApiResource<MockInterview | null>(async () => {
    const run = await getMockInterview(id);
    if (run.status !== "COMPLETED") {
      return completeMockInterview(id);
    }
    return run;
  }, [id], null);

  const metrics = useMemo(() => (data ? averageMetrics(data) : []), [data]);

  // Practice → learn: reuse the student's existing Learning Recommendations (already YouTube-enriched
  // server-side) to point weak areas at real tutorials. Non-fatal and advisory — a student with no
  // pending gaps (or no YouTube key configured) simply sees no video section.
  const { data: recs } = useApiResource(
    () => getLearningRecommendations().catch(() => []),
    [],
    [],
  );

  // Flatten every recommendation's tutorial videos into one deduped list for the "improve" section.
  const improveVideos = useMemo<TutorialVideo[]>(() => {
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
    return out.slice(0, 6);
  }, [recs]);

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        eyebrow="Mock Interview"
        title={data?.role ? `${data.role} — report` : "Practice report"}
        subtitle="Your scored feedback across every answer. Practice only — this never affects your placement record."
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!data}
        onRetry={reload}
        emptyTitle="Report unavailable"
        emptyMessage="This practice interview doesn't exist or isn't yours."
      >
        {data && (
          <>
            <DepthCard className="mb-5 flex flex-col items-center gap-4 p-6 sm:flex-row sm:items-center sm:gap-8">
              <ScoreRing
                value={data.overallScore ?? 0}
                label="Overall"
                sublabel="out of 100"
                animateKey={data.id}
              />
              <div className="min-w-0 flex-1">
                <div className="mb-2 flex items-center gap-2">
                  <Sparkles className="h-4 w-4 text-violet-bright" />
                  <span className="text-sm font-semibold text-foreground">Summary</span>
                </div>
                <p className="text-sm text-muted-foreground">
                  {data.summary || "No summary was generated for this run."}
                </p>
              </div>
            </DepthCard>

            {metrics.some((m) => m.value > 0) && (
              <DepthCard className="mb-5 p-5">
                <h2 className="mb-3 text-sm font-semibold text-foreground">Average across your answers</h2>
                <div className="grid gap-3 sm:grid-cols-2">
                  {metrics.map((m) => (
                    <SkillBar key={m.key} label={m.label} value={m.value} />
                  ))}
                </div>
              </DepthCard>
            )}

            <div className="mb-5 grid gap-4 sm:grid-cols-3">
              <ReportList
                icon={<TrendingUp className="h-4 w-4 text-success" />}
                title="Strengths"
                items={data.strengths}
                accent="border-success/30"
              />
              <ReportList
                icon={<AlertTriangle className="h-4 w-4 text-risk" />}
                title="To improve"
                items={data.weaknesses}
                accent="border-risk/30"
              />
              <ReportList
                icon={<Lightbulb className="h-4 w-4 text-violet-bright" />}
                title="Recommendations"
                items={data.recommendations}
                accent="border-violet/30"
              />
            </div>

            <DepthCard className="mb-5 p-5">
              <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Map className="h-4 w-4 text-violet-bright" />
                  <h2 className="text-sm font-semibold text-foreground">Improve these areas</h2>
                </div>
                <Button asChild variant="outline" size="sm">
                  <Link href="/student/roadmap">
                    Open my roadmap <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </Button>
              </div>
              <p className="mb-3 text-xs text-muted-foreground">
                Turn this practice into progress — your Career Roadmap sequences the skills to close,
                and these tutorials cover your current gaps. Practice feedback is advisory; it never
                changes your placement record.
              </p>
              {improveVideos.length > 0 ? (
                <TutorialVideoLinks videos={improveVideos} />
              ) : (
                <p className="rounded-lg border border-dashed border-border px-3 py-2 text-sm text-muted-foreground/70">
                  No tutorial videos to surface right now — check your roadmap for the next steps.
                </p>
              )}
            </DepthCard>

            <h2 className="mb-3 text-sm font-semibold text-foreground">Question-by-question</h2>
            <div className="space-y-3">
              {data.questions.map((q, i) => (
                <DepthCard key={q.id} className="p-4">
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <span className="text-xs font-medium text-muted-foreground">Q{i + 1}</span>
                    {q.coding ? (
                      <Badge variant="default">
                        <Code2 className="mr-1 h-3 w-3" /> Coding
                      </Badge>
                    ) : (
                      <Badge variant="muted">
                        <MessageSquare className="mr-1 h-3 w-3" /> Answer
                      </Badge>
                    )}
                    {q.score != null && (
                      <span className="ml-auto text-sm font-semibold tabular-nums text-foreground">
                        {q.score}/100
                      </span>
                    )}
                  </div>
                  <p className="text-sm font-medium text-foreground">{q.prompt}</p>
                  {q.studentAnswer && (
                    <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap rounded-md bg-muted/50 p-3 text-xs text-muted-foreground">
                      {q.studentAnswer}
                    </pre>
                  )}
                  {q.feedback && (
                    <div className="mt-2 rounded-md border border-border p-3">
                      <p className="text-sm text-muted-foreground">{q.feedback.feedback}</p>
                      {q.feedback.improvements.length > 0 && (
                        <ul className="mt-2 list-inside list-disc space-y-0.5 text-xs text-muted-foreground">
                          {q.feedback.improvements.map((imp, j) => (
                            <li key={j}>{imp}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  )}
                </DepthCard>
              ))}
            </div>

            <div className="mt-6 flex items-center gap-3">
              <Button asChild variant="outline">
                <Link href="/student/mock-interview">
                  <ArrowLeft className="mr-1.5 h-4 w-4" /> Back to practice
                </Link>
              </Button>
            </div>
          </>
        )}
      </AsyncBoundary>
    </div>
  );
}

function ReportList({
  icon,
  title,
  items,
  accent,
}: {
  icon: React.ReactNode;
  title: string;
  items: string[];
  accent: string;
}) {
  return (
    <DepthCard className={`p-4 ${accent}`}>
      <div className="mb-2 flex items-center gap-2">
        {icon}
        <span className="text-sm font-semibold text-foreground">{title}</span>
      </div>
      {items.length > 0 ? (
        <ul className="list-inside list-disc space-y-1 text-xs text-muted-foreground">
          {items.map((it, i) => (
            <li key={i}>{it}</li>
          ))}
        </ul>
      ) : (
        <p className="text-xs text-muted-foreground/70">None noted.</p>
      )}
    </DepthCard>
  );
}
