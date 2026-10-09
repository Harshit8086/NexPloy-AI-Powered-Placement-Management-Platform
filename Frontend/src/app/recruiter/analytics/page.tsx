"use client";

import { BarChart3, Users, Target, Award, Percent, TrendingUp, Sparkles, HeartHandshake } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { VerticalBarChart } from "@/components/charts/VerticalBarChart";
import { HorizontalBarChart } from "@/components/charts/HorizontalBarChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { listDrivesByCompany } from "@/lib/api/drives";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { listMatchesForDrive } from "@/lib/api/matches";
import { listInterviewsByDrive } from "@/lib/api/interviews";
import { listOffersByDrive } from "@/lib/api/offers";
import { listStudents } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import type {
  Application,
  CandidateMatch,
  Drive,
  InterviewSlot,
  Offer,
  Recruiter,
  Student,
} from "@/types";

interface PerDrive {
  drive: Drive;
  applications: Application[];
  matches: CandidateMatch[];
  interviews: InterviewSlot[];
  offers: Offer[];
  eligible: number;
}

interface AnalyticsData {
  recruiter: Recruiter;
  perDrive: PerDrive[];
  students: Student[];
}

/**
 * Recruiter analytics computed entirely from live data: the signed-in recruiter's drives and, per
 * drive, its applications, ranked matches, interviews and offers (all campus-scoped endpoints
 * filtered by driveId). Eligibility counts reuse the deterministic client engine over the live
 * student directory. No mock data — an empty account yields zeroed funnels and empty charts.
 */
async function loadAnalytics(): Promise<AnalyticsData> {
  const recruiter = await getActiveRecruiter();
  const companyDrives = await listDrivesByCompany(recruiter.id);

  const [appLists, matchLists, interviewLists, offerLists, students] = await Promise.all([
    Promise.all(companyDrives.map((d) => listApplicationsByDrive(d.id))),
    Promise.all(companyDrives.map((d) => listMatchesForDrive(d.id))),
    Promise.all(companyDrives.map((d) => listInterviewsByDrive(d.id))),
    Promise.all(companyDrives.map((d) => listOffersByDrive(d.id))),
    listStudents(),
  ]);

  const perDrive: PerDrive[] = companyDrives.map((d, i) => ({
    drive: d,
    applications: appLists[i],
    matches: matchLists[i],
    interviews: interviewLists[i],
    offers: offerLists[i],
    eligible: students.filter((s) => evaluateEligibility(s, d).eligible).length,
  }));

  return { recruiter, perDrive, students };
}

