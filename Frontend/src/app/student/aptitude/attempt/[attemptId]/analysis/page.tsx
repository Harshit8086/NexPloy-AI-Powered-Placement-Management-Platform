"use client";

import { useParams, useRouter } from "next/navigation";
import {
  AlertTriangle,
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  ExternalLink,
  FlaskConical,
  Lightbulb,
  Loader2,
  Sparkles,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useApiResource } from "@/hooks/useApiResource";
import {
  getAnalysis,
  getAttempt,
  getStudyPlan,
  type AptitudeAnalysisResponse,
  type AttemptResponse,
  type StudyPlanResponse,
} from "@/lib/api/aptitude";
import { cn } from "@/lib/utils";

// ─── Display helpers ──────────────────────────────────────────────────────────

const CATEGORY_LABELS: Record<string, string> = {
  QUANTITATIVE: "Quantitative Aptitude",
  LOGICAL: "Logical Reasoning",
  VERBAL: "Verbal Ability",
  DATA_INTERPRETATION: "Data Interpretation",
};

// ─── Page ─────────────────────────────────────────────────────────────────────

/**
 * AI Performance Analysis — strengths / weaknesses / tips for a COMPLETED attempt,
 * plus the readiness before/after delta (design page 9). The attempt itself is
 * fetched alongside to know the lane: PRACTICE scores never feed readiness, so the
 * delta card is annotated accordingly instead of implying an impact.
 */
