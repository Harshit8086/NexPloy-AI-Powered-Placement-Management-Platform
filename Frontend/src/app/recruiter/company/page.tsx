"use client";

import Link from "next/link";
import {
  Globe,
  MapPin,
  Users,
  CalendarRange,
  Trophy,
  Briefcase,
  Target,
  Pencil,
  ArrowUpRight,
  User,
  Phone,
  Mail,
} from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { useApiResource } from "@/hooks/useApiResource";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { listCampuses } from "@/lib/api/campuses";
import { listDrivesByCompany } from "@/lib/api/drives";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { driveStatusTone } from "@/lib/status";
import type { Application, Drive, Recruiter } from "@/types";

interface CompanyProfileData {
  company: Recruiter;
  drives: Drive[];
  applicationsByDrive: Application[][];
  /** Authoritative campusId → campus name, from GET /api/campuses (the drive.campusId relation). */
  campusNames: Record<string, string>;
}

/**
 * The recruiter's own company profile, resolved server-side from the signed-in principal
 * (GET /recruiters/me) with their real drives (GET /drives filtered by company) and the live
 * applications per drive. No mock data: identity/company columns and derived counts all come
 * from the backend; fields the backend does not store (about text, headcount, hiring-since) are
 * hidden rather than fabricated. Campus names come from the authoritative campus endpoint via
 * each drive's real campusId — never a mock lookup.
 */
async function loadCompanyProfile(): Promise<CompanyProfileData> {
  const company = await getActiveRecruiter();
  const [drives, campuses] = await Promise.all([
    listDrivesByCompany(company.id),
    listCampuses(),
  ]);
  const applicationsByDrive = await Promise.all(
    drives.map((drive) => listApplicationsByDrive(drive.id)),
  );
  const campusNames = Object.fromEntries(campuses.map((c) => [c.id, c.name]));
  return { company, drives, applicationsByDrive, campusNames };
}

const EMPTY: CompanyProfileData = {
  company: null as unknown as Recruiter,
  drives: [],
  applicationsByDrive: [],
  campusNames: {},
};

/** Saved links may omit the scheme — prefix https:// so external anchors navigate correctly. */
function externalHref(url: string): string {
  return /^https?:\/\//i.test(url) ? url : `https://${url}`;
}

const socialLinkClass =
  "flex items-center gap-1.5 rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:border-violet/40 hover:text-violet-bright";

// Brand icons were removed from lucide-react — inline the LinkedIn, X and GitHub marks instead.
function LinkedInIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
      <path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z" />
      <rect width="4" height="12" x="2" y="9" />
      <circle cx="4" cy="4" r="2" />
    </svg>
  );
}

function TwitterIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
    </svg>
  );
}

function GitHubIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
      <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
      <path d="M9 18c-4.51 2-5-2-7-2" />
    </svg>
  );
}

