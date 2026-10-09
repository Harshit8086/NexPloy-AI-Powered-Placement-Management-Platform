"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  AlertTriangle,
  ArrowLeft,
  Brain,
  ChevronLeft,
  ChevronRight,
  Flag,
  FlaskConical,
  Loader2,
  Send,
  X,
} from "lucide-react";
import { CircularTimer } from "@/components/interview/CircularTimer";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  getAttempt,
  saveAnswer,
  submitAttempt,
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

function formatClock(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

// ─── Page ─────────────────────────────────────────────────────────────────────

/**
 * Timed Test Interface — the immersive attempt screen shared by both lanes.
 * Every answer / mark-for-review toggle is PUT-persisted immediately, so a page
 * refresh resumes exactly where the student left off (the server returns the same
 * IN_PROGRESS attempt). The clock is derived from the server's startedAt, not local
 * state, so refreshing never grants extra time; on expiry the attempt auto-submits.
 * Correct options and explanations are never shown here — the server only serializes
 * them once the attempt is COMPLETED.
 */
export default function AptitudeTakePage() {
  const { attemptId } = useParams<{ attemptId: string }>();
  const router = useRouter();
  const pushToast = useAppStore((s) => s.pushToast);

  const { data: attempt, loading, error, reload } = useApiResource(
    () => getAttempt(attemptId),
    [attemptId],
    null as AttemptResponse | null,
  );

  const [questions, setQuestions] = useState<AttemptQuestionResponse[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [seenIds, setSeenIds] = useState<Set<string>>(new Set());
  const [initialRemaining, setInitialRemaining] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  // Seed local question state + the timer's starting point from the server attempt.
  useEffect(() => {
    if (!attempt) return;
    if (attempt.status === "COMPLETED") {
      router.replace(`/student/aptitude/attempt/${attempt.attemptId}/result`);
      return;
    }
    setQuestions(attempt.questions);
    if (initialRemaining === null) {
      const elapsed = Math.floor((Date.now() - new Date(attempt.startedAt).getTime()) / 1000);
      setInitialRemaining(Math.max(0, attempt.durationLimitSeconds - elapsed));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt]);

  // 1s tick for the mm:ss readout (the ring keeps its own interval).
  useEffect(() => {
    if (!attempt) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [attempt?.attemptId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Track which questions the student has actually seen (palette colouring).
  useEffect(() => {
    const q = questions[currentIndex];
    if (!q) return;
    setSeenIds((prev) => (prev.has(q.attemptQuestionId) ? prev : new Set(prev).add(q.attemptQuestionId)));
  }, [currentIndex, questions]);

  async function doSubmit(auto: boolean) {
    if (!attempt || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    setConfirmOpen(false);
    // Client-measured time; the server clamps to duration limit + 60s grace.
    const timeSpent = Math.min(
      Math.floor((Date.now() - new Date(attempt.startedAt).getTime()) / 1000),
      attempt.durationLimitSeconds + 60,
    );
    try {
      await submitAttempt(attempt.attemptId, timeSpent);
      pushToast(
        auto ? "Time's up — submitted automatically" : "Test submitted",
        "Your saved answers were scored deterministically.",
      );
      router.replace(`/student/aptitude/attempt/${attempt.attemptId}/result`);
    } catch (err) {
      pushToast("Could not submit the attempt", err instanceof Error ? err.message : "Try again.");
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  function applyLocally(q: AttemptQuestionResponse, selectedOption: string | null, marked: boolean) {
    setQuestions((prev) =>
      prev.map((item) =>
        item.attemptQuestionId === q.attemptQuestionId ? { ...item, selectedOption, marked } : item,
      ),
    );
  }

  async function persistAnswer(q: AttemptQuestionResponse, selectedOption: string | null, marked: boolean) {
    try {
      const updated = await saveAnswer(attemptId, q.attemptQuestionId, selectedOption, marked);
      setQuestions(updated.questions);
    } catch (err) {
      pushToast("Answer not saved", err instanceof Error ? err.message : "Check your connection and try again.");
      reload();
    }
  }

  /** Clicking the already-selected option clears the answer (selectedOption=null). */
  function selectOption(q: AttemptQuestionResponse, key: string) {
    const next = q.selectedOption === key ? null : key;
    applyLocally(q, next, q.marked);
    void persistAnswer(q, next, q.marked);
  }

  function toggleMark(q: AttemptQuestionResponse) {
    applyLocally(q, q.selectedOption, !q.marked);
    void persistAnswer(q, q.selectedOption, !q.marked);
  }

  if (loading || (!attempt && !error)) {
    return (
      <div className="mx-auto max-w-6xl space-y-4 px-4 py-6 sm:px-6 lg:px-8">
        <Skeleton className="h-14 w-full" />
        <div className="grid gap-4 lg:grid-cols-[1fr_280px]">
          <Skeleton className="h-96 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-10 sm:px-6 lg:px-8">
        <Alert variant="destructive">
          <AlertTitle>Couldn&apos;t load this attempt</AlertTitle>
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
      </div>
    );
  }

  if (!attempt || questions.length === 0) return null;

  const current = questions[currentIndex];
  const answeredCount = questions.filter((q) => q.selectedOption !== null).length;
  const markedCount = questions.filter((q) => q.marked).length;
  const unansweredCount = questions.length - answeredCount;

  const startedAtMs = new Date(attempt.startedAt).getTime();
  const elapsedSeconds = Math.max(0, Math.floor((now - startedAtMs) / 1000));
  const remainingDisplay = Math.max(0, attempt.durationLimitSeconds - elapsedSeconds);

  function paletteClass(q: AttemptQuestionResponse): string {
    const base =
      "flex h-9 w-9 items-center justify-center rounded-lg border text-xs font-semibold tabular-nums transition-colors";
    if (q.marked) return cn(base, "border-violet/50 bg-violet/15 text-violet-bright");
    if (q.selectedOption !== null) return cn(base, "border-success/40 bg-success/15 text-success");
    if (seenIds.has(q.attemptQuestionId)) return cn(base, "border-border bg-muted text-foreground");
    return cn(base, "border-border bg-transparent text-muted-foreground");
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6 lg:px-8">
      {/* Header — test identity, clock, submit */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet/10 text-violet-bright">
            <Brain className="h-5 w-5" />
          </div>
          <div>
            <p className="text-xs uppercase tracking-wide text-muted-foreground">
              {attempt.mode === "OFFICIAL" ? "Official Aptitude Test" : "AI Practice Session"}
            </p>
            <h1 className="text-lg font-semibold text-foreground">{attempt.testTitle}</h1>
          </div>
          {attempt.mode === "PRACTICE" && (
            <Badge variant="muted">
              <FlaskConical className="h-3 w-3" />
              Practice — readiness unaffected
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-4">
          <div className="text-right">
            <p className="text-xs text-muted-foreground">Time left</p>
            <p
              className={cn(
                "text-xl font-semibold tabular-nums",
                remainingDisplay <= 60 ? "text-risk" : "text-foreground",
              )}
            >
              {formatClock(remainingDisplay)}
            </p>
          </div>
          {initialRemaining !== null && (
            <CircularTimer
              seconds={initialRemaining}
              resetKey={attempt.attemptId}
              onExpire={() => doSubmit(true)}
              size={56}
              strokeWidth={5}
            />
          )}
          <Button variant="glow" size="sm" disabled={submitting} onClick={() => setConfirmOpen(true)}>
            <Send />
            Submit
          </Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_280px]">
        {/* Question card */}
        <Card>
          <CardContent className="p-6">
            <div className="mb-4 flex items-center justify-between gap-2">
              <p className="text-sm font-medium text-muted-foreground">
                Question <span className="font-semibold text-foreground">{currentIndex + 1}</span> of{" "}
                {questions.length}
              </p>
              {current.marked && (
                <Badge variant="muted">
                  <Flag className="h-3 w-3" />
                  Marked for review
                </Badge>
              )}
            </div>

            <p className="mb-5 whitespace-pre-line text-base font-medium text-foreground">{current.prompt}</p>

            <div className="space-y-2.5">
              {OPTION_KEYS.map((key) => {
                const selected = current.selectedOption === key;
                return (
                  <button
                    key={key}
                    type="button"
                    onClick={() => selectOption(current, key)}
                    className={cn(
                      "flex w-full items-start gap-3 rounded-lg border px-4 py-3 text-left text-sm transition-colors",
                      selected
                        ? "border-violet bg-violet/10 text-foreground"
                        : "border-border bg-secondary/30 text-muted-foreground hover:border-violet/50 hover:text-foreground",
                    )}
                  >
                    <span
                      className={cn(
                        "flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold",
                        selected ? "border-violet bg-violet text-white" : "border-border",
                      )}
                    >
                      {key}
                    </span>
                    <span>{optionText[key](current)}</span>
                  </button>
                );
              })}
            </div>

            {/* Bottom controls — mark/clear on the left, prev/next on the right */}
            <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
              <div className="flex items-center gap-2">
                <Button
                  variant={current.marked ? "outline" : "ghost"}
                  size="sm"
                  onClick={() => toggleMark(current)}
                >
                  <Flag />
                  {current.marked ? "Marked for Review" : "Mark for Review"}
                </Button>
                {current.selectedOption !== null && (
                  <Button variant="ghost" size="sm" onClick={() => selectOption(current, current.selectedOption!)}>
                    <X />
                    Clear
                  </Button>
                )}
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={currentIndex === 0}
                  onClick={() => setCurrentIndex((i) => i - 1)}
                >
                  <ChevronLeft />
                  Previous
                </Button>
                {currentIndex < questions.length - 1 ? (
                  <Button variant="outline" size="sm" onClick={() => setCurrentIndex((i) => i + 1)}>
                    Next
                    <ChevronRight />
                  </Button>
                ) : (
                  <Button variant="glow" size="sm" disabled={submitting} onClick={() => setConfirmOpen(true)}>
                    <Send />
                    Finish
                  </Button>
                )}
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Question palette */}
        <div className="space-y-4">
          <Card>
            <CardContent className="p-4">
              <p className="mb-3 text-sm font-semibold text-foreground">Question Palette</p>
              <div className="grid grid-cols-5 gap-2">
                {questions.map((q, i) => (
                  <button
                    key={q.attemptQuestionId}
                    type="button"
                    onClick={() => setCurrentIndex(i)}
                    className={cn(
                      paletteClass(q),
                      i === currentIndex && "ring-2 ring-violet ring-offset-1 ring-offset-background",
                    )}
                  >
                    {i + 1}
                  </button>
                ))}
              </div>
              <div className="mt-4 space-y-1.5 border-t border-border pt-3 text-xs text-muted-foreground">
                <p className="flex items-center gap-2">
                  <span className="h-3 w-3 rounded-sm border-success/40 bg-success/15" /> Answered
                </p>
                <p className="flex items-center gap-2">
                  <span className="h-3 w-3 rounded-sm border-violet/50 bg-violet/15" /> Marked for review
                </p>
                <p className="flex items-center gap-2">
                  <span className="h-3 w-3 rounded-sm border-border bg-muted" /> Seen, not answered
                </p>
                <p className="flex items-center gap-2">
                  <span className="h-3 w-3 rounded-sm border-border" /> Not seen
                </p>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="space-y-1.5 p-4 text-sm">
              <p className="flex justify-between">
                <span className="text-muted-foreground">Answered</span>
                <span className="font-semibold tabular-nums text-foreground">
                  {answeredCount}/{questions.length}
                </span>
              </p>
              <p className="flex justify-between">
                <span className="text-muted-foreground">Marked</span>
                <span className="font-semibold tabular-nums text-foreground">{markedCount}</span>
              </p>
              <p className="flex justify-between">
                <span className="text-muted-foreground">Elapsed</span>
                <span className="font-semibold tabular-nums text-foreground">{formatClock(elapsedSeconds)}</span>
              </p>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Submit confirmation */}
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Submit this attempt?</DialogTitle>
            <DialogDescription>
              Scoring is deterministic and final once submitted — you cannot change answers afterwards.
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-3 gap-3 text-center">
            <div className="rounded-lg border border-border p-3">
              <p className="text-lg font-semibold tabular-nums text-success">{answeredCount}</p>
              <p className="text-xs text-muted-foreground">Answered</p>
            </div>
            <div className="rounded-lg border border-border p-3">
              <p className="text-lg font-semibold tabular-nums text-foreground">{unansweredCount}</p>
              <p className="text-xs text-muted-foreground">Unanswered</p>
            </div>
            <div className="rounded-lg border border-border p-3">
              <p className="text-lg font-semibold tabular-nums text-violet-bright">{markedCount}</p>
              <p className="text-xs text-muted-foreground">Marked</p>
            </div>
          </div>
          {unansweredCount > 0 && (
            <p className="flex items-center gap-2 text-sm text-risk">
              <AlertTriangle className="h-4 w-4 shrink-0" />
              {unansweredCount} question{unansweredCount === 1 ? " is" : "s are"} still unanswered.
            </p>
          )}
          <DialogFooter>
            <Button variant="ghost" onClick={() => setConfirmOpen(false)}>
              Keep Working
            </Button>
            <Button variant="glow" disabled={submitting} onClick={() => doSubmit(false)}>
              {submitting ? <Loader2 className="animate-spin" /> : <Send />}
              Submit Final Answers
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}