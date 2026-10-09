"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, CheckCircle2, Loader2, RotateCcw, Sparkles, XCircle } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  getAttempt,
  startRetry,
  type AttemptQuestionResponse,
  type AttemptResponse,
} from "@/lib/api/aptitude";
import { cn } from "@/lib/utils";

// ─── Display helpers ──────────────────────────────────────────────────────────

const OPTION_KEYS = ["A", "B", "C", "D"] as const;

const optionText: Record<string, (q: AttemptQuestionResponse) => string> = {
  A: (q) => q.optionA,
  B: (q) => q.optionB,
  C: (q) => q.optionC,
  D: (q) => q.optionD,
};

const CATEGORY_LABELS: Record<string, string> = {
  QUANTITATIVE: "Quantitative Aptitude",
  LOGICAL: "Logical Reasoning",
  VERBAL: "Verbal Ability",
  DATA_INTERPRETATION: "Data Interpretation",
};

type Filter = "ALL" | "WRONG" | "UNANSWERED";

const FILTERS: { id: Filter; label: string }[] = [
  { id: "ALL", label: "All Questions" },
  { id: "WRONG", label: "Wrong Only" },
  { id: "UNANSWERED", label: "Unanswered" },
];

function matchesFilter(q: AttemptQuestionResponse, filter: Filter): boolean {
  if (filter === "WRONG") return q.selectedOption !== null && q.correct === false;
  if (filter === "UNANSWERED") return q.selectedOption === null;
  return true;
}

// ─── Page ─────────────────────────────────────────────────────────────────────

/**
 * Answer Review — question-by-question walkthrough of a COMPLETED attempt: the student's
 * pick (red when wrong, green when right), the verified correct option, and the bank
 * explanation. In-progress attempts are bounced back to the timed interface since the
 * answer key is only serialized after completion. Retry launches a practice-lane attempt
 * over the missed questions only — readiness is never touched.
 */