export default function CompanyProfilePage() {
  const { data, loading, error, reload } = useApiResource(
    () => loadCompanyProfile(),
    [],
    EMPTY,
  );

  const company = data.company;
  const companyDrives = data.drives;
  const campusNames = data.campusNames;
  const allApplications = data.applicationsByDrive.flat();
  const totalApplicants = allApplications.length;
  const totalOffers = allApplications.filter(
    (a) => a.status === "offer" || a.status === "joined",
  ).length;
  const expectedHires = companyDrives
    .filter((d) => d.status === "ACTIVE")
    .reduce((n, d) => n + d.expectedHiring, 0);

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Company"
        title="Company Profile"
        subtitle="How your organisation appears to students and the placement office."
        actions={
          <Button asChild variant="outline" size="sm">
            <Link href="/recruiter/settings">
              <Pencil className="h-3.5 w-3.5" />
              Edit Profile
            </Link>
          </Button>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!company}
        onRetry={reload}
        emptyTitle="No company profile"
        emptyMessage="Your recruiter account isn't linked to a company profile yet."
      >
        {company ? (
          <>
            <DepthCard className="mb-5 p-5" tilt>
              <div className="flex flex-wrap items-start gap-4">
                <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-xl bg-violet/10 text-2xl font-bold text-violet-bright">
                  {company.logoInitial}
                </div>
                <div className="min-w-0 flex-1">
                  <h2 className="text-xl font-semibold">{company.companyName}</h2>
                  {company.industry ? (
                    <p className="text-sm text-muted-foreground">{company.industry}</p>
                  ) : null}

                  {company.aboutText || company.companyDescription ? (
                    <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
                      {company.aboutText || company.companyDescription}
                    </p>
                  ) : null}
                  <div className="mt-3 flex flex-wrap gap-4 text-xs text-muted-foreground">
                    {company.hqLocation ? (
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5" /> {company.hqLocation}
                      </span>
                    ) : null}
                    {company.employees || company.companySize ? (
                      <span className="flex items-center gap-1.5">
                        <Users className="h-3.5 w-3.5" />{" "}
                        {company.employees || company.companySize} employees
                      </span>
                    ) : null}
                    {company.hiringSince ? (
                      <span className="flex items-center gap-1.5">
                        <CalendarRange className="h-3.5 w-3.5" /> Hiring since {company.hiringSince}
                      </span>
                    ) : null}
                  </div>
                  {company.website || company.companyLinkedin || company.companyTwitter || company.companyGithub ? (
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      {company.website ? (
                        <a
                          href={externalHref(company.website)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className={socialLinkClass}
                        >
                          <Globe className="h-3.5 w-3.5" /> Website
                        </a>
                      ) : null}
                      {company.companyLinkedin ? (
                        <a
                          href={externalHref(company.companyLinkedin)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className={socialLinkClass}
                        >
                          <LinkedInIcon className="h-3.5 w-3.5" /> LinkedIn
                        </a>
                      ) : null}
                      {company.companyTwitter ? (
                        <a
                          href={externalHref(company.companyTwitter)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className={socialLinkClass}
                        >
                          <TwitterIcon className="h-3.5 w-3.5" /> Twitter
                        </a>
                      ) : null}
                      {company.companyGithub ? (
                        <a
                          href={externalHref(company.companyGithub)}
                          target="_blank"
                          rel="noopener noreferrer"
                          className={socialLinkClass}
                        >
                          <GitHubIcon className="h-3.5 w-3.5" /> GitHub
                        </a>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              </div>
            </DepthCard>

            <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <StatCard label="Drives" value={companyDrives.length} icon={Briefcase} accent="violet" />
              <StatCard label="Applicants" value={totalApplicants} icon={Users} />
              <StatCard label="Offers Made" value={totalOffers} icon={Trophy} accent="success" />
              <StatCard label="Students Hired" value={company.studentsHired} accent="success" />
            </div>

            {company.name || company.email || company.phone ? (
              <DepthCard className="mb-5 p-5">
                <div className="mb-3 flex items-center gap-2">
                  <User className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Campus Point of Contact</CardTitle>
                </div>
                <div className="flex flex-wrap items-center gap-4">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-violet/10 text-sm font-semibold text-violet-bright">
                    {company.name ? company.name.charAt(0).toUpperCase() : "?"}
                  </div>
                  <div className="min-w-0">
                    {company.name ? (
                      <p className="text-sm font-medium">{company.name}</p>
                    ) : null}
                    {company.designation ? (
                      <p className="text-xs text-muted-foreground">{company.designation}</p>
                    ) : null}
                  </div>
                  <div className="flex flex-wrap gap-4 text-xs text-muted-foreground sm:ml-auto">
                    {company.email ? (
                      <a
                        href={`mailto:${company.email}`}
                        className="flex items-center gap-1.5 transition-colors hover:text-violet-bright"
                      >
                        <Mail className="h-3.5 w-3.5" /> {company.email}
                      </a>
                    ) : null}
                    {company.phone ? (
                      <a
                        href={`tel:${company.phone}`}
                        className="flex items-center gap-1.5 transition-colors hover:text-violet-bright"
                      >
                        <Phone className="h-3.5 w-3.5" /> {company.phone}
                      </a>
                    ) : null}
                  </div>
                </div>
              </DepthCard>
            ) : null}

            <DataReveal stagger className="grid gap-5 md:grid-cols-2">
              <DataRevealItem>
                <DepthCard className="h-full p-5">
                  <div className="mb-3 flex items-center gap-2">
                    <Briefcase className="h-4 w-4 text-violet-bright" />
                    <CardTitle>Drives &amp; Eligibility</CardTitle>
                  </div>
                  {companyDrives.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No drives yet.</p>
                  ) : (
                    <div className="space-y-3">
                      {companyDrives.map((d) => (
                        <div key={d.id} className="rounded-lg border border-border p-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <p className="text-sm font-medium">{d.role}</p>
                            <Badge variant={driveStatusTone[d.status]}>{d.status}</Badge>
                          </div>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {d.driveType} · {campusNames[d.campusId] ?? d.location} · {d.package}
                          </p>
                          <p className="mt-1.5 text-xs text-muted-foreground">
                            CGPA {d.criteria.minCgpa}+ · {d.criteria.allowedBranches.join("/")} · max{" "}
                            {d.criteria.maxBacklogs} backlog{d.criteria.maxBacklogs === 1 ? "" : "s"} ·{" "}
                            {d.criteria.graduationYear} batch
                          </p>
                          <Link
                            href={`/recruiter/candidates?drive=${d.id}`}
                            className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-violet-bright transition-colors hover:text-violet"
                          >
                            View Candidates Pool
                            <ArrowUpRight className="h-3 w-3" />
                          </Link>
                        </div>
                      ))}
                    </div>
                  )}
                </DepthCard>
              </DataRevealItem>

              <DataRevealItem>
                <DepthCard className="h-full p-5">
                  <div className="mb-3 flex items-center gap-2">
                    <Target className="h-4 w-4 text-violet-bright" />
                    <CardTitle>What You Hire For</CardTitle>
                  </div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Skills across your open roles
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {Array.from(new Set(companyDrives.flatMap((d) => d.requiredSkills))).map((s) => (
                      <Badge key={s} variant="muted">
                        {s}
                      </Badge>
                    ))}
                  </div>
                  <p className="mb-2 mt-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Selection process
                  </p>
                  <div className="space-y-2">
                    {companyDrives[0]?.rounds.map((r, i) => (
                      <div key={r.name} className="flex gap-2.5">
                        <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-violet/15 text-[10px] font-semibold text-violet-bright">
                          {i + 1}
                        </span>
                        <div>
                          <p className="text-sm font-medium">{r.name}</p>
                          <p className="text-xs text-muted-foreground">{r.description}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="mt-4 rounded-lg border border-violet/30 bg-violet/5 p-3 text-xs text-muted-foreground">
                    <span className="font-medium text-violet-bright">{expectedHires} hires</span> planned
                    across your active drives this cycle.
                  </div>
                </DepthCard>
              </DataRevealItem>
            </DataReveal>
          </>
        ) : null}
      </AsyncBoundary>
    </div>
  );
}
