"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { JourneyTimeline } from "@/components/intelligence/JourneyTimeline";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { listApplicationsByStudent, withdrawApplication } from "@/lib/api/applications";
import { listDrives } from "@/lib/api/drives";
import {
  getApplicationRecommendation,
  regenerateApplicationRecommendation,
} from "@/lib/api/recommendations";
import { getPrimaryStudent } from "@/lib/api/students";
import { applicationStageIndex, applicationStatusLabel, applicationStatusTone } from "@/lib/status";
import type { Application, Drive, Recommendation, RecommendationType } from "@/types";

/** Terminal application states a student can no longer withdraw from. */
const WITHDRAW_BLOCKED: Application["status"][] = ["rejected", "withdrawn", "joined", "selected"];

/** Display label + badge tone for each derived recommendation type. */
const RECOMMENDATION_META: Record<
  RecommendationType,
  { label: string; tone: "default" | "success" | "warning" | "risk" }
> = {
  SHORTLISTED: { label: "Shortlisted", tone: "success" },
  ELIGIBLE: { label: "Eligible", tone: "default" },
  NOT_SHORTLISTED: { label: "Not shortlisted", tone: "warning" },
  NOT_ELIGIBLE: { label: "Not eligible", tone: "risk" },
};

interface ApplicationsView {
  applications: Application[];
  drivesById: Record<string, Drive>;
}

/** Loads the signed-in student, their applications, and the drives to resolve company/role. */
async function loadApplications(): Promise<ApplicationsView> {
  const student = await getPrimaryStudent();
  const [applications, drives] = await Promise.all([
    listApplicationsByStudent(student.id),
    listDrives(),
  ]);
  const drivesById: Record<string, Drive> = {};
  for (const drive of drives) drivesById[drive.id] = drive;
  // "eligible" is a pre-apply state — this page tracks submitted applications only.
  return { applications: applications.filter((a) => a.status !== "eligible"), drivesById };
}

