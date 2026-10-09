"use client";

import { useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  Sparkles,
  CheckCircle2,
  XCircle,
  Zap,
  ArrowLeft,
  ListChecks,
  ShieldCheck,
  ShieldX,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { PageHeader } from "@/components/layout/PageHeader";
import { MatchBreakdownBars } from "@/components/intelligence/MatchBreakdown";
import { EligibilityChecklist } from "@/components/intelligence/EligibilityChecklist";
import { StagedRunner } from "@/components/intelligence/StagedRunner";
import {
  IntelligencePulse,
  IntelligencePulseBadge,
} from "@/components/intelligence/IntelligencePulse";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { GlowLine } from "@/components/intelligence/GlowLine";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { showcaseEntrance } from "@/lib/motion-variants";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { listDrivesByCompany } from "@/lib/api/drives";
import { listMatchesForDrive } from "@/lib/api/matches";
import { listCandidateRecommendations } from "@/lib/api/recommendations";
import { getStudent } from "@/lib/api/students";
import { candidateAnalysisSteps } from "@/lib/simulate";
import { evaluateEligibility } from "@/lib/eligibility";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";
import type {
  CandidateMatch,
  CandidateRecommendation,
  Drive,
  RecommendationType,
  Student,
} from "@/types";

type RunState = "idle" | "running" | "done";

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

interface CandidateData {
  student: Student;
  drive: Drive;
  match: CandidateMatch;
  recommendation: CandidateRecommendation | null;
}

/**
 * Resolve one candidate against the signed-in recruiter's headline drive, entirely live: their
 * company (GET /recruiters/me), that company's headline drive (first ACTIVE, else first), the
 * student (GET /students/:id) and the real ranked match for that student on that drive (GET
 * /drives/:id/matches). The match carries its own whySelected / weakOrMissing / recommendation —
 * no simulated analysis. The recommendation overlay (derived type + persisted AI explanation) comes
 * from GET /drives/:id/candidate-recommendations; it serves persisted text only, so `aiExplanation`
 * may be null and the UI falls back to the deterministic match recommendation. Returns null when the
 * recruiter has no drive or the student has no match.
 */
async function loadCandidate(studentId: string): Promise<CandidateData | null> {
  const recruiter = await getActiveRecruiter();
  const drives = await listDrivesByCompany(recruiter.id);
  const drive = drives.find((d) => d.status === "ACTIVE") ?? drives[0] ?? null;
  if (!drive) return null;

  const [student, matches, recommendations] = await Promise.all([
    getStudent(studentId),
    listMatchesForDrive(drive.id),
    listCandidateRecommendations(drive.id).catch(() => [] as CandidateRecommendation[]),
  ]);
  const match = matches.find((m) => m.studentId === studentId) ?? null;
  if (!student || !match) return null;

  const recommendation = recommendations.find((r) => r.studentId === studentId) ?? null;
  return { student, drive, match, recommendation };
}

function fitLabel(pct: number) {
  if (pct >= 88) return "STRONG MATCH";
  if (pct >= 78) return "GOOD MATCH";
  return "MODERATE MATCH";
}

/** Left/center/right matching network — Candidate → NEXPLOY Matching
 * Intelligence → Job/Recruiter — with connection lines that pulse while
 * a match analysis is running. Lightweight CSS/SVG, not a 3D scene. */
function MatchingNetwork({
  studentName,
  studentInitials,
  companyName,
  role,
  logoInitial,
  active,
}: {
  studentName: string;
  studentInitials: string;
  companyName: string;
  role: string;
  logoInitial: string;
  active: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-2 sm:gap-4">
      <div className="flex min-w-0 items-center gap-2.5">
        <Avatar className="h-10 w-10 shrink-0">
          <AvatarFallback>{studentInitials}</AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{studentName}</p>
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">Candidate</p>
        </div>
      </div>

      <GlowLine active={active} className="w-8 sm:w-16" />

      <div className="flex shrink-0 flex-col items-center gap-1.5">
        <div
          className={cn(
            "flex h-11 w-11 items-center justify-center rounded-full border border-violet/40 bg-violet/10",
            active && "shadow-glow-strong",
          )}
        >
          <Zap className="h-4 w-4 text-violet-bright" />
        </div>
        <span className="text-center text-[9px] font-semibold uppercase leading-tight tracking-wide text-violet-bright">
          NEXPLOY
          <br />
          Matching Intelligence
        </span>
      </div>

      <GlowLine active={active} className="w-8 sm:w-16" />

      <div className="flex min-w-0 items-center gap-2.5">
        <div className="min-w-0 text-right">
          <p className="truncate text-sm font-semibold">{companyName}</p>
          <p className="text-[10px] uppercase tracking-wide text-muted-foreground">{role}</p>
        </div>
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-xs font-bold text-violet-bright">
          {logoInitial}
        </div>
      </div>
    </div>
  );
}

export function CandidateMatchingPage({ studentId }: { studentId: string }) {
  const shortlisted = useAppStore((s) => s.shortlisted);
  const shortlistCandidates = useAppStore((s) => s.shortlistCandidates);
  const pushToast = useAppStore((s) => s.pushToast);

  const { data, loading, error, reload } = useApiResource<CandidateData | null>(
    () => loadCandidate(studentId),
    [studentId],
    null,
  );

  const [runState, setRunState] = useState<RunState>("idle");
  const [revealKey, setRevealKey] = useState(0);

  const student = data?.student;
  const drive = data?.drive;
  const match = data?.match;
  const recommendation = data?.recommendation ?? null;
  const eligibility = student && drive ? evaluateEligibility(student, drive) : null;

  const isShortlisted = student ? shortlisted.includes(student.id) : false;

  const shortlist = () => {
    if (!student || !drive) return;
    shortlistCandidates([student.id]);
    pushToast("Candidate shortlisted", `${student.name} advanced to the shortlist for ${drive.role}.`);
  };

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!student || !drive || !match || !eligibility}
      onRetry={reload}
      emptyTitle="Candidate not found"
      emptyMessage="This candidate has no ranked match against your current drive."
      className="mx-auto max-w-4xl"
    >
      {student && drive && match && eligibility && (
        <div className="mx-auto max-w-4xl">
          <Button asChild variant="ghost" size="sm" className="mb-3 -ml-2">
            <Link href="/recruiter/candidates">
              <ArrowLeft className="h-3.5 w-3.5" /> Back to candidate pool
            </Link>
          </Button>

          <PageHeader
            eyebrow={`Candidate for ${drive.companyName} — ${drive.role}`}
            title={student.name}
            subtitle={`${student.branch} · CGPA ${student.cgpa.toFixed(2)} · Readiness ${student.readiness}/100 · ${student.backlogs} active backlogs`}
            actions={
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  variant="glow"
                  onClick={() => setRunState("running")}
                  disabled={runState === "running"}
                >
                  <Zap className="h-4 w-4" /> Analyze Candidate
                </Button>
                <Button
                  variant="outline"
                  onClick={shortlist}
                  disabled={isShortlisted || !eligibility.eligible}
                >
                  <ListChecks className="h-4 w-4" />
                  {isShortlisted ? "Shortlisted" : "Shortlist"}
                </Button>
              </div>
            }
          />

          <DepthCard className="mb-5 p-5">
            <MatchingNetwork
              studentName={student.name}
              studentInitials={student.avatarInitials}
              companyName={drive.companyName}
              role={drive.role}
              logoInitial={drive.logoInitial}
              active={runState === "running"}
            />
          </DepthCard>

          <IntelligencePulse active={runState === "running"}>
            <Card className="p-6">
              {runState === "running" ? (
                <div className="flex flex-col items-center gap-6 py-6">
                  <IntelligencePulseBadge label="Generating Match Intelligence" />
                  <div className="w-full max-w-sm">
                    <StagedRunner
                      steps={candidateAnalysisSteps}
                      onDone={() => {
                        setRunState("done");
                        setRevealKey((k) => k + 1);
                      }}
                    />
                  </div>
                </div>
              ) : (
                <motion.div key={revealKey} initial="hidden" animate="visible" variants={showcaseEntrance}>
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                      <Avatar className="h-12 w-12">
                        <AvatarFallback>{student.avatarInitials}</AvatarFallback>
                      </Avatar>
                      <div>
                        <p className="text-sm font-semibold">{student.name}</p>
                        <p className="text-xs text-muted-foreground">
                          Matched against {drive.companyName} — {drive.role}
                        </p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-3xl font-semibold tabular-nums text-violet-bright">
                        {match.overallFit}%
                      </p>
                      <Badge variant={match.overallFit >= 88 ? "success" : "default"}>
                        {fitLabel(match.overallFit)}
                      </Badge>
                    </div>
                  </div>

                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <Badge variant={eligibility.eligible ? "success" : "risk"} className="gap-1">
                      {eligibility.eligible ? (
                        <ShieldCheck className="h-3 w-3" />
                      ) : (
                        <ShieldX className="h-3 w-3" />
                      )}
                      Eligibility {eligibility.eligible ? "PASS" : "FAIL"}
                    </Badge>
                    {isShortlisted && (
                      <Badge variant="success" className="gap-1">
                        <CheckCircle2 className="h-3 w-3" /> Shortlisted
                      </Badge>
                    )}
                  </div>

                  {runState === "done" && (
                    <motion.div
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      className="mt-3 flex items-center gap-1.5 text-xs text-success"
                    >
                      <Sparkles className="h-3.5 w-3.5" /> Match intelligence generated
                    </motion.div>
                  )}

                  <div className="mt-5">
                    <MatchBreakdownBars breakdown={match.breakdown} />
                  </div>
                </motion.div>
              )}
            </Card>
          </IntelligencePulse>

          <DepthCard className="mt-5 p-5">
            <CardTitle className="mb-1">Eligibility Check</CardTitle>
            <p className="mb-3 text-xs text-muted-foreground">
              Rule check against the published criteria — independent of the match score above.
            </p>
            <EligibilityChecklist student={student} drive={drive} />
          </DepthCard>

          <div className="mt-5 grid gap-5 md:grid-cols-2">
            <DepthCard className="p-5">
              <p className="mb-2.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-success">
                <CheckCircle2 className="h-3.5 w-3.5" /> Strengths
              </p>
              <ul className="space-y-2">
                {match.whySelected.map((w) => (
                  <li key={w} className="flex gap-2 text-sm text-muted-foreground">
                    <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" /> {w}
                  </li>
                ))}
              </ul>
            </DepthCard>
            <DepthCard className="p-5">
              <p className="mb-2.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-risk">
                <XCircle className="h-3.5 w-3.5" /> Gaps
              </p>
              <ul className="space-y-2">
                {match.weakOrMissing.map((w) => (
                  <li key={w} className="flex gap-2 text-sm text-muted-foreground">
                    <XCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-risk" /> {w}
                  </li>
                ))}
              </ul>
            </DepthCard>
          </div>

          <DepthCard className="mt-5 border-violet/30 bg-violet/5 p-5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-xs font-semibold uppercase tracking-wider text-violet-bright">
                Recommendation
              </p>
              {recommendation && (
                <div className="flex items-center gap-2">
                  <Badge variant={RECOMMENDATION_META[recommendation.recommendationType].tone}>
                    {RECOMMENDATION_META[recommendation.recommendationType].label}
                  </Badge>
                  {recommendation.explanationStatus === "FALLBACK" && (
                    <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
                      summary unavailable — showing evidence
                    </span>
                  )}
                </div>
              )}
            </div>
            <p className="mt-1.5 text-sm text-foreground">
              {recommendation?.aiExplanation ?? match.recommendation}
            </p>
          </DepthCard>
        </div>
      )}
    </AsyncBoundary>
  );
}
