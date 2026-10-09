"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Brain, CheckCircle2, Clock, Loader2, Play, Timer } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { listAptitudeTests, startAttempt } from "@/lib/api/aptitude";

function formatDuration(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes} min`;
}

/** Fixed rules shown on every official test's instructions card. */
const TEST_RULES = [
  "The timer starts as soon as the test opens and cannot be paused.",
  "Each question has exactly one correct answer; there is no negative marking.",
  "Use Mark for Review to flag a question and revisit it before submitting.",
  "Your answers are saved automatically — a page refresh resumes the attempt.",
  "When time runs out the test is submitted automatically with your saved answers.",
  "Only official bank-tests feed your placement readiness; scores are final once submitted.",
];

/**
 * Test Details + Instructions — one catalog test's shape (from the code-level catalog,
 * surfaced via GET /tests) plus the rules, then Start which POSTs the attempt and drops
 * straight into the timed interface. Resuming an IN_PROGRESS attempt returns the same
 * attempt id server-side, so "Start" doubles as "Resume" with no extra client logic.
 */
export default function AptitudeTestDetailsPage() {
  const { testId } = useParams<{ testId: string }>();
  const router = useRouter();
  const pushToast = useAppStore((s) => s.pushToast);

  const { data: tests, loading, error, reload } = useApiResource(
    () => listAptitudeTests(),
    [],
    [],
  );
  const [starting, setStarting] = useState(false);

  const test = tests.find((t) => t.testId === testId);

  async function handleStart() {
    if (!test) return;
    setStarting(true);
    try {
      const attempt = await startAttempt(test.testId);
      router.push(`/student/aptitude/attempt/${attempt.attemptId}/take`);
    } catch (err) {
      pushToast("Could not start the test", err instanceof Error ? err.message : "Try again.");
      setStarting(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Couldn&apos;t load this test</AlertTitle>
        <AlertDescription className="flex flex-col gap-3">
          <span>{error}</span>
          <Button variant="outline" size="sm" className="w-fit" onClick={reload}>
            Retry
          </Button>
        </AlertDescription>
      </Alert>
    );
  }

  if (!test) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed border-border py-16 text-center">
        <Brain className="h-6 w-6 text-muted-foreground" />
        <p className="font-medium">Test not found</p>
        <p className="max-w-sm text-sm text-muted-foreground">
          This test is not part of the aptitude catalog.
        </p>
        <Button variant="ghost" size="sm" onClick={() => router.push("/student/aptitude")}>
          <ArrowLeft />
          Back to Assessment Center
        </Button>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        eyebrow="Aptitude Test"
        title={test.title}
        subtitle={test.description}
        actions={
          <Button variant="ghost" onClick={() => router.push("/student/aptitude")}>
            <ArrowLeft />
            Assessment Center
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="flex items-center gap-3 p-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
              <Timer className="h-4 w-4" />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Questions</p>
              <p className="text-lg font-semibold tabular-nums">{test.questionCount}</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-3 p-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
              <Clock className="h-4 w-4" />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Duration</p>
              <p className="text-lg font-semibold tabular-nums">
                {formatDuration(test.durationSeconds)}
              </p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-3 p-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
              <CheckCircle2 className="h-4 w-4" />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Last Score</p>
              <p className="text-lg font-semibold tabular-nums">
                {test.lastScore !== null ? `${test.lastScore}%` : "—"}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Instructions</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <ul className="space-y-2.5">
            {TEST_RULES.map((rule) => (
              <li key={rule} className="flex items-start gap-2.5 text-sm text-muted-foreground">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" />
                {rule}
              </li>
            ))}
          </ul>

          <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
            {test.available ? (
              <>
                <Badge variant="success">Test available — bank is stocked</Badge>
                <Button variant="glow" disabled={starting} onClick={handleStart}>
                  {starting ? <Loader2 className="animate-spin" /> : <Play />}
                  {test.lastScore !== null ? "Retake Test" : "Start Test"}
                </Button>
              </>
            ) : (
              <>
                <Badge variant="risk">Unavailable</Badge>
                <p className="text-sm text-risk">{test.unavailableReason}</p>
              </>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