export default function AptitudeReviewPage() {
  const { attemptId } = useParams<{ attemptId: string }>();
  const router = useRouter();
  const pushToast = useAppStore((s) => s.pushToast);

  const { data: attempt, loading, error, reload } = useApiResource(
    () => getAttempt(attemptId),
    [attemptId],
    null as AttemptResponse | null,
  );

  const [filter, setFilter] = useState<Filter>("ALL");
  const [retrying, setRetrying] = useState(false);

  useEffect(() => {
    if (!attempt) return;
    if (attempt.status === "IN_PROGRESS") {
      router.replace(`/student/aptitude/attempt/${attempt.attemptId}/take`);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt?.attemptId, attempt?.status]);

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

  if (loading || (!attempt && !error)) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <Loader2 className="h-8 w-8 animate-spin text-violet-bright" />
        <p className="text-sm text-muted-foreground">Loading review…</p>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Couldn&apos;t load this review</AlertTitle>
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

  if (!attempt || attempt.status !== "COMPLETED") return null;

  const questions = attempt.questions;
  const correctCount = questions.filter((q) => q.correct === true).length;
  const wrongCount = questions.filter((q) => q.selectedOption !== null && q.correct === false).length;
  const skippedCount = questions.filter((q) => q.selectedOption === null).length;
  const missedCount = questions.filter((q) => q.correct !== true).length;
  const visible = questions.filter((q) => matchesFilter(q, filter));

  return (
    <div>
      <PageHeader
        eyebrow={attempt.mode === "OFFICIAL" ? "Official Test Review" : "Practice Session Review"}
        title={`Answer Review — ${attempt.testTitle}`}
        subtitle="Every question with your answer, the verified key, and the explanation."
        actions={
          <Button
            variant="ghost"
            onClick={() => router.push(`/student/aptitude/attempt/${attempt.attemptId}/result`)}
          >
            <ArrowLeft />
            Back to Result
          </Button>
        }
      />

      {/* Outcome summary + filters */}
      <Card className="mb-4">
        <CardContent className="flex flex-wrap items-center justify-between gap-4 p-4">
          <div className="flex flex-wrap items-center gap-3">
            <Badge variant="risk">
              <XCircle className="h-3 w-3" />
              {wrongCount} Wrong
            </Badge>
            <Badge variant="muted">{skippedCount} Skipped</Badge>
            <Badge variant="success">
              <CheckCircle2 className="h-3 w-3" />
              {correctCount} Correct
            </Badge>
          </div>
          <div className="flex items-center gap-1 rounded-lg border border-border bg-secondary/30 p-1">
            {FILTERS.map((f) => (
              <button
                key={f.id}
                type="button"
                onClick={() => setFilter(f.id)}
                className={cn(
                  "rounded-md px-3 py-1.5 text-xs font-medium transition-colors",
                  filter === f.id
                    ? "bg-violet text-white"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {f.label}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Question-by-question review */}
      {visible.length === 0 ? (
        <Card>
          <CardContent className="p-8 text-center text-sm text-muted-foreground">
            No questions match this filter.
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {visible.map((q) => {
            const pickedCorrect = q.selectedOption !== null && q.correct === true;
            return (
              <Card key={q.attemptQuestionId}>
                <CardContent className="p-6">
                  <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-medium text-muted-foreground">
                        Question <span className="font-semibold text-foreground">{q.ordinal}</span>
                      </p>
                      <Badge variant="muted">
                        {CATEGORY_LABELS[q.category] ?? q.category}
                      </Badge>
                    </div>
                    {q.correct === true && (
                      <Badge variant="success">
                        <CheckCircle2 className="h-3 w-3" />
                        Correct
                      </Badge>
                    )}
                    {q.selectedOption !== null && q.correct === false && (
                      <Badge variant="risk">
                        <XCircle className="h-3 w-3" />
                        Incorrect
                      </Badge>
                    )}
                    {q.selectedOption === null && <Badge variant="warning">Not Answered</Badge>}
                  </div>

                  <p className="mb-5 whitespace-pre-line text-base font-medium text-foreground">
                    {q.prompt}
                  </p>

                  <div className="space-y-2.5">
                    {OPTION_KEYS.map((key) => {
                      const selected = q.selectedOption === key;
                      const isCorrectOption = q.correctOption === key;
                      return (
                        <div
                          key={key}
                          className={cn(
                            "flex w-full items-start gap-3 rounded-lg border px-4 py-3 text-left text-sm",
                            // Correct option always wears the green outline; the student's pick
                            // is filled red when wrong, green when right.
                            isCorrectOption
                              ? selected
                                ? "border-success bg-success/10 text-foreground"
                                : "border-success/60 bg-secondary/30 text-muted-foreground"
                              : selected
                                ? "border-risk bg-risk/10 text-foreground"
                                : "border-border bg-secondary/30 text-muted-foreground",
                          )}
                        >
                          <span
                            className={cn(
                              "flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold",
                              isCorrectOption
                                ? "border-success bg-success text-white"
                                : selected
                                  ? "border-risk bg-risk text-white"
                                  : "border-border",
                            )}
                          >
                            {key}
                          </span>
                          <span>{optionText[key](q)}</span>
                        </div>
                      );
                    })}
                  </div>

                  {q.explanation && (
                    <div
                      className={cn(
                        "mt-4 rounded-lg border px-4 py-3 text-sm",
                        pickedCorrect
                          ? "border-success/30 bg-success/5 text-muted-foreground"
                          : "border-violet/30 bg-violet/5 text-muted-foreground",
                      )}
                    >
                      <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-foreground">
                        Explanation
                      </p>
                      <p className="whitespace-pre-line">{q.explanation}</p>
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {/* Footer actions */}
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5">
        <Button
          variant="ghost"
          onClick={() => router.push(`/student/aptitude/attempt/${attempt.attemptId}/result`)}
        >
          <ArrowLeft />
          Back to Result
        </Button>
        <div className="flex flex-wrap items-center gap-2">
          {missedCount > 0 && (
            <Button variant="outline" disabled={retrying} onClick={handleRetry}>
              {retrying ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw />}
              Retry Missed Questions ({missedCount})
            </Button>
          )}
          <Button
            variant="glow"
            onClick={() => router.push(`/student/aptitude/attempt/${attempt.attemptId}/analysis`)}
          >
            <Sparkles />
            AI Performance Analysis
          </Button>
        </div>
      </div>
    </div>
  );
}
