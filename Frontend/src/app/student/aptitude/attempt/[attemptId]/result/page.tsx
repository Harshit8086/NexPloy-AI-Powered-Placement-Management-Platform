"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Brain, Clock, FlaskConical, ListChecks, Loader2, RotateCcw, Sparkles, Timer } from "lucide-react";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { getAttempt, startRetry, type AttemptResponse } from "@/lib/api/aptitude";
import { cn } from "@/lib/utils";

// ─── Display helpers ──────────────────────────────────────────────────────────

const CATEGORY_LABELS: Record<string, string> = {
  QUANTITATIVE: "Quantitative Aptitude",
  LOGICAL: "Logical Reasoning",
  VERBAL: "Verbal Ability",
  DATA_INTERPRETATION: "Data Interpretation",
};

function formatDuration(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes} min`;
}

function scoreTone(percent: number): { ring: string; badge: "success" | "warning" | "risk" } {
  if (percent >= 70) return { ring: "stroke-success", badge: "success" };
  if (percent >= 40)
    return { ring: "stroke-[hsl(var(--warning,38_92%_50%))]", badge: "warning" };
  return { ring: "stroke-risk", badge: "risk" };
}

// ─── Page ─────────────────────────────────────────────────────────────────────

/**
 * Evaluation → Result. Scoring already happened synchronously server-side at submit;
 * the ~2s "Evaluating your attempt" spinner is cosmetic pacing that mirrors the design.
 * In-progress attempts are bounced back to the timed interface — there is nothing to
 * show for them here.
 */
export default function AptitudeResultPage() {
  const { attemptId } = useParams<{ attemptId: string }>();
  const router = useRouter();

  const { data: attempt, loading, error, reload } = useApiResource(
    () => getAttempt(attemptId),
    [attemptId],
    null as AttemptResponse | null,
  );
  const [revealed, setRevealed] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const pushToast = useAppStore((s) => s.pushToast);

  async function handleRetry() {
    if (!attempt || retrying) return;
    setRetrying(true);
    try {
      const retry = await startRetry(attempt.attemptId);
      pushToast(
        "Retry started",
        "Missed questions re-opened as a practice session — readiness is unaffected.",
      );
      router.push(`/student/aptitude/attempt/${retry.attemptId}/take`);
    } catch (err) {
      pushToast("Could not start the retry", err instanceof Error ? err.message : "Try again.");
      setRetrying(false);
    }
  }

  // Cosmetic evaluation delay; also bounce in-progress attempts back to /take.
  useEffect(() => {
    if (!attempt) return;
    if (attempt.status === "IN_PROGRESS") {
      router.replace(`/student/aptitude/attempt/${attempt.attemptId}/take`);
      return;
    }
    const t = setTimeout(() => setRevealed(true), 2000);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt?.attemptId, attempt?.status]);

  if (loading || (!attempt && !error)) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <Loader2 className="h-8 w-8 animate-spin text-violet-bright" />
        <p className="text-sm text-muted-foreground">Loading attempt…</p>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Couldn&apos;t load this result</AlertTitle>
        <AlertDescription className="flex flex-col gap-3">
          <span>{error}</span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={reload}>
              Retry
            </Button>
            <Button variant="ghost" size="sm" onClick={() => router.push("/student/aptitude")}>
              <ArrowLeft />
              Assessment Center
            </Button>
          </div>
        </AlertDescription>
      </Alert>
    );
  }

  if (!attempt) return null;

  if (!revealed) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-violet/10 text-violet-bright">
          <Brain className="h-7 w-7 animate-pulse" />
        </div>
        <div>
          <p className="text-lg font-semibold text-foreground">Evaluating your attempt…</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Scoring is deterministic — matching your answers against the verified bank keys.
          </p>
        </div>
      </div>
    );
  }

  const score = attempt.score ?? 0;
  const tone = scoreTone(score);
  const missedCount = attempt.questions.filter((q) => q.correct !== true).length;

  return (
    <div>
      <PageHeader
        eyebrow={attempt.mode === "OFFICIAL" ? "Official Test Result" : "Practice Session Result"}
        title={attempt.testTitle}
        subtitle={
          attempt.mode === "OFFICIAL"
            ? "Deterministic bank scoring — this result feeds your placement readiness."
            : "Practice result for self-review only — readiness is unaffected."
        }
        actions={
          <Button variant="ghost" onClick={() => router.push("/student/aptitude")}>
            <ArrowLeft />
            Assessment Center
          </Button>
        }
      />

      {attempt.mode === "PRACTICE" && (
        <Alert className="mb-4">
          <FlaskConical className="h-4 w-4" />
          <AlertDescription>
            AI-generated practice questions — answer keys are unverified, so this score never touches
            your placement readiness.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
        {/* Overall score */}
        <Card>
          <CardContent className="flex flex-col items-center gap-4 p-6">
            <ScoreRing
              value={score}
              size={160}
              colorClass={tone.ring}
              label="Overall Score"
              animateKey={attempt.attemptId}
            />
            <Badge variant={tone.badge}>
              {attempt.correctCount ?? 0} / {attempt.totalQuestions} correct
            </Badge>
            <div className="grid w-full grid-cols-2 gap-3 border-t border-border pt-4 text-center">
              <div>
                <Clock className="mx-auto mb-1 h-4 w-4 text-muted-foreground" />
                <p className="text-xs text-muted-foreground">Time Taken</p>
                <p className="text-sm font-semibold tabular-nums text-foreground">
                  {attempt.timeSpentSeconds !== null ? formatDuration(attempt.timeSpentSeconds) : "—"}
                </p>
              </div>
              <div>
                <Timer className="mx-auto mb-1 h-4 w-4 text-muted-foreground" />
                <p className="text-xs text-muted-foreground">Limit</p>
                <p className="text-sm font-semibold tabular-nums text-foreground">
                  {formatDuration(attempt.durationLimitSeconds)}
                </p>
              </div>
            </div>
            <div className="flex w-full flex-col gap-2 border-t border-border pt-4">
              <Button
                variant="glow"
                className="w-full"
                onClick={() => router.push(`/student/aptitude/attempt/${attempt.attemptId}/analysis`)}
              >
                <Sparkles />
                AI Performance Analysis
              </Button>
              <Button
                variant="outline"
                className="w-full"
                onClick={() => router.push(`/student/aptitude/attempt/${attempt.attemptId}/review`)}
              >
                <ListChecks />
                Review Answers
              </Button>
              {missedCount > 0 && (
                <Button variant="outline" className="w-full" disabled={retrying} onClick={handleRetry}>
                  {retrying ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw />}
                  Retry Missed Questions ({missedCount})
                </Button>
              )}
              <Button variant="ghost" className="w-full" onClick={() => router.push("/student/aptitude")}>
                Back to Assessment Center
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Section performance */}
        <Card>
          <CardHeader>
            <CardTitle>Section Performance</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {attempt.sections.length === 0 ? (
              <p className="text-sm text-muted-foreground">No section breakdown available.</p>
            ) : (
              attempt.sections.map((section) => {
                const sectionTone = scoreTone(section.percent);
                return (
                  <div key={section.category}>
                    <div className="mb-1.5 flex items-center justify-between text-sm">
                      <span className="font-medium text-foreground">
                        {CATEGORY_LABELS[section.category] ?? section.category}
                      </span>
                      <span className="tabular-nums text-muted-foreground">
                        {section.correct}/{section.total} · {section.percent}%
                      </span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-muted">
                      <div
                        className={cn(
                          "h-full rounded-full transition-all duration-700",
                          sectionTone.badge === "success"
                            ? "bg-success"
                            : sectionTone.badge === "warning"
                              ? "bg-[hsl(var(--warning,38_92%_50%))]"
                              : "bg-risk",
                        )}
                        style={{ width: `${section.percent}%` }}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}