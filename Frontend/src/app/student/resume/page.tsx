"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  FileSearch,
  CheckCircle2,
  AlertTriangle,
  Lightbulb,
  Sparkles,
  Tag,
  Upload,
  Plus,
  Loader2,
  Target,
  ListChecks,
  ChevronDown,
  History,
  Clock,
  BookOpen,
  ExternalLink,
  GraduationCap,
  PlayCircle,
  FilePenLine,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Progress } from "@/components/ui/progress";
import { PageHeader } from "@/components/layout/PageHeader";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { IntelligencePulse, IntelligencePulseBadge } from "@/components/intelligence/IntelligencePulse";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { showcaseEntrance } from "@/lib/motion-variants";
import type { ResumeIntelligence } from "@/lib/simulate";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { extractResumeFile, type ResumeExtractionResult } from "@/lib/api/ai";
import {
  analyzeResumeFile,
  addRoadmapProject,
  getLearningRecommendations,
  getPrimaryStudent,
  getResumeIntelligence,
  getResumeAnalysisDetail,
  listResumeAnalysisHistory,
  upsertSkill,
  type ResumeAnalysis,
  type ResumeAnalysisSummary,
  type ResumeQualityAnalysis,
  type ResumeQualityDimension,
  type ResumeQualityFinding,
  type ResumeQualitySeverity,
  type ResumeQualityStatus,
  type LearningRecommendation,
} from "@/lib/api/students";

/**
 * §10 Resume Quality — the eight quality dimensions in display order, with a human label each. The
 * key indexes into the {@link ResumeQualityAnalysis} object so the section renders straight from the
 * grounded backend payload.
 */
const QUALITY_DIMENSIONS: ReadonlyArray<readonly [keyof ResumeQualityAnalysis, string]> = [
  ["structure", "Structure"],
  ["contentQuality", "Content Quality"],
  ["projectQuality", "Project Quality"],
  ["experienceQuality", "Experience Quality"],
  ["quantification", "Quantification"],
  ["skillsEvidence", "Skills Evidence"],
  ["targetRoleRelevance", "Target Role Relevance"],
  ["readability", "Readability / ATS"],
];

function qualityStatusBadge(status: ResumeQualityStatus): { variant: "success" | "warning" | "muted"; label: string } {
  switch (status) {
    case "GOOD":
      return { variant: "success", label: "Looks good" };
    case "NEEDS_ATTENTION":
      return { variant: "warning", label: "Needs attention" };
    default:
      return { variant: "muted", label: "Not enough evidence" };
  }
}

function severityBadgeVariant(severity: ResumeQualitySeverity): "risk" | "warning" | "muted" {
  if (severity === "HIGH") return "risk";
  if (severity === "MEDIUM") return "warning";
  return "muted";
}

/** One evidence-backed quality finding: the issue, the cited evidence, and the recommended fix. */
function QualityFinding({ finding }: { finding: ResumeQualityFinding }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <div className="mb-1.5 flex flex-wrap items-center gap-2">
        <Badge variant={severityBadgeVariant(finding.severity)}>{finding.severity}</Badge>
        <span className="text-sm font-medium text-foreground">{finding.issue}</span>
      </div>
      {finding.evidence && (
        <p className="mb-1 text-xs text-muted-foreground">
          <span className="font-semibold uppercase tracking-wider">Evidence:</span> {finding.evidence}
        </p>
      )}
      {finding.recommendation && (
        <p className="text-xs text-violet-bright/90">
          <span className="font-semibold uppercase tracking-wider">Fix:</span> {finding.recommendation}
        </p>
      )}
    </div>
  );
}

/** One quality dimension: its status and any findings (or an honest "not enough evidence" note). */
function QualityDimensionBlock({ label, dimension }: { label: string; dimension: ResumeQualityDimension }) {
  const badge = qualityStatusBadge(dimension.status);
  return (
    <div className="rounded-xl border border-border p-4">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-foreground">{label}</span>
        <Badge variant={badge.variant}>{badge.label}</Badge>
      </div>
      {dimension.findings.length > 0 ? (
        <div className="space-y-2">
          {dimension.findings.map((f, i) => (
            <QualityFinding key={`${f.category}-${i}`} finding={f} />
          ))}
        </div>
      ) : dimension.status === "GOOD" ? (
        <p className="text-xs text-muted-foreground">No material concerns found in this area.</p>
      ) : (
        <p className="text-xs text-muted-foreground/70">
          Not enough content in the resume to assess this dimension.
        </p>
      )}
    </div>
  );
}


/**
 * Loads the signed-in student's resume-intelligence summary from the live endpoint. The profile
 * (/students/me) is the anchor: a 404 means no student profile yet → null → empty state (never
 * fabricated). Before any resume is analyzed this returns the stored score, strengths and gaps only;
 * per-resume weaknesses/suggestions/recommendation come from analyzing an uploaded file.
 */