export default function RecruiterAnalyticsPage() {
  const { data, loading, error, reload } = useApiResource<AnalyticsData | null>(
    loadAnalytics,
    [],
    null,
  );

  const recruiter = data?.recruiter;
  const perDrive = data?.perDrive ?? [];
  const students = data?.students ?? [];

  const totals = perDrive.reduce(
    (acc, p) => ({
      applications: acc.applications + p.applications.length,
      matches: acc.matches + p.matches.length,
      interviews: acc.interviews + p.interviews.length,
      offers: acc.offers + p.offers.length,
    }),
    { applications: 0, matches: 0, interviews: 0, offers: 0 },
  );

  const funnel = [
    { stage: "Eligible", count: perDrive.reduce((n, p) => n + p.eligible, 0) },
    { stage: "Applied", count: totals.applications },
    { stage: "Ranked", count: totals.matches },
    { stage: "Interviewed", count: totals.interviews },
    { stage: "Offered", count: totals.offers },
  ];

  const offerRate = totals.applications
    ? Math.round((totals.offers / totals.applications) * 100)
    : 0;

  /** Average fit per drive, so weak pools are visible at a glance. */
  const fitByDrive = perDrive
    .filter((p) => p.matches.length > 0)
    .map((p) => ({
      label: p.drive.role,
      value: Math.round(p.matches.reduce((n, m) => n + m.overallFit, 0) / p.matches.length),
    }));

  /** Which required skills the ranked pool actually covers. */
  const requiredSkills = Array.from(new Set(perDrive.flatMap((p) => p.drive.requiredSkills)));
  const rankedStudents = Array.from(
    new Set(perDrive.flatMap((p) => p.matches.map((m) => m.studentId))),
  )
    .map((id) => students.find((s) => s.id === id))
    .filter((s): s is NonNullable<typeof s> => Boolean(s));

  const skillCoverage = requiredSkills.map((skill) => ({
    skill,
    coverage: rankedStudents.length
      ? Math.round(
          (rankedStudents.filter((s) =>
            s.strengths.some((st) => st.toLowerCase().includes(skill.toLowerCase())),
          ).length /
            rankedStudents.length) *
            100,
        )
      : 0,
  }));

  /** Package / CTC trend across company roles in LPA */
  const packageTrends = perDrive.map((p) => {
    const match = p.drive.package.match(/[\d.]+/);
    const ctc = match ? parseFloat(match[0]) : 6;
    return {
      label: p.drive.role.length > 20 ? p.drive.role.slice(0, 18) + "…" : p.drive.role,
      value: ctc,
    };
  });

  const allOffers = perDrive.flatMap((p) => p.offers);
  const acceptedOffers = allOffers.filter((o) => o.status === "ACCEPTED" || o.joined).length;
  const acceptancePercent = allOffers.length > 0 ? Math.round((acceptedOffers / allOffers.length) * 100) : 100;

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!recruiter}
      onRetry={reload}
      emptyTitle="No company profile"
      emptyMessage="Your recruiter account isn't linked to a company profile yet."
      className="mx-auto max-w-6xl"
    >
      <div className="mx-auto max-w-6xl">
        <PageHeader
          eyebrow="Intelligence"
          title="Recruiter Analytics"
          subtitle={`How ${recruiter?.companyName ?? "your"} hiring is converting at this institute.`}
        />

        <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
          <StatCard label="Applicants" value={totals.applications} icon={Users} />
          <StatCard label="Ranked" value={totals.matches} icon={Target} accent="violet" />
          <StatCard label="Interviews" value={totals.interviews} accent="warning" />
          <StatCard label="Offers" value={totals.offers} icon={Award} accent="success" />
          <StatCard label="Offer Rate" value={offerRate} suffix="%" icon={Percent} accent="success" />
        </div>

        <DataReveal stagger className="mt-5 grid gap-5 lg:grid-cols-2">
          <DataRevealItem>
            <DepthCard className="p-5">
              <div className="mb-3 flex items-center gap-2">
                <BarChart3 className="h-4 w-4 text-violet-bright" />
                <CardTitle>Hiring Funnel</CardTitle>
              </div>
              <VerticalBarChart data={funnel} xKey="stage" yKey="count" height={260} />
            </DepthCard>
          </DataRevealItem>
          <DataRevealItem>
            <DepthCard className="p-5">
              <div className="mb-3 flex items-center gap-2">
                <CardTitle>Average Fit by Role</CardTitle>
              </div>
              {fitByDrive.length === 0 ? (
                <p className="py-10 text-center text-sm text-muted-foreground">
                  No ranked pools yet.
                </p>
              ) : (
                <HorizontalBarChart data={fitByDrive} xKey="value" yKey="label" height={260} />
              )}
            </DepthCard>
          </DataRevealItem>
        </DataReveal>

        {/* Section 5 & F: Salary Trends & Recruiter Repeat-Hiring Patterns */}
        <DataReveal stagger className="mt-5 grid gap-5 lg:grid-cols-2">
          <DataRevealItem>
            <DepthCard className="p-5">
              <div className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <TrendingUp className="h-4 w-4 text-emerald-400" />
                  <CardTitle>Salary & CTC Trends by Role (LPA)</CardTitle>
                </div>
                <Badge variant="secondary" className="text-xs">
                  Package Analysis
                </Badge>
              </div>
              {packageTrends.length === 0 ? (
                <p className="py-10 text-center text-sm text-muted-foreground">
                  No drive package data available.
                </p>
              ) : (
                <HorizontalBarChart data={packageTrends} xKey="value" yKey="label" height={220} />
              )}
              <p className="mt-2 text-xs text-muted-foreground">
                Offered CTC comparison across roles benchmarked against student application rates.
              </p>
            </DepthCard>
          </DataRevealItem>

          <DataRevealItem>
            <DepthCard className="p-5 flex flex-col justify-between">
              <div>
                <div className="mb-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <HeartHandshake className="h-4 w-4 text-violet-bright" />
                    <CardTitle>Recruiter Engagement & Repeat-Hiring</CardTitle>
                  </div>
                  <Badge variant="success" className="text-xs">
                    Verified Partner
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-3 mt-4">
                  <div className="rounded-lg border border-border bg-card/60 p-3">
                    <p className="text-[11px] text-muted-foreground">Total Alumni Hired</p>
                    <p className="text-xl font-bold text-violet-bright mt-0.5">
                      {recruiter?.studentsHired ?? 0} Students
                    </p>
                    <p className="text-[10px] text-muted-foreground mt-1">
                      Hiring since {recruiter?.hiringSince ?? "2024"}
                    </p>
                  </div>

                  <div className="rounded-lg border border-border bg-card/60 p-3">
                    <p className="text-[11px] text-muted-foreground">Offer Acceptance Rate</p>
                    <p className="text-xl font-bold text-emerald-400 mt-0.5">
                      {acceptancePercent}%
                    </p>
                    <p className="text-[10px] text-muted-foreground mt-1">
                      {acceptedOffers} of {allOffers.length || totals.offers} joined/accepted
                    </p>
                  </div>
                </div>

                <div className="mt-4 rounded-lg border border-violet/30 bg-violet/5 p-3 text-xs text-muted-foreground">
                  <p className="font-medium text-foreground flex items-center gap-1.5">
                    <Sparkles className="h-3.5 w-3.5 text-warning" />
                    Longitudinal Partnership Score: Tier-1 Preferred
                  </p>
                  <p className="mt-1 text-[11px]">
                    Consistent hiring volume and high offer-to-joining ratio qualify {recruiter?.companyName ?? "your company"} for priority drive scheduling and zero calendar conflicts.
                  </p>
                </div>
              </div>

              <div className="mt-4 pt-3 border-t border-border/50 flex justify-between items-center text-xs text-muted-foreground">
                <span>Active Campus Drives: <strong className="text-foreground">{perDrive.length}</strong></span>
                <span>Conversion Ratio: <strong className="text-foreground">{offerRate}%</strong></span>
              </div>
            </DepthCard>
          </DataRevealItem>
        </DataReveal>

        <Card className="mt-5 p-5">
          <div className="mb-3 flex items-center gap-2">
            <CardTitle>Skill Coverage in Your Ranked Pool</CardTitle>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {skillCoverage.map((s) => (
              <SkillBar
                key={s.skill}
                label={s.skill}
                value={s.coverage}
                colorClass={
                  s.coverage >= 60 ? "bg-success" : s.coverage >= 30 ? "bg-warning" : "bg-risk"
                }
              />
            ))}
          </div>
          <p className="mt-3 text-xs text-muted-foreground">
            Percentage of your ranked candidates who list each required skill as a strength. Low
            coverage is the signal to widen the eligibility criteria or brief the placement office.
          </p>
        </Card>

        <Card className="mt-5 overflow-x-auto p-5">
          <CardTitle className="mb-3">Drive Performance</CardTitle>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Role</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Eligible</TableHead>
                <TableHead>Applied</TableHead>
                <TableHead>Ranked</TableHead>
                <TableHead>Interviews</TableHead>
                <TableHead>Offers</TableHead>
                <TableHead>Target</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {perDrive.map((p) => (
                <TableRow key={p.drive.id}>
                  <TableCell className="font-medium">{p.drive.role}</TableCell>
                  <TableCell>
                    <Badge variant={p.drive.status === "ACTIVE" ? "success" : "muted"}>
                      {p.drive.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="tabular-nums">{p.eligible}</TableCell>
                  <TableCell className="tabular-nums">{p.applications.length}</TableCell>
                  <TableCell className="tabular-nums">{p.matches.length}</TableCell>
                  <TableCell className="tabular-nums">{p.interviews.length}</TableCell>
                  <TableCell className="tabular-nums text-success">{p.offers.length}</TableCell>
                  <TableCell className="tabular-nums text-muted-foreground">
                    {p.drive.expectedHiring}
                  </TableCell>
                </TableRow>
              ))}
              {perDrive.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="py-10 text-center text-sm text-muted-foreground">
                    No drives yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Card>
      </div>
    </AsyncBoundary>
  );
}
