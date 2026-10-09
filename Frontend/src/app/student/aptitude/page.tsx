"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Brain, Clock, FlaskConical, Loader2, Play, Sparkles, Timer } from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  getAptitudeHistory,
  listAptitudeTests,
  startPractice,
  type AptitudeTestResponse,
} from "@/lib/api/aptitude";

// ─── Display helpers ──────────────────────────────────────────────────────────

/** The four bank categories the practice lane can generate questions for. */
const PRACTICE_CATEGORIES = [
  { value: "QUANTITATIVE", label: "Quantitative Aptitude" },
  { value: "LOGICAL", label: "Logical Reasoning" },
  { value: "VERBAL", label: "Verbal Ability" },
  { value: "DATA_INTERPRETATION", label: "Data Interpretation" },
];

function formatDuration(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes} min`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

function scoreTone(score: number): "success" | "warning" | "risk" {
  return score >= 70 ? "success" : score >= 40 ? "warning" : "risk";
}

// ─── Page ─────────────────────────────────────────────────────────────────────

/**
 * Aptitude Assessment Center — the two-lane entry point. OFFICIAL tests are timed
 * MCQs drawn from the curated bank and feed placement readiness; AI Practice Mode
 * generates throwaway questions that never touch readiness.
 */
export default function AptitudeCenterPage() {
  const router = useRouter();
  const pushToast = useAppStore((s) => s.pushToast);

  const tests = useApiResource(() => listAptitudeTests(), [], [] as AptitudeTestResponse[]);
  const history = useApiResource(() => getAptitudeHistory(), [], []);

  const [practiceOpen, setPracticeOpen] = useState(false);
  const [practiceCategory, setPracticeCategory] = useState("QUANTITATIVE");
  const [practiceCount, setPracticeCount] = useState("10");
  const [startingPractice, setStartingPractice] = useState(false);

  async function handleStartPractice() {
    setStartingPractice(true);
    try {
      const attempt = await startPractice(practiceCategory, Number(practiceCount));
      setPracticeOpen(false);
      pushToast("Practice session started", "AI-generated questions loaded — good luck!");
      router.push(`/student/aptitude/attempt/${attempt.attemptId}/take`);
    } catch (err) {
      pushToast("Could not start practice", err instanceof Error ? err.message : "Try again.");
      setStartingPractice(false);
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Career Prep"
        title="Aptitude Assessment Center"
        subtitle="Official bank-tests feed your placement readiness; AI practice never does."
        actions={
          <Button variant="outline" onClick={() => setPracticeOpen(true)}>
            <FlaskConical />
            AI Practice Mode
          </Button>
        }
      />

      {/* Official test cards */}
      <AsyncBoundary
        loading={tests.loading}
        error={tests.error}
        empty={tests.data.length === 0}
        onRetry={tests.reload}
        emptyTitle="No tests configured"
        emptyMessage="The aptitude catalog has no tests yet."
      >
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {tests.data.map((test) => (
            <Card key={test.testId} className={!test.available ? "opacity-70" : undefined}>
              <CardHeader>
                <div className="flex items-start justify-between gap-2">
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
                    <Brain className="h-4 w-4" />
                  </div>
                  {test.available ? (
                    test.lastScore !== null ? (
                      <Badge variant={scoreTone(test.lastScore)}>Last: {test.lastScore}%</Badge>
                    ) : (
                      <Badge variant="muted">Not attempted</Badge>
                    )
                  ) : (
                    <Badge variant="risk">Unavailable</Badge>
                  )}
                </div>
                <CardTitle className="mt-2 text-base">{test.title}</CardTitle>
                <CardDescription className="line-clamp-3">{test.description}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center gap-4 text-xs text-muted-foreground">
                  <span className="inline-flex items-center gap-1">
                    <Timer className="h-3.5 w-3.5" />
                    {test.questionCount} questions
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3.5 w-3.5" />
                    {formatDuration(test.durationSeconds)}
                  </span>
                </div>
                {test.available ? (
                  <Button
                    variant="glow"
                    className="w-full"
                    onClick={() => router.push(`/student/aptitude/${test.testId}`)}
                  >
                    <Play />
                    {test.lastScore !== null ? "Retake Test" : "Start Test"}
                  </Button>
                ) : (
                  <p className="text-xs text-risk">{test.unavailableReason}</p>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      </AsyncBoundary>

      {/* Attempt history */}
      <div className="mt-8">
        <h2 className="mb-3 text-sm font-semibold text-foreground">Attempt History</h2>
        <AsyncBoundary
          loading={history.loading}
          error={history.error}
          empty={history.data.length === 0}
          onRetry={history.reload}
          emptyTitle="No attempts yet"
          emptyMessage="Take an official test or start an AI practice session to see it here."
        >
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Test</TableHead>
                  <TableHead>Mode</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Score</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {history.data.map((attempt) => (
                  <TableRow key={attempt.attemptId}>
                    <TableCell className="font-medium">{attempt.testTitle}</TableCell>
                    <TableCell>
                      {attempt.mode === "OFFICIAL" ? (
                        <Badge>Official</Badge>
                      ) : (
                        <Badge variant="muted">Practice</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDate(attempt.startedAt)}
                    </TableCell>
                    <TableCell>
                      {attempt.status === "COMPLETED" ? (
                        <Badge variant="success">Completed</Badge>
                      ) : (
                        <Badge variant="warning">In Progress</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {attempt.score !== null ? `${attempt.score}%` : "—"}
                    </TableCell>
                    <TableCell className="text-right">
                      {attempt.status === "COMPLETED" ? (
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() =>
                              router.push(`/student/aptitude/attempt/${attempt.attemptId}/result`)
                            }
                          >
                            View
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() =>
                              router.push(`/student/aptitude/attempt/${attempt.attemptId}/review`)
                            }
                          >
                            Review
                          </Button>
                        </div>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() =>
                            router.push(`/student/aptitude/attempt/${attempt.attemptId}/take`)
                          }
                        >
                          Resume
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        </AsyncBoundary>
      </div>

      {/* AI Practice Mode dialog */}
      <Dialog open={practiceOpen} onOpenChange={setPracticeOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-violet-bright" />
              AI Practice Mode
            </DialogTitle>
            <DialogDescription>
              AI-generated questions for self-practice only. Practice attempts never feed your
              placement readiness — the answer keys may not be verified.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <p className="text-sm font-medium">Category</p>
              <Select value={practiceCategory} onValueChange={setPracticeCategory}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PRACTICE_CATEGORIES.map((c) => (
                    <SelectItem key={c.value} value={c.value}>
                      {c.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <p className="text-sm font-medium">Questions</p>
              <Select value={practiceCount} onValueChange={setPracticeCount}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="5">5 questions</SelectItem>
                  <SelectItem value="10">10 questions</SelectItem>
                  <SelectItem value="15">15 questions</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setPracticeOpen(false)}>
              Cancel
            </Button>
            <Button variant="glow" disabled={startingPractice} onClick={handleStartPractice}>
              {startingPractice && <Loader2 className="animate-spin" />}
              Generate &amp; Start
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