export default function ApplicationsPage() {
  const { data, loading, error, reload } = useApiResource<ApplicationsView>(loadApplications, [], {
    applications: [],
    drivesById: {},
  });
  const { applications, drivesById } = data;
  const pushToast = useAppStore((s) => s.pushToast);
  const [withdrawing, setWithdrawing] = useState<Application | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const confirmWithdraw = async () => {
    if (!withdrawing) return;
    setSubmitting(true);
    try {
      await withdrawApplication(withdrawing.id);
      pushToast("Application withdrawn", "You have withdrawn from this drive.");
      reload();
      setWithdrawing(null);
    } catch (err) {
      pushToast("Withdraw failed", err instanceof Error ? err.message : "Could not withdraw.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Opportunities"
        title="Applications"
        subtitle="Track where each application stands across the full placement lifecycle."
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={applications.length === 0}
        onRetry={reload}
        emptyTitle="No applications yet"
        emptyMessage="Apply to an eligible drive and it will appear here."
      >
        <div className="space-y-4">
          {applications.map((a) => {
            const drive = drivesById[a.driveId];
            if (!drive) return null;
            return (
              <DepthCard key={a.id} className="p-5">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold">
                      {drive.companyName}{" "}
                      <span className="font-normal text-muted-foreground">· {drive.role}</span>
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {a.appliedOn ? `Applied on ${a.appliedOn}` : "Not yet applied"}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={applicationStatusTone[a.status]}>
                      {applicationStatusLabel[a.status]}
                    </Badge>
                    <Link
                      href={`/student/drives/${drive.id}`}
                      className="text-violet-bright"
                      aria-label={`Open ${drive.companyName} drive`}
                    >
                      <ArrowUpRight className="h-4 w-4" />
                    </Link>
                  </div>
                </div>
                <div className="mt-4">
                  <JourneyTimeline
                    currentIndex={applicationStageIndex[a.status]}
                    halted={a.status === "rejected"}
                  />
                </div>
                <RecommendationPanel applicationId={a.id} />
                {!WITHDRAW_BLOCKED.includes(a.status) && (
                  <div className="mt-4 flex justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-risk"
                      onClick={() => setWithdrawing(a)}
                    >
                      Withdraw
                    </Button>
                  </div>
                )}
              </DepthCard>
            );
          })}
        </div>
      </AsyncBoundary>

      <Dialog open={!!withdrawing} onOpenChange={(open) => !open && setWithdrawing(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Withdraw application</DialogTitle>
            <DialogDescription>
              {withdrawing &&
                `Withdraw from ${drivesById[withdrawing.driveId]?.companyName ?? "this drive"}? This cannot be undone.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setWithdrawing(null)} disabled={submitting}>
              Cancel
            </Button>
            <Button
              variant="outline"
              className="text-risk"
              onClick={confirmWithdraw}
              disabled={submitting}
            >
              {submitting ? "Withdrawing…" : "Confirm Withdraw"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/**
 * Lazily-loaded "Why this recommendation" panel for one application. It fetches only when the
 * student expands it (so the list view never triggers a per-application generation), then shows the
 * derived type, the deterministic evidence, and the human-readable explanation. Regenerate forces a
 * fresh AI pass. All numbers/eligibility come from the backend — never fabricated here.
 */
function RecommendationPanel({ applicationId }: { applicationId: string }) {
  const [open, setOpen] = useState(false);
  const [rec, setRec] = useState<Recommendation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [regenerating, setRegenerating] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRec(await getApplicationRecommendation(applicationId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the recommendation.");
    } finally {
      setLoading(false);
    }
  };

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && !rec && !loading) void load();
  };

  const regenerate = async () => {
    setRegenerating(true);
    setError(null);
    try {
      setRec(await regenerateApplicationRecommendation(applicationId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not regenerate the explanation.");
    } finally {
      setRegenerating(false);
    }
  };

  return (
    <div className="mt-4 border-t border-border pt-3">
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        className="text-xs font-medium text-violet-bright hover:underline"
      >
        {open ? "Hide" : "Why this recommendation?"}
      </button>

      {open && (
        <div className="mt-3 space-y-3 text-sm">
          {loading && <p className="text-xs text-muted-foreground">Loading recommendation…</p>}
          {error && <p className="text-xs text-risk">{error}</p>}

          {rec && (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={RECOMMENDATION_META[rec.recommendationType].tone}>
                  {RECOMMENDATION_META[rec.recommendationType].label}
                </Badge>
                {rec.overallFit !== null && (
                  <span className="text-xs text-muted-foreground">Fit {rec.overallFit}/100</span>
                )}
                {rec.explanationStatus === "FALLBACK" && (
                  <span className="text-xs text-muted-foreground">(summary unavailable — showing evidence)</span>
                )}
              </div>

              {rec.aiExplanation ? (
                <p className="text-muted-foreground">{rec.aiExplanation}</p>
              ) : (
                <p className="text-xs text-muted-foreground">
                  No AI explanation yet — the evidence below is the basis for this recommendation.
                </p>
              )}

              {rec.whySelected.length > 0 && (
                <div>
                  <p className="text-xs font-semibold">Strengths</p>
                  <ul className="ml-4 list-disc text-xs text-muted-foreground">
                    {rec.whySelected.map((s, i) => (
                      <li key={i}>{s}</li>
                    ))}
                  </ul>
                </div>
              )}

              {(rec.improvementAreas.length > 0 || rec.weakOrMissing.length > 0) && (
                <div>
                  <p className="text-xs font-semibold">Areas to improve</p>
                  <ul className="ml-4 list-disc text-xs text-muted-foreground">
                    {(rec.improvementAreas.length > 0 ? rec.improvementAreas : rec.weakOrMissing).map(
                      (s, i) => (
                        <li key={i}>{s}</li>
                      ),
                    )}
                  </ul>
                </div>
              )}

              <div className="flex justify-end">
                <Button variant="outline" size="sm" onClick={regenerate} disabled={regenerating}>
                  {regenerating ? "Regenerating…" : "Regenerate"}
                </Button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
