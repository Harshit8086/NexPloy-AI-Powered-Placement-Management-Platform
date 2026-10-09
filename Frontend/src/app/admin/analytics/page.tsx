"use client";

import { BarChart3, TrendingUp, Users, Award, IndianRupee } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { VerticalBarChart } from "@/components/charts/VerticalBarChart";
import { HorizontalBarChart } from "@/components/charts/HorizontalBarChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getInstituteAnalytics, listAllCampusAnalytics } from "@/lib/api/analytics";
import { useApiResource } from "@/hooks/useApiResource";
import type { CampusAnalytics, InstituteAnalytics } from "@/types";

/** Parse a "₹7.4 LPA" style label to a number, or NaN when unavailable ("—"). */
const parseLpa = (label: string | undefined) => Number((label ?? "").replace(/[^\d.]/g, ""));

export default function CrossCampusAnalyticsPage() {
  // Live: institute rollup (totals + per-campus comparison) + each campus's full dashboard, so the
  // institute funnel/offer/salary charts are exact sums of the per-campus server aggregations.
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [institute, byCampus] = await Promise.all([
        getInstituteAnalytics().catch(() => null),
        listAllCampusAnalytics().catch(() => ({} as Record<string, CampusAnalytics>)),
      ]);
      return { institute, byCampus };
    },
    [],
    { institute: null as InstituteAnalytics | null, byCampus: {} as Record<string, CampusAnalytics> },
  );

  const institute = data.institute;
  const byCampus = data.byCampus;
  const campuses = institute?.campuses ?? [];
  const totals = institute?.totals;
  const analyticsList = campuses.map((c) => byCampus[c.campusId]).filter(Boolean) as CampusAnalytics[];

  // Institute funnel = sum of every campus funnel, so it reconciles with the per-campus views.
  const stageNames = analyticsList[0]?.funnel.map((f) => f.stage) ?? [];
  const instituteFunnel = stageNames.map((stage) => ({
    stage,
    count: analyticsList.reduce(
      (sum, a) => sum + (a.funnel.find((f) => f.stage === stage)?.count ?? 0),
      0,
    ),
  }));

  const months = analyticsList[0]?.offerPipeline.map((p) => p.month) ?? [];
  const offersByMonth = months.map((month) => ({
    month,
    offers: analyticsList.reduce(
      (sum, a) => sum + (a.offerPipeline.find((o) => o.month === month)?.offers ?? 0),
      0,
    ),
  }));

  const atRiskByCampus = campuses.map((c) => ({
    label: c.campus,
    value: byCampus[c.campusId]?.snapshot.atRisk ?? 0,
  }));

  const ranked = [...campuses].sort((a, b) => b.placementRate - a.placementRate);
  const bestCampus = ranked[0];
  const weakestCampus = ranked[ranked.length - 1];

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Campuses"
        title="Cross-Campus Analytics"
        subtitle="Institute-wide placement performance, compared campus by campus."
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!institute || campuses.length === 0}
        onRetry={reload}
        emptyTitle="No cross-campus data"
        emptyMessage="No campuses or placement data have been aggregated yet."
      >
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
          <StatCard label="Students" value={totals?.totalStudents ?? 0} icon={Users} />
          <StatCard label="Placed" value={totals?.placed ?? 0} icon={Award} accent="success" />
          <StatCard label="Offers" value={totals?.offers ?? 0} accent="success" />
          <StatCard
            label="Institute Rate"
            value={totals?.placementRate ?? 0}
            suffix="%"
            icon={TrendingUp}
            accent="violet"
          />
          <StatCard label="At Risk" value={totals?.atRisk ?? 0} accent="risk" />
        </div>

        <Card className="mt-5 overflow-x-auto p-5">
          <div className="mb-3 flex items-center justify-between">
            <CardTitle>Campus Comparison</CardTitle>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Campus</TableHead>
                <TableHead>Students</TableHead>
                <TableHead>Ready</TableHead>
                <TableHead>Placed</TableHead>
                <TableHead>Placement Rate</TableHead>
                <TableHead>Average CTC</TableHead>
                <TableHead>Standing</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {campuses.map((c) => (
                <TableRow key={c.campusId}>
                  <TableCell className="font-medium">{c.campus}</TableCell>
                  <TableCell className="tabular-nums">{c.students.toLocaleString()}</TableCell>
                  <TableCell className="tabular-nums">{c.ready.toLocaleString()}</TableCell>
                  <TableCell className="tabular-nums">{c.placed.toLocaleString()}</TableCell>
                  <TableCell className="tabular-nums text-violet-bright">{c.placementRate}%</TableCell>
                  <TableCell className="tabular-nums">{c.averageCtc}</TableCell>
                  <TableCell>
                    <Badge
                      variant={
                        c.placementRate >= 80 ? "success" : c.placementRate >= 74 ? "warning" : "risk"
                      }
                    >
                      {c.placementRate >= 80
                        ? "Leading"
                        : c.placementRate >= 74
                          ? "On track"
                          : "Needs attention"}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>

        <DataReveal stagger className="mt-5 grid gap-5 lg:grid-cols-2">
          <DataRevealItem>
            <DepthCard className="p-5">
              <div className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <BarChart3 className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Institute Placement Funnel</CardTitle>
                </div>
              </div>
              <VerticalBarChart data={instituteFunnel} xKey="stage" yKey="count" height={260} />
            </DepthCard>
          </DataRevealItem>
          <DataRevealItem>
            <DepthCard className="p-5">
              <div className="mb-3 flex items-center justify-between">
                <CardTitle>Offers by Month (All Campuses)</CardTitle>
              </div>
              <VerticalBarChart
                data={offersByMonth}
                xKey="month"
                yKey="offers"
                height={260}
                colorVar="--success"
              />
            </DepthCard>
          </DataRevealItem>
        </DataReveal>

        <DataReveal stagger className="mt-5 grid gap-5 lg:grid-cols-2">
          <DataRevealItem>
            <DepthCard className="h-full p-5">
              <div className="mb-3 flex items-center justify-between">
                <CardTitle>At-Risk Students by Campus</CardTitle>
              </div>
              <HorizontalBarChart
                data={atRiskByCampus}
                xKey="value"
                yKey="label"
                height={200}
                colorVar="--risk"
              />
            </DepthCard>
          </DataRevealItem>
          <DataRevealItem>
            <DepthCard className="h-full p-5">
              <div className="mb-3 flex items-center gap-2">
                <IndianRupee className="h-4 w-4 text-violet-bright" />
                <CardTitle>Salary Comparison</CardTitle>
              </div>
              <div className="space-y-4">
                {campuses.map((c) => {
                  const snap = byCampus[c.campusId]?.snapshot;
                  const avg = parseLpa(snap?.averageCtc ?? c.averageCtc);
                  const high = parseLpa(snap?.highestCtc);
                  return (
                    <div key={c.campusId}>
                      <div className="mb-1 flex items-center justify-between text-xs">
                        <span className="font-medium">{c.campus}</span>
                        <span className="text-muted-foreground">
                          avg {snap?.averageCtc ?? c.averageCtc}
                          {snap?.highestCtc ? ` · high ${snap.highestCtc}` : ""}
                        </span>
                      </div>
                      <SkillBar
                        label="Average CTC (of ₹15 LPA scale)"
                        value={Number.isFinite(avg) ? Math.round((avg / 15) * 100) : 0}
                        colorClass="bg-violet"
                      />
                      <div className="mt-1.5">
                        <SkillBar
                          label="Highest CTC (of ₹15 LPA scale)"
                          value={Number.isFinite(high) ? Math.round((high / 15) * 100) : 0}
                          colorClass="bg-success"
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </DepthCard>
          </DataRevealItem>
        </DataReveal>

        {bestCampus && weakestCampus && (
          <DepthCard className="mt-5 p-5">
            <CardTitle className="mb-2">Institute Read</CardTitle>
            <p className="text-sm text-muted-foreground">
              <span className="font-medium text-foreground">{bestCampus.campus}</span> leads at{" "}
              {bestCampus.placementRate}% placement with an average of {bestCampus.averageCtc}.{" "}
              <span className="font-medium text-foreground">{weakestCampus.campus}</span> trails at{" "}
              {weakestCampus.placementRate}% — the widest gap between placement-ready students and
              students actually placed sits there, which is where mentor capacity and drive coverage
              are worth reviewing first. All figures on this page are live institute analytics.
            </p>
          </DepthCard>
        )}
      </AsyncBoundary>
    </div>
  );
}