export default function AptitudeAnalysisPage() {
  const { attemptId } = useParams<{ attemptId: string }>();
  const router = useRouter();

  const { data: analysis, loading, error, reload } = useApiResource(
    () => getAnalysis(attemptId),
    [attemptId],
    null as AptitudeAnalysisResponse | null,
  );
  const { data: attempt } = useApiResource(
    () => getAttempt(attemptId),
    [attemptId],
    null as AttemptResponse | null,
  );
  // Curated study guidance for weak sections — deterministic catalog, mode-agnostic,
  // so it renders for practice attempts too. Hidden entirely if the endpoint errors.
  const { data: studyPlan, error: studyPlanError } = useApiResource(
    () => getStudyPlan(attemptId),
    [attemptId],
    null as StudyPlanResponse | null,
  );

  if (loading || (!analysis && !error)) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <Loader2 className="h-8 w-8 animate-spin text-violet-bright" />
        <p className="text-sm text-muted-foreground">Generating your analysis…</p>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Couldn&apos;t load the analysis</AlertTitle>
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

  if (!analysis) return null;

  const isPractice = attempt?.mode === "PRACTICE";
  const delta = analysis.aptitudeAfter - analysis.aptitudeBefore;
  const deltaTone = delta > 0 ? "up" : delta < 0 ? "down" : "flat";

  return (
    <div>
      <PageHeader
        eyebrow="AI Performance Analysis"
        title={analysis.testTitle}
        subtitle="Strengths, weaknesses and targeted tips — generated from your section performance."
        actions={
          <Button
            variant="ghost"
            onClick={() => router.push(`/student/aptitude/attempt/${attemptId}/result`)}
          >
            <ArrowLeft />
            Back to Result
          </Button>
        }
      />

      {isPractice && (
        <Alert className="mb-4">
          <FlaskConical className="h-4 w-4" />
          <AlertDescription>
            Practice session — AI-generated questions with unverified keys, so this score never
            touches your placement readiness. The readiness values below are shown for context only.
          </AlertDescription>
        </Alert>
      )}

      {/* Readiness before/after — design page 9 */}
      <Card className="mb-4">
        <CardContent className="flex flex-wrap items-center justify-between gap-4 p-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet/10 text-violet-bright">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-foreground">Aptitude Readiness Impact</p>
              <p className="text-xs text-muted-foreground">
                {isPractice
                  ? "Practice lane — readiness is unaffected by this score."
                  : "Deterministic score applied to your placement readiness."}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-6">
            <div className="text-center">
              <p className="text-xs text-muted-foreground">Before</p>
              <p className="text-xl font-semibold tabular-nums text-foreground">
                {analysis.aptitudeBefore}%
              </p>
            </div>
            <div
              className={cn(
                "flex items-center gap-1 rounded-full px-3 py-1 text-sm font-semibold tabular-nums",
                deltaTone === "up" && "bg-success/15 text-success",
                deltaTone === "down" && "bg-risk/15 text-risk",
                deltaTone === "flat" && "bg-muted text-muted-foreground",
              )}
            >
              {deltaTone === "up" && <TrendingUp className="h-4 w-4" />}
              {deltaTone === "down" && <TrendingDown className="h-4 w-4" />}
              {delta > 0 ? "+" : ""}
              {delta}%
            </div>
            <div className="text-center">
              <p className="text-xs text-muted-foreground">After</p>
              <p className="text-xl font-semibold tabular-nums text-foreground">
                {analysis.aptitudeAfter}%
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Summary */}
      <Card className="mb-4">
        <CardContent className="p-5">
          <p className="text-sm leading-relaxed text-muted-foreground">{analysis.summary}</p>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Strengths */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <CheckCircle2 className="h-4 w-4 text-success" />
              Strengths
            </CardTitle>
          </CardHeader>
          <CardContent>
            {analysis.strengths.length === 0 ? (
              <p className="text-sm text-muted-foreground">No standout strengths this attempt.</p>
            ) : (
              <ul className="space-y-2.5">
                {analysis.strengths.map((item) => (
                  <li key={item} className="flex items-start gap-2.5 text-sm text-foreground">
                    <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-success" />
                    {item}
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        {/* Weaknesses */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <AlertTriangle className="h-4 w-4 text-risk" />
              Focus Areas
            </CardTitle>
          </CardHeader>
          <CardContent>
            {analysis.weaknesses.length === 0 ? (
              <p className="text-sm text-muted-foreground">No weak areas flagged this attempt.</p>
            ) : (
              <ul className="space-y-2.5">
                {analysis.weaknesses.map((item) => (
                  <li key={item} className="flex items-start gap-2.5 text-sm text-foreground">
                    <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-risk" />
                    {item}
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      {/* What to Study Next — curated guidance for weak sections (deterministic catalog, no AI) */}
      {!studyPlanError && (
        <Card className="mt-4">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <BookOpen className="h-4 w-4 text-violet-bright" />
              What to Study Next
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!studyPlan ? (
              <p className="text-sm text-muted-foreground">Loading study guidance…</p>
            ) : studyPlan.recommendations.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                Strong attempt — every section scored at or above the 70% benchmark. Keep
                practising to stay sharp.
              </p>
            ) : (
              <div className="space-y-5">
                {studyPlan.recommendations.map((rec) => (
                  <div key={rec.category} className="rounded-lg border border-border p-4">
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm font-semibold text-foreground">
                        {CATEGORY_LABELS[rec.category] ?? rec.category}
                      </p>
                      <div className="flex items-center gap-2">
                        <Badge variant={rec.priority === "HIGH" ? "risk" : "warning"}>
                          {rec.priority === "HIGH" ? "High priority" : "Medium priority"}
                        </Badge>
                        <span className="text-xs tabular-nums text-muted-foreground">
                          {rec.percent}%
                        </span>
                      </div>
                    </div>
                    <div className="grid gap-4 md:grid-cols-2">
                      <div>
                        <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                          Topics to cover
                        </p>
                        <ul className="space-y-1.5">
                          {rec.topics.map((topic) => (
                            <li key={topic} className="flex items-start gap-2 text-sm text-foreground">
                              <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-violet-bright" />
                              {topic}
                            </li>
                          ))}
                        </ul>
                      </div>
                      <div>
                        <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                          Resources
                        </p>
                        <ul className="space-y-1.5">
                          {rec.resources.map((resource) => (
                            <li key={resource.url}>
                              <a
                                href={resource.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1.5 text-sm text-violet-bright hover:underline"
                              >
                                <ExternalLink className="h-3.5 w-3.5 shrink-0" />
                                {resource.title}
                                <span className="text-xs text-muted-foreground">
                                  ({resource.provider})
                                </span>
                              </a>
                            </li>
                          ))}
                        </ul>
                      </div>
                    </div>
                    <p className="mt-3 rounded-md bg-secondary/40 px-3 py-2 text-xs text-muted-foreground">
                      <span className="font-semibold text-foreground">Practice task: </span>
                      {rec.practiceTask}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Tips */}
      <Card className="mt-4">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Lightbulb className="h-4 w-4 text-violet-bright" />
            Preparation Tips
          </CardTitle>
        </CardHeader>
        <CardContent>
          {analysis.tips.length === 0 ? (
            <p className="text-sm text-muted-foreground">No tips generated.</p>
          ) : (
            <ol className="space-y-2.5">
              {analysis.tips.map((tip, i) => (
                <li key={tip} className="flex items-start gap-3 text-sm text-foreground">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-violet/10 text-xs font-semibold text-violet-bright">
                    {i + 1}
                  </span>
                  {tip}
                </li>
              ))}
            </ol>
          )}
        </CardContent>
      </Card>

      <div className="mt-6 flex flex-wrap gap-2">
        <Button
          variant="glow"
          onClick={() => router.push(`/student/aptitude/attempt/${attemptId}/result`)}
        >
          Back to Result
        </Button>
        <Button variant="outline" onClick={() => router.push("/student/aptitude")}>
          Assessment Center
        </Button>
      </div>
    </div>
  );
}