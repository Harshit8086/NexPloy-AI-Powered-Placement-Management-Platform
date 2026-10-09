"use client";

import Link from "next/link";
import {
  Building2,
  MapPin,
  IndianRupee,
  Calendar,
  Users,
  ShieldCheck,
  ArrowUpRight,
  Target,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { CandidateTable } from "@/components/recruiter/CandidateTable";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { listDrivesByCompany } from "@/lib/api/drives";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { listMatchesForDrive } from "@/lib/api/matches";
import { listStudents } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import { driveStatusTone } from "@/lib/status";
import type { Application, Drive, Recruiter, Student } from "@/types";

interface DrivesData {
  recruiter: Recruiter;
  companyDrives: Drive[];
  /** The drive whose ranked pool is shown in full — first ACTIVE, else first, else null. */
  featured: Drive | null;
  applicationsByDrive: Record<string, Application[]>;
  matchCountByDrive: Record<string, number>;
  students: Student[];
}

/**
 * The signed-in recruiter's drives, resolved live: their company (GET /recruiters/me), the
 * company's drives, and — per drive — its applications and ranked-match count, plus the student
 * directory used for the deterministic eligibility count. No mock data, no featured-drive constant.
 */
async function loadDrives(): Promise<DrivesData> {
  const recruiter = await getActiveRecruiter();
  const companyDrives = await listDrivesByCompany(recruiter.id);
  const featured =
    companyDrives.find((d) => d.status === "ACTIVE") ?? companyDrives[0] ?? null;

  const [appLists, matchLists, students] = await Promise.all([
    Promise.all(companyDrives.map((d) => listApplicationsByDrive(d.id))),
    Promise.all(companyDrives.map((d) => listMatchesForDrive(d.id))),
    listStudents(),
  ]);

  const applicationsByDrive = Object.fromEntries(
    companyDrives.map((d, i) => [d.id, appLists[i]]),
  );
  const matchCountByDrive = Object.fromEntries(
    companyDrives.map((d, i) => [d.id, matchLists[i].length]),
  );

  return { recruiter, companyDrives, featured, applicationsByDrive, matchCountByDrive, students };
}

export default function RecruiterDrivesPage() {
  const { data, loading, error, reload } = useApiResource<DrivesData | null>(
    loadDrives,
    [],
    null,
  );

  const recruiter = data?.recruiter;
  const companyDrives = data?.companyDrives ?? [];
  const featured = data?.featured ?? null;
  const applicationsByDrive = data?.applicationsByDrive ?? {};
  const matchCountByDrive = data?.matchCountByDrive ?? {};
  const students = data?.students ?? [];

  const featuredApplications = featured ? applicationsByDrive[featured.id] ?? [] : [];

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
          eyebrow="Hiring"
          title="Placement Drives"
          subtitle={`${recruiter?.companyName ?? "Your"} drives at this institute, with their published eligibility criteria.`}
        />

        <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Drives" value={companyDrives.length} icon={Building2} accent="violet" />
          <StatCard
            label="Active"
            value={companyDrives.filter((d) => d.status === "ACTIVE").length}
            accent="success"
          />
          <StatCard
            label="Expected Hires"
            value={companyDrives
              .filter((d) => d.status === "ACTIVE")
              .reduce((n, d) => n + d.expectedHiring, 0)}
            icon={Users}
          />
          <StatCard
            label="Ranked Candidates"
            value={companyDrives.reduce((n, d) => n + (matchCountByDrive[d.id] ?? 0), 0)}
            icon={Target}
            accent="violet"
          />
        </div>

        <DataReveal stagger className="mb-5 grid gap-4 md:grid-cols-2">
          {companyDrives.map((d) => {
            const applications = applicationsByDrive[d.id] ?? [];
            const eligible = students.filter((s) => evaluateEligibility(s, d).eligible).length;
            return (
              <DataRevealItem key={d.id}>
                <DepthCard className="h-full p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-base font-bold text-violet-bright">
                        {d.logoInitial}
                      </div>
                      <div className="min-w-0">
                        <CardTitle className="truncate">{d.role}</CardTitle>
                        <p className="truncate text-xs text-muted-foreground">{d.driveType}</p>
                      </div>
                    </div>
                    <Badge variant={driveStatusTone[d.status]}>{d.status}</Badge>
                  </div>

                  <p className="mt-3 text-xs text-muted-foreground">{d.description}</p>

                  <div className="mt-4 flex flex-wrap gap-3 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1.5">
                      <MapPin className="h-3.5 w-3.5" /> {d.location}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <IndianRupee className="h-3.5 w-3.5" /> {d.package}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <Calendar className="h-3.5 w-3.5" /> {d.driveDate}, {d.driveTime}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <Users className="h-3.5 w-3.5" /> {applications.length} applicants
                    </span>
                  </div>

                  <div className="mt-3 rounded-lg border border-border p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                        Eligibility Criteria
                      </p>
                      <Badge variant="muted" className="gap-1">
                        <ShieldCheck className="h-3 w-3" /> {eligible} qualify
                      </Badge>
                    </div>
                    <p className="mt-2 text-xs text-muted-foreground">
                      Minimum CGPA{" "}
                      <span className="font-medium text-foreground">{d.criteria.minCgpa}</span> ·
                      Branches{" "}
                      <span className="font-medium text-foreground">
                        {d.criteria.allowedBranches.join(", ")}
                      </span>{" "}
                      · Max backlogs{" "}
                      <span className="font-medium text-foreground">{d.criteria.maxBacklogs}</span> ·
                      Batch{" "}
                      <span className="font-medium text-foreground">{d.criteria.graduationYear}</span>
                    </p>
                  </div>

                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {d.requiredSkills.map((s) => (
                      <Badge key={s} variant="muted">
                        {s}
                      </Badge>
                    ))}
                  </div>

                  <Button asChild variant="outline" size="sm" className="mt-4">
                    <Link href="/recruiter/candidates">
                      View candidate pool <ArrowUpRight className="h-3.5 w-3.5" />
                    </Link>
                  </Button>
                </DepthCard>
              </DataRevealItem>
            );
          })}
          {companyDrives.length === 0 && (
            <Card className="p-8 text-center text-sm text-muted-foreground md:col-span-2">
              You have no drives yet — create one with the placement office to start hiring.
            </Card>
          )}
        </DataReveal>

        {featured && (
          <Card className="overflow-x-auto p-5">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Building2 className="h-4 w-4 text-violet-bright" />
                <CardTitle>
                  {featured.role} — Ranked Candidates ({featuredApplications.length} applicants)
                </CardTitle>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link href="/recruiter/candidates">
                  Bulk shortlist <ArrowUpRight className="h-3.5 w-3.5" />
                </Link>
              </Button>
            </div>
            <CandidateTable driveId={featured.id} />
          </Card>
        )}
      </div>
    </AsyncBoundary>
  );
}