async function loadResumeIntelligence(): Promise<ResumeIntelligence | null> {
  // Confirm the signed-in user has a student profile first; no profile (404) → empty state.
  try {
    await getPrimaryStudent();
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status: number }).status === 404) {
      return null;
    }
    throw error;
  }
  return getResumeIntelligence();
}

/**
 * Loads the signed-in student's resume Analysis History (newest first). No student profile (404) or
 * no prior runs both resolve to an empty list — the History section then shows its own empty copy,
 * never fabricated runs.
 */
async function loadAnalysisHistory(): Promise<ResumeAnalysisSummary[]> {
  try {
    return await listResumeAnalysisHistory();
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status: number }).status === 404) {
      return [];
    }
    throw error;
  }
}

/**
 * Defense-in-depth for externally-sourced resource links. The backend catalog already validates that
 * every curated URL is https at construction, but we never render a resource link as a trusted target
 * without re-checking the scheme here — an http/javascript:/data: URL is dropped rather than opened.
 * Guards the "External URLs are validated before rendering as trusted resources" requirement.
 */
function isSafeHttpUrl(url: string): boolean {
  try {
    return new URL(url).protocol === "https:";
  } catch {
    return false;
  }
}

/** Formats a stored ISO timestamp for the history list; falls back to the raw value if unparseable. */
function formatRunDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

/**
 * Loads the signed-in student's Learning Recommendations from the live endpoint — one per still-pending
 * roadmap skill gap, each with curated resources and its related roadmap milestone. No student profile
 * (404) or no pending gaps both resolve to an empty list; the section then shows its own empty copy,
 * never fabricated resources.
 */
async function loadLearningRecommendations(): Promise<LearningRecommendation[]> {
  try {
    return await getLearningRecommendations();
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status: number }).status === 404) {
      return [];
    }
    throw error;
  }
}

function ResumeSkeleton() {
  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-6 space-y-2.5">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-7 w-72" />
        <Skeleton className="h-4 w-96" />
      </div>
      <Skeleton className="mb-5 h-[220px] w-full" />
      <Skeleton className="mb-5 h-[180px] w-full" />
      <div className="grid gap-5 md:grid-cols-2">
        <Skeleton className="h-[180px] w-full" />
        <Skeleton className="h-[180px] w-full" />
      </div>
    </div>
  );
}

