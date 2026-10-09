"use client";

import Link from "next/link";
import { Compass, CheckCircle2, ShieldCheck, ShieldAlert, ArrowRight, Sparkles } from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/PageHeader";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { useApiResource } from "@/hooks/useApiResource";
import { getCareerRecommendations, type CareerRecommendation } from "@/lib/api/students";
import { cn } from "@/lib/utils";

/**
 * Loads the signed-in student's server-ranked career matches. A 404 means there is no student
 * profile yet → null → the "no profile" empty state (never fabricated). An empty array means the
 * profile exists but the student's campus has no open drives to match — a valid, honest empty list.
 */
async function loadCareerRecommendations(): Promise<CareerRecommendation[] | null> {
  try {
    return await getCareerRecommendations();
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status: number }).status === 404) {
      return null;
    }
    throw error;
  }
}

function CareerSkeleton() {
  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-6 space-y-2.5">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-7 w-72" />
        <Skeleton className="h-4 w-96" />
      </div>
      <div className="space-y-4">
        <Skeleton className="h-[168px] w-full" />
        <Skeleton className="h-[168px] w-full" />
        <Skeleton className="h-[168px] w-full" />
      </div>
    </div>
  );
}

/** Colour the score by band so the strongest matches read at a glance. */
function scoreTone(score: number): string {
  if (score >= 70) return "text-success";
  if (score >= 40) return "text-violet-bright";
  return "text-muted-foreground";
}

function RecommendationCard({ rec }: { rec: CareerRecommendation }) {
  return (
    <DepthCard className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <CardTitle className="truncate">{rec.role}</CardTitle>
            {rec.eligible ? (
              <Badge variant="success" className="shrink-0">
                <ShieldCheck className="h-3 w-3" /> Eligible
              </Badge>
            ) : (
              <Badge variant="risk" className="shrink-0">
                <ShieldAlert className="h-3 w-3" /> Not yet eligible
              </Badge>
            )}
          </div>
          <p className="mt-0.5 text-sm text-muted-foreground">
            {rec.companyName}
            {rec.location ? ` · ${rec.location}` : ""}
            {rec.packageLpa != null ? ` · ₹${rec.packageLpa} LPA` : ""}
          </p>
        </div>
        <div className="flex shrink-0 flex-col items-center">
          <span className={cn("text-2xl font-semibold tabular-nums", scoreTone(rec.matchScore))}>
            {rec.matchScore}
            <span className="text-sm text-muted-foreground">%</span>
          </span>
          <span className="text-[10px] uppercase tracking-wider text-muted-foreground">Match</span>
        </div>
      </div>

      {rec.reasons.length > 0 && (
        <ul className="mt-3 space-y-1">
          {rec.reasons.map((reason) => (
            <li key={reason} className="flex items-start gap-1.5 text-xs text-muted-foreground">
              <Sparkles className="mt-0.5 h-3 w-3 shrink-0 text-violet-bright" /> {reason}
            </li>
          ))}
        </ul>
      )}

      {(rec.matchedSkills.length > 0 || rec.missingSkills.length > 0) && (
        <div className="mt-4 flex flex-wrap gap-1.5">
          {rec.matchedSkills.map((skill) => (
            <Badge key={`m-${skill}`} variant="success">
              <CheckCircle2 className="h-3 w-3" /> {skill}
            </Badge>
          ))}
          {rec.missingSkills.map((skill) => (
            <Badge key={`x-${skill}`} variant="risk">
              {skill}
            </Badge>
          ))}
        </div>
      )}

      <div className="mt-4">
        <Button asChild variant="outline" size="sm">
          <Link href={`/student/drives/${rec.driveId}`}>
            View drive <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </Button>
      </div>
    </DepthCard>
  );
}

export default function CareerMatchesPage() {
  const { data, loading, error, reload } = useApiResource<CareerRecommendation[] | null>(
    loadCareerRecommendations,
    [],
    null,
  );

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!data}
      onRetry={reload}
      loadingFallback={<CareerSkeleton />}
      emptyTitle="No student profile"
      emptyMessage="Your student profile isn't available yet. Once your profile is registered, your career matches will appear here."
      className="mx-auto max-w-5xl"
    >
      <div className="mx-auto max-w-5xl">
        <PageHeader
          eyebrow="Opportunities"
          title="Career Matches"
          subtitle="Open drives on your campus, ranked by how well they fit your skills, eligibility and role preferences."
        />

        {data && data.length > 0 ? (
          <DataReveal stagger className="space-y-4">
            {data.map((rec) => (
              <DataRevealItem key={rec.driveId}>
                <RecommendationCard rec={rec} />
              </DataRevealItem>
            ))}
          </DataReveal>
        ) : (
          <DepthCard className="flex flex-col items-center gap-3 p-12 text-center">
            <Compass className="h-8 w-8 text-muted-foreground" />
            <CardTitle>No open drives to match yet</CardTitle>
            <p className="max-w-md text-sm text-muted-foreground">
              There are no open placement drives on your campus right now. New drives will be ranked
              and shown here as soon as they open.
            </p>
          </DepthCard>
        )}
      </div>
    </AsyncBoundary>
  );
}
