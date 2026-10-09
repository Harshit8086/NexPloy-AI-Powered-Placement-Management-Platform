"use client";

import Link from "next/link";
import { MapPin, Briefcase, IndianRupee, ArrowRight, Globe } from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { PageHeader } from "@/components/layout/PageHeader";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { useApiResource } from "@/hooks/useApiResource";
import { listCompanies, type Company } from "@/lib/api/companies";

function CompaniesSkeleton() {
  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-6 space-y-2.5">
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-7 w-72" />
        <Skeleton className="h-4 w-96" />
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    </div>
  );
}

function CompanyCard({ company }: { company: Company }) {
  const activeDrives = company.drives.filter((d) => d.status === "ACTIVE");
  return (
    <DepthCard className="h-full p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-base font-bold text-violet-bright">
            {company.name?.[0]?.toUpperCase() ?? "?"}
          </div>
          <div className="min-w-0">
            <CardTitle className="truncate">{company.name}</CardTitle>
            {company.industry && (
              <p className="truncate text-xs text-muted-foreground">{company.industry}</p>
            )}
          </div>
        </div>
        <Badge variant={company.activeDriveCount > 0 ? "success" : "muted"}>
          {company.activeDriveCount} active
        </Badge>
      </div>

      <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted-foreground">
        {company.location && (
          <span className="flex items-center gap-1.5">
            <MapPin className="h-3.5 w-3.5" /> {company.location}
          </span>
        )}
        {company.website && (
          <span className="flex min-w-0 items-center gap-1.5">
            <Globe className="h-3.5 w-3.5 shrink-0" />{" "}
            <span className="truncate">{company.website}</span>
          </span>
        )}
        <span className="flex items-center gap-1.5">
          <Briefcase className="h-3.5 w-3.5" /> {company.totalDriveCount} total drives
        </span>
      </div>

      {activeDrives.length > 0 ? (
        <ul className="mt-4 space-y-2">
          {activeDrives.map((d) => (
            <li key={d.driveId}>
              <Link
                href={`/student/drives/${d.driveId}`}
                className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm transition-colors"
              >
                <span className="min-w-0 truncate font-medium">{d.role}</span>
                <span className="flex shrink-0 items-center gap-3 text-xs text-muted-foreground">
                  {d.packageLpa != null && (
                    <span className="flex items-center gap-0.5">
                      <IndianRupee className="h-3 w-3" />
                      {d.packageLpa} LPA
                    </span>
                  )}
                  <ArrowRight className="h-3.5 w-3.5" />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-4 text-xs text-muted-foreground">No open drives right now.</p>
      )}
    </DepthCard>
  );
}

/**
 * Companies recruiting at the signed-in student's campus, aggregated live from real recruiter +
 * drive rows (GET /api/companies). No mock data — an empty campus shows an honest empty state.
 */
export default function CompaniesPage() {
  const { data, loading, error, reload } = useApiResource<Company[] | null>(
    listCompanies,
    [],
    null,
  );

  const companies = data ?? [];

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={companies.length === 0}
      onRetry={reload}
      loadingFallback={<CompaniesSkeleton />}
      emptyTitle="No companies yet"
      emptyMessage="No companies are recruiting at your campus right now. They'll appear here as soon as a drive opens."
      className="mx-auto max-w-5xl"
    >
      <div className="mx-auto max-w-5xl">
        <PageHeader
          eyebrow="Opportunities"
          title="Companies"
          subtitle="Companies recruiting at your campus and the roles they're hiring for."
        />

        <DataReveal stagger className="grid gap-4 md:grid-cols-2">
          {companies.map((company) => (
            <DataRevealItem key={company.id}>
              <CompanyCard company={company} />
            </DataRevealItem>
          ))}
        </DataReveal>
      </div>
    </AsyncBoundary>
  );
}