export default function ResumeIntelligencePage() {
  const { data, loading, error, reload } = useApiResource<ResumeIntelligence | null>(
    loadResumeIntelligence,
    [],
    null,
  );
  const [revealKey, setRevealKey] = useState(0);
  const pushToast = useAppStore((st) => st.pushToast);
  const pendingResumeAnalysis = useAppStore((st) => st.pendingResumeAnalysis);
  const setPendingResumeAnalysis = useAppStore((st) => st.setPendingResumeAnalysis);

  // Analysis History (Phase B): the student's prior runs, newest first. Reloaded after each analyze
  // so a fresh run appears immediately. Empty list → the section shows its own empty state.
  const {
    data: history,
    loading: historyLoading,
    error: historyError,
    reload: reloadHistory,
  } = useApiResource<ResumeAnalysisSummary[]>(loadAnalysisHistory, [], []);
  // Id of the past run currently being opened, so its row can show a spinner. Null when none.
  const [openingRunId, setOpeningRunId] = useState<string | null>(null);

  // Learning Recommendations (Phase D §9): one per still-pending roadmap skill gap, each with curated
  // resources and its related roadmap milestone. Server-derived; empty list → the section's empty copy.
  const {
    data: learning,
    loading: learningLoading,
    error: learningError,
    reload: reloadLearning,
  } = useApiResource<LearningRecommendation[]>(loadLearningRecommendations, [], []);

  // Real resume file upload -> server-side parse -> per-resume analysis + evidence extraction.
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [extraction, setExtraction] = useState<ResumeExtractionResult | null>(null);
  // The per-resume analysis of the most recently uploaded file. When present it drives the deep
  // sections; before any upload the panel shows the stored summary (`data`). Nothing is persisted.
  const [analyzed, setAnalyzed] = useState<ResumeAnalysis | null>(null);
  const [addingSkills, setAddingSkills] = useState(false);
  // Names of project suggestions currently being added to the roadmap (per-button spinner) and those
  // that have been added this session (so the button reflects success without a full refetch here).
  const [addingProject, setAddingProject] = useState<string | null>(null);
  const [addedProjects, setAddedProjects] = useState<Record<string, boolean>>({});

  const onAddToRoadmap = async (p: {
    name: string;
    description: string;
    addressesGap: string;
    skillsDemonstrated: string[];
    difficulty: string;
  }) => {
    setAddingProject(p.name);
    try {
      await addRoadmapProject({
        name: p.name,
        description: p.description,
        addressesGap: p.addressesGap,
        skillsDemonstrated: p.skillsDemonstrated,
        difficulty: p.difficulty,
      });
      setAddedProjects((prev) => ({ ...prev, [p.name]: true }));
      pushToast("Added to roadmap", `"${p.name}" is now a tracked Portfolio Project.`);
      // The gap this project closes now has a matching roadmap project — refresh recs so its
      // `alreadyAdded` flips without a full page reload.
      reloadLearning();
    } catch (e) {
      pushToast("Couldn't add to roadmap", e instanceof Error ? e.message : "Please try again.");
    } finally {
      setAddingProject(null);
    }
  };

  const onFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    // Reset the input so re-selecting the same file fires change again.
    e.target.value = "";
    if (!file) return;
    setAnalyzing(true);
    setUploadError(null);
    setExtraction(null);
    try {
      // One upload drives both: the resume-specific intelligence panel (score/weaknesses/
      // suggestions/recommendation, derived from THIS resume) and the evidence-bearing skill
      // extraction. Both are computed server-side; nothing is stored.
      const [intelligence, result] = await Promise.all([
        analyzeResumeFile(file),
        extractResumeFile(file),
      ]);
      setAnalyzed(intelligence);
      setExtraction(result);
      setRevealKey((k) => k + 1);
      pushToast("Resume analyzed", `Scored ${intelligence.overallScore}/100 with ${result.skills.length} skill(s) found.`);
      // A new run was persisted server-side — refresh the history list so it appears immediately.
      reloadHistory();
    } catch (err) {
      setUploadError(
        err instanceof Error ? err.message : "Could not analyze that file. Use a PDF or text resume under 10 MB.",
      );
    } finally {
      setAnalyzing(false);
    }
  };

  const addAllToSkills = async () => {    if (!extraction || extraction.skills.length === 0) return;
    setAddingSkills(true);
    try {
      for (const item of extraction.skills) {
        await upsertSkill({ skill: item.normalizedValue, proficiency: 60, source: "RESUME" });
      }
      pushToast("Added to My Skills", `${extraction.skills.length} skill(s) added at 60%. Adjust them in My Skills.`);
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : "Could not add skills. Please try again.");
    } finally {
      setAddingSkills(false);
    }
  };

  // Opens a past run: fetches its full stored analysis and drives every deep section from it, exactly
  // as a fresh upload would. The extraction panel is cleared — it reflects a live upload, not history.
  const openPastAnalysis = async (id: string) => {
    setOpeningRunId(id);
    setUploadError(null);
    try {
      const detail = await getResumeAnalysisDetail(id);
      setAnalyzed(detail.analysis);
      setExtraction(null);
      setRevealKey((k) => k + 1);
      if (typeof window !== "undefined") window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : "Could not open that analysis. Please try again.");
    } finally {
      setOpeningRunId(null);
    }
  };

  // The Resume Builder hands off a freshly-analyzed built résumé via the store. Consume it once on
  // mount: surface it as the active analysis, refresh history so the new run appears, then clear it
  // so a later revisit/refresh doesn't re-show a stale result.
  useEffect(() => {
    if (!pendingResumeAnalysis) return;
    setAnalyzed(pendingResumeAnalysis);
    setRevealKey((k) => k + 1);
    reloadHistory();
    pushToast("Resume analyzed", `Scored ${pendingResumeAnalysis.overallScore}/100 from your built résumé.`);
    setPendingResumeAnalysis(null);
    if (typeof window !== "undefined") window.scrollTo({ top: 0, behavior: "smooth" });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingResumeAnalysis]);

  // Base summary values, preferring the fresh per-resume analysis when present, otherwise the
  // stored summary. `data` is non-null wherever this renders (AsyncBoundary gates the empty case).
  const score = analyzed?.overallScore ?? data?.score ?? 0;
  const recommendation = analyzed?.recommendation ?? data?.recommendation ?? "";
  const missingKeywords = analyzed?.missingKeywords ?? data?.missingKeywords ?? [];
  const strengths = analyzed?.strengths ?? data?.strengths ?? [];

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!data}
      onRetry={reload}
      loadingFallback={<ResumeSkeleton />}
      emptyTitle="No student profile"
      emptyMessage="Your student profile isn't available yet. Once your profile is registered, your resume intelligence will appear here."
      className="mx-auto max-w-5xl"
    >
      {data && (
        <div className="mx-auto max-w-5xl">
          <PageHeader
            eyebrow="Growth"
            title="Resume Intelligence"
            subtitle="How your resume reads against your target role, and what to change first."
            actions={
              <div className="flex items-center gap-2">
                <Button asChild variant="outline">
                  <Link href="/student/resume/builder">
                    <FilePenLine className="h-4 w-4 mr-1.5" /> Resume Builder
                  </Link>
                </Button>
                <Button
                  variant="glow"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={analyzing}
                >
                  {analyzing ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" /> Analyzing…
                    </>
                  ) : (
                    <>
                      <FileSearch className="h-4 w-4" /> {analyzed ? "Re-analyze Resume" : "Analyze Resume"}
                    </>
                  )}
                </Button>
              </div>
            }
          />

          <IntelligencePulse active={analyzing}>
            <Card className="p-6">
              {analyzing ? (
                <div className="flex flex-col items-center gap-6 py-10">
                  <IntelligencePulseBadge label="Analyzing Resume" />
                  <p className="text-sm text-muted-foreground">Reading your resume and scoring it against your target role…</p>
                </div>
              ) : (
                <motion.div
                  key={revealKey}
                  initial="hidden"
                  animate="visible"
                  variants={showcaseEntrance}
                  className="grid gap-6 md:grid-cols-[auto_1fr]"
                >
                  <div className="flex flex-col items-center gap-2">
                    <ScoreRing
                      value={score}
                      size={156}
                      strokeWidth={12}
                      label="Resume Score"
                      sublabel="out of 100"
                      animateKey={revealKey}
                    />
                  </div>
                  <div>
                    <div className="rounded-lg border border-violet/30 bg-violet/5 p-4">
                      <p className="flex items-center gap-1.5 text-xs font-semibold text-violet-bright">
                        <Sparkles className="h-3.5 w-3.5" /> Recommendation
                      </p>
                      <p className="mt-1.5 text-sm text-foreground">{recommendation}</p>
                    </div>
                    {missingKeywords.length > 0 && (
                      <>
                        <p className="mb-2 mt-4 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                          <Tag className="h-3.5 w-3.5" /> Missing Keywords
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {missingKeywords.map((k) => (
                            <Badge key={k} variant="risk">
                              {k}
                            </Badge>
                          ))}
                        </div>
                      </>
                    )}
                    {analyzed && analyzed.metrics.length > 0 && (
                      <div className="mt-4 grid gap-3 sm:grid-cols-2">
                        {analyzed.metrics.map((m) => (
                          <div key={m.key} className="rounded-lg border border-border p-3" title={m.basis}>
                            <div className="mb-1.5 flex items-center justify-between text-xs">
                              <span className="font-medium text-foreground">{m.label}</span>
                              <span className="tabular-nums text-muted-foreground">{m.value}%</span>
                            </div>
                            <Progress value={m.value} />
                            <p className="mt-1.5 text-[11px] leading-snug text-muted-foreground">{m.basis}</p>
                          </div>
                        ))}
                      </div>
                    )}
                    {analyzed ? (
                      <motion.p
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="mt-4 flex items-center gap-1.5 text-xs text-success"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" /> Analysis of your uploaded resume
                      </motion.p>
                    ) : (
                      <p className="mt-4 text-xs text-muted-foreground">
                        Upload a resume below to get weaknesses and project suggestions tailored to it.
                      </p>
                    )}
                  </div>
                </motion.div>
              )}
            </Card>
          </IntelligencePulse>

          <DepthCard className="mt-5 p-5">
            <div className="mb-2 flex items-center gap-2">
              <Upload className="h-4 w-4 text-violet-bright" />
              <CardTitle>Analyze a Resume File</CardTitle>
            </div>
            <p className="mb-4 text-xs text-muted-foreground">
              Upload a PDF or text resume. We read it server-side and extract your skills and
              qualifications — each with the exact line it came from. Your resume file is processed
              in-memory and not stored; the analysis result is saved to your history so you can track
              progress over time.
            </p>

            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.txt,application/pdf,text/plain"
              className="hidden"
              onChange={onFileChange}
            />
            <Button variant="glow" onClick={() => fileInputRef.current?.click()} disabled={analyzing}>
              {analyzing ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" /> Analyzing…
                </>
              ) : (
                <>
                  <Upload className="h-4 w-4" /> Upload resume
                </>
              )}
            </Button>

            {uploadError && (
              <p className="mt-3 text-xs text-risk" role="alert">
                {uploadError}
              </p>
            )}

            {extraction && (
              <div className="mt-5 space-y-4">
                <div>
                  <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      <Tag className="h-3.5 w-3.5" /> Extracted Skills
                    </p>
                    {extraction.skills.length > 0 && (
                      <Button size="sm" variant="outline" onClick={addAllToSkills} disabled={addingSkills}>
                        <Plus className="h-3.5 w-3.5" /> {addingSkills ? "Adding…" : "Add all to My Skills"}
                      </Button>
                    )}
                  </div>
                  {extraction.skills.length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                      {extraction.skills.map((item) => (
                        <Badge key={item.normalizedValue} variant="success" title={item.evidence}>
                          {item.normalizedValue}
                        </Badge>
                      ))}
                    </div>
                  ) : (
                    <p className="text-sm text-muted-foreground">No skills detected in this file.</p>
                  )}
                </div>

                {extraction.qualifications.length > 0 && (
                  <div>
                    <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      <CheckCircle2 className="h-3.5 w-3.5" /> Qualifications
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {extraction.qualifications.map((item) => (
                        <Badge key={item.normalizedValue} variant="muted" title={item.evidence}>
                          {item.normalizedValue}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}

                <p className="text-xs text-muted-foreground">
                  Extracted by <span className="font-medium text-foreground">{extraction.metadata.provider}</span>. Each
                  badge shows its source line on hover — review before adding.
                </p>
              </div>
            )}
          </DepthCard>

          <DataReveal stagger className="mt-5 grid gap-5 md:grid-cols-2">
            <DataRevealItem>
              <DepthCard className="h-full p-5">
                <div className="mb-3 flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4 text-success" />
                  <CardTitle>Strengths</CardTitle>
                </div>
                <ul className="space-y-2">
                  {strengths.map((w) => (
                    <li key={w} className="flex gap-2 text-sm text-muted-foreground">
                      <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" /> {w}
                    </li>
                  ))}
                </ul>
              </DepthCard>
            </DataRevealItem>
            <DataRevealItem>
              <DepthCard className="h-full p-5">
                <div className="mb-3 flex items-center gap-2">
                  <AlertTriangle className="h-4 w-4 text-warning" />
                  <CardTitle>Weaknesses</CardTitle>
                </div>
                {analyzed ? (
                  <ul className="space-y-3">
                    {analyzed.weaknesses.map((w) => (
                      <li key={w.gap} className="rounded-lg border border-border p-3">
                        <div className="flex items-start justify-between gap-2">
                          <p className="text-sm font-medium text-foreground">{w.gap}</p>
                          <Badge
                            variant={w.severity === "HIGH" ? "risk" : w.severity === "MEDIUM" ? "warning" : "muted"}
                          >
                            {w.severity}
                          </Badge>
                        </div>
                        {w.evidence && (
                          <p className="mt-1.5 text-xs text-muted-foreground">
                            <span className="font-medium text-foreground/80">Evidence:</span> {w.evidence}
                          </p>
                        )}
                        {w.whyItMatters && (
                          <p className="mt-1 text-xs text-muted-foreground">
                            <span className="font-medium text-foreground/80">Why it matters:</span> {w.whyItMatters}
                          </p>
                        )}
                        {w.recommendedAction && (
                          <p className="mt-1 flex items-start gap-1.5 text-xs text-violet-bright">
                            <Lightbulb className="mt-0.5 h-3 w-3 shrink-0" /> {w.recommendedAction}
                          </p>
                        )}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <ul className="space-y-2">
                    {(data?.weaknesses ?? []).map((w) => (
                      <li key={w} className="flex gap-2 text-sm text-muted-foreground">
                        <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" /> {w}
                      </li>
                    ))}
                    {(data?.weaknesses ?? []).length === 0 && (
                      <li className="text-sm text-muted-foreground">
                        Upload a resume to see grounded weaknesses with evidence and severity.
                      </li>
                    )}
                  </ul>
                )}
              </DepthCard>
            </DataRevealItem>
          </DataReveal>

          <DepthCard className="mt-5 p-5">
            <div className="mb-3 flex items-center gap-2">
              <Lightbulb className="h-4 w-4 text-violet-bright" />
              <CardTitle>Project Suggestions</CardTitle>
            </div>
            {analyzed ? (
              <div className="space-y-3">
                {analyzed.projectSuggestions.map((p) => (
                  <div key={p.name} className="rounded-lg border border-border p-3">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="text-sm font-medium text-foreground">{p.name}</p>
                        <p className="mt-0.5 text-sm text-muted-foreground">{p.description}</p>
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-1.5">
                        <Badge variant="muted">{p.difficulty}</Badge>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={addedProjects[p.name] || addingProject === p.name}
                          onClick={() => onAddToRoadmap(p)}
                        >
                          {addingProject === p.name
                            ? "Adding…"
                            : addedProjects[p.name]
                              ? "Added"
                              : "Add to Roadmap"}
                        </Button>
                      </div>
                    </div>
                    {p.skillsDemonstrated.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {p.skillsDemonstrated.map((s) => (
                          <Badge key={s} variant="success">
                            {s}
                          </Badge>
                        ))}
                      </div>
                    )}
                    <p className="mt-2 text-xs text-muted-foreground">
                      <span className="font-medium text-foreground/80">Addresses:</span> {p.addressesGap}
                      {p.outcome ? ` · ${p.outcome}` : ""}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <div className="space-y-2.5">
                {(data?.projectSuggestions ?? []).map((p) => (
                  <div
                    key={p}
                    className="flex items-center justify-between gap-3 rounded-lg border border-border p-3"
                  >
                    <p className="text-sm text-muted-foreground">{p}</p>
                  </div>
                ))}
                {(data?.projectSuggestions ?? []).length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    Upload a resume to get project suggestions tailored to your detected gaps.
                  </p>
                )}
              </div>
            )}
          </DepthCard>

          {analyzed && analyzed.skillMatch.length > 0 && (
            <DepthCard className="mt-5 p-5">
              <div className="mb-3 flex items-center gap-2">
                <Target className="h-4 w-4 text-violet-bright" />
                <CardTitle>Target Role Skill Match</CardTitle>
              </div>
              <p className="mb-3 text-xs text-muted-foreground">
                Skills expected for your target role vs. the evidence in your resume. A skill is only
                marked demonstrated when the resume actually shows it — a role requiring it is not proof.
              </p>
              <div className="space-y-1.5">
                {analyzed.skillMatch.map((sm) => (
                  <div
                    key={sm.requiredSkill}
                    className="flex items-center justify-between gap-3 rounded-lg border border-border px-3 py-2"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-foreground">{sm.requiredSkill}</p>
                      <p className="truncate text-xs text-muted-foreground" title={sm.resumeEvidence}>
                        {sm.resumeEvidence}
                      </p>
                    </div>
                    <Badge
                      variant={
                        sm.status === "DEMONSTRATED" ? "success" : sm.status === "PARTIAL" ? "warning" : "risk"
                      }
                    >
                      {sm.status === "DEMONSTRATED"
                        ? "Demonstrated"
                        : sm.status === "PARTIAL"
                          ? "Partial"
                          : "Not shown"}
                    </Badge>
                  </div>
                ))}
              </div>
            </DepthCard>
          )}

          {analyzed && analyzed.extracted.length > 0 && (
            <DepthCard className="mt-5 p-5">
              <div className="mb-3 flex items-center gap-2">
                <Tag className="h-4 w-4 text-violet-bright" />
                <CardTitle>Extracted Information</CardTitle>
              </div>
              <div className="space-y-4">
                {analyzed.extracted.map((cat) => (
                  <div key={cat.category}>
                    <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      {cat.category}
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {cat.items.map((it) => (
                        <Badge key={it.value} variant="muted" title={it.evidence}>
                          {it.value}
                        </Badge>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs text-muted-foreground">
                Each badge shows its source line on hover — grounded in your resume text.
              </p>
            </DepthCard>
          )}

          {analyzed &&
            (analyzed.actionPlan.now.length > 0 ||
              analyzed.actionPlan.next.length > 0 ||
              analyzed.actionPlan.later.length > 0) && (
              <DepthCard className="mt-5 p-5">
                <div className="mb-3 flex items-center gap-2">
                  <ListChecks className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Action Plan</CardTitle>
                </div>
                <div className="grid gap-4 md:grid-cols-3">
                  {(
                    [
                      ["Now", analyzed.actionPlan.now],
                      ["Next", analyzed.actionPlan.next],
                      ["Later", analyzed.actionPlan.later],
                    ] as const
                  ).map(([label, items]) => (
                    <div key={label} className="rounded-lg border border-border p-3">
                      <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                        <ChevronDown className="h-3.5 w-3.5" /> {label}
                      </p>
                      <ul className="space-y-1.5">
                        {items.length > 0 ? (
                          items.map((it) => (
                            <li key={it} className="text-sm text-muted-foreground">
                              {it}
                            </li>
                          ))
                        ) : (
                          <li className="text-sm text-muted-foreground/60">—</li>
                        )}
                      </ul>
                    </div>
                  ))}
                </div>
              </DepthCard>
            )}

          {analyzed?.resumeQuality && (
            <DepthCard className="mt-5 p-5">
              <div className="mb-2 flex items-center gap-2">
                <FileSearch className="h-4 w-4 text-violet-bright" />
                <CardTitle>Resume Quality</CardTitle>
              </div>
              <p className="mb-4 text-xs text-muted-foreground">
                A grounded, dimension-by-dimension read of your resume — not another score. Every point
                cites your resume or states plainly what is missing. A skill that isn&apos;t shown is
                reported as &ldquo;not demonstrated in the resume&rdquo;, never as a judgment that you
                don&apos;t know it.
              </p>

              <div className="grid gap-3 md:grid-cols-2">
                {QUALITY_DIMENSIONS.map(([key, label]) => (
                  <QualityDimensionBlock
                    key={key}
                    label={label}
                    dimension={analyzed.resumeQuality![key] as ResumeQualityDimension}
                  />
                ))}
              </div>

              {analyzed.resumeQuality.topImprovements.length > 0 && (
                <div className="mt-5">
                  <div className="mb-2 flex items-center gap-2">
                    <ListChecks className="h-4 w-4 text-violet-bright" />
                    <p className="text-sm font-semibold text-foreground">Top Improvements</p>
                  </div>
                  <ol className="space-y-2">
                    {analyzed.resumeQuality.topImprovements.map((f, i) => (
                      <li key={`top-${i}`} className="flex gap-3 rounded-lg border border-border p-3">
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-violet-bright/15 text-xs font-semibold text-violet-bright">
                          {i + 1}
                        </span>
                        <div>
                          <div className="mb-1 flex flex-wrap items-center gap-2">
                            <Badge variant={severityBadgeVariant(f.severity)}>{f.severity}</Badge>
                            <span className="text-sm font-medium text-foreground">{f.issue}</span>
                          </div>
                          {f.recommendation && (
                            <p className="text-xs text-muted-foreground">{f.recommendation}</p>
                          )}
                        </div>
                      </li>
                    ))}
                  </ol>
                </div>
              )}
            </DepthCard>
          )}

          <DepthCard className="mt-5 p-5">
            <div className="mb-2 flex items-center gap-2">
              <BookOpen className="h-4 w-4 text-violet-bright" />
              <CardTitle>Learning Recommendations</CardTitle>
            </div>
            <p className="mb-4 text-xs text-muted-foreground">
              One recommendation per still-pending skill gap on your Career Roadmap. Resources are drawn
              from a curated, human-reviewed catalog — never invented. Skills you already demonstrate are
              excluded.
            </p>

            {learningLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-24 w-full" />
                <Skeleton className="h-24 w-full" />
              </div>
            ) : learningError ? (
              <div className="flex items-center justify-between gap-3 rounded-lg border border-risk/30 bg-risk/5 p-3">
                <p className="text-sm text-risk">Could not load your learning recommendations.</p>
                <Button size="sm" variant="outline" onClick={reloadLearning}>
                  Retry
                </Button>
              </div>
            ) : (learning ?? []).length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No pending skill gaps right now — every skill on your roadmap is either demonstrated or
                already complete. Analyze a resume or add a target role to surface new gaps.
              </p>
            ) : (
              <div className="space-y-4">
                {(learning ?? []).map((rec) => {
                  const added = rec.alreadyAdded || (rec.addToRoadmap ? addedProjects[rec.addToRoadmap.name] : false);
                  return (
                    <div key={`${rec.relatedMilestone.taskId}-${rec.skill}`} className="rounded-xl border border-border p-4">
                      <div className="mb-2 flex flex-wrap items-center gap-2">
                        <GraduationCap className="h-4 w-4 text-violet-bright" />
                        <span className="text-sm font-semibold text-foreground">{rec.skill}</span>
                        <Badge variant="warning">Gap: {rec.gap}</Badge>
                      </div>
                      <p className="mb-3 text-sm text-muted-foreground">{rec.whyLearn}</p>

                      {rec.learningSequence.length > 0 && (
                        <div className="mb-3">
                          <p className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                            Recommended sequence
                          </p>
                          <ol className="list-decimal space-y-1 pl-5">
                            {rec.learningSequence.map((step, i) => (
                              <li key={i} className="text-sm text-muted-foreground">
                                {step}
                              </li>
                            ))}
                          </ol>
                        </div>
                      )}

                      <div className="mb-3">
                        <p className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                          Curated resources
                        </p>
                        {(() => {
                          const safeResources = rec.resources.filter((r) => isSafeHttpUrl(r.url));
                          return rec.hasCuratedResource && safeResources.length > 0 ? (
                            <div className="grid gap-2 sm:grid-cols-2">
                              {safeResources.map((r) => (
                                <a
                                  key={r.url}
                                  href={r.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="group flex flex-col rounded-lg border border-border p-3 transition-colors"
                                >
                                  <div className="mb-1 flex items-start justify-between gap-2">
                                    <span className="text-sm font-medium text-foreground group-hover:text-violet-bright">
                                      {r.title}
                                    </span>
                                    <ExternalLink className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                                  </div>
                                  <p className="mb-1.5 text-xs text-muted-foreground">{r.description}</p>
                                  <div className="mt-auto flex flex-wrap items-center gap-1.5">
                                    <Badge variant="muted">{r.provider}</Badge>
                                    <Badge variant="default">{r.type}</Badge>
                                    <Badge variant="muted">{r.difficulty}</Badge>
                                    {r.estimatedEffort && <Badge variant="muted">{r.estimatedEffort}</Badge>}
                                  </div>
                                </a>
                              ))}
                            </div>
                          ) : (
                            <p className="rounded-lg border border-dashed border-border px-3 py-2 text-sm text-muted-foreground/70">
                              No curated resource available yet.
                            </p>
                          );
                        })()}
                      </div>

                      {(() => {
                        const safeVideos = (rec.tutorialVideos ?? []).filter((v) => isSafeHttpUrl(v.url));
                        return safeVideos.length > 0 ? (
                          <div className="mb-3">
                            <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                              <PlayCircle className="h-3.5 w-3.5 text-red-500" /> Video tutorials
                            </p>
                            <div className="grid gap-2 sm:grid-cols-2">
                              {safeVideos.map((v) => (
                                <a
                                  key={v.url}
                                  href={v.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="group flex items-start justify-between gap-2 rounded-lg border border-border p-3 transition-colors"
                                >
                                  <div className="min-w-0">
                                    <span className="block truncate text-sm font-medium text-foreground group-hover:text-violet-bright">
                                      {v.title}
                                    </span>
                                    {v.channel && (
                                      <span className="block truncate text-xs text-muted-foreground">{v.channel}</span>
                                    )}
                                  </div>
                                  <ExternalLink className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                                </a>
                              ))}
                            </div>
                          </div>
                        ) : null;
                      })()}

                      <div className="mb-3 flex flex-wrap gap-x-6 gap-y-1.5 text-xs text-muted-foreground">
                        <span>
                          <span className="font-semibold text-foreground/80">Practice:</span> {rec.practiceTask}
                        </span>
                        <span>
                          <span className="font-semibold text-foreground/80">Milestone:</span>{" "}
                          {rec.relatedMilestone.phaseTitle} → {rec.relatedMilestone.taskTitle}
                        </span>
                      </div>

                      {rec.addToRoadmap && (
                        <Button
                          size="sm"
                          variant={added ? "ghost" : "outline"}
                          disabled={added || addingProject === rec.addToRoadmap.name}
                          onClick={() => rec.addToRoadmap && onAddToRoadmap(rec.addToRoadmap)}
                        >
                          {addingProject === rec.addToRoadmap.name ? (
                            <>
                              <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> Adding…
                            </>
                          ) : added ? (
                            <>
                              <CheckCircle2 className="mr-1.5 h-3.5 w-3.5 text-success" /> Added to roadmap
                            </>
                          ) : (
                            <>
                              <Plus className="mr-1.5 h-3.5 w-3.5" /> Add to roadmap
                            </>
                          )}
                        </Button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </DepthCard>

          <DepthCard className="mt-5 p-5">
            <div className="mb-2 flex items-center gap-2">
              <History className="h-4 w-4 text-violet-bright" />
              <CardTitle>Analysis History</CardTitle>
            </div>
            <p className="mb-4 text-xs text-muted-foreground">
              Every resume you analyze is saved here so you can revisit a past run and track how your
              score changes over time. Open any run to reload its full analysis above.
            </p>

            {historyLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : historyError ? (
              <div className="flex items-center justify-between gap-3 rounded-lg border border-risk/30 bg-risk/5 p-3">
                <p className="text-sm text-risk">Could not load your analysis history.</p>
                <Button size="sm" variant="outline" onClick={reloadHistory}>
                  Retry
                </Button>
              </div>
            ) : (history ?? []).length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No analyses yet. Upload a resume above and your runs will appear here.
              </p>
            ) : (
              <>
                {(() => {
                  const runs = history ?? [];
                  const latest = runs[0].overallScore;
                  const earliest = runs[runs.length - 1].overallScore;
                  const delta = latest - earliest;
                  return runs.length > 1 ? (
                    <p className="mb-3 text-xs text-muted-foreground">
                      {delta > 0 ? (
                        <span className="font-medium text-success">▲ Up {delta} point(s)</span>
                      ) : delta < 0 ? (
                        <span className="font-medium text-risk">▼ Down {Math.abs(delta)} point(s)</span>
                      ) : (
                        <span className="font-medium text-foreground/80">No change</span>
                      )}{" "}
                      since your first analysis ({earliest} → {latest}).
                    </p>
                  ) : null;
                })()}
                <div className="space-y-1.5">
                  {(history ?? []).map((run) => (
                    <button
                      key={run.id}
                      type="button"
                      onClick={() => openPastAnalysis(run.id)}
                      disabled={openingRunId !== null}
                      className="flex w-full items-center justify-between gap-3 rounded-lg border border-border px-3 py-2 text-left transition-colors disabled:opacity-60"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-foreground">
                          {run.resumeName ?? "Resume"}
                        </p>
                        <p className="flex items-center gap-1 text-xs text-muted-foreground">
                          <Clock className="h-3 w-3" /> {formatRunDate(run.createdAt)}
                        </p>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <Badge
                          variant={
                            run.overallScore >= 80 ? "success" : run.overallScore >= 60 ? "warning" : "risk"
                          }
                        >
                          {run.overallScore}/100
                        </Badge>
                        {openingRunId === run.id && <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />}
                      </div>
                    </button>
                  ))}
                </div>
              </>
            )}
          </DepthCard>
        </div>
      )}
    </AsyncBoundary>
  );
}
